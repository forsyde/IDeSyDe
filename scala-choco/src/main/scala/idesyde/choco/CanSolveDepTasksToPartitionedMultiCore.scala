package idesyde.choco

import org.chocosolver.solver.Model

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import org.chocosolver.solver.Solution

import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMin
import org.chocosolver.solver.search.strategy.assignments.DecisionOperator
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.Constraint
import idesyde.exploration.explorers.SimpleWorkloadBalancingDecisionStrategy
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import idesyde.choco.HasSingleProcessSingleMessageMemoryConstraints
import idesyde.choco.HasActive4StageDuration
import idesyde.utils.HasUtils
import idesyde.identification.choco.interfaces.ChocoModelMixin
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.core.DecisionModel
import idesyde.identification.choco.ChocoDecisionModel
import idesyde.choco.HasDiscretizationToIntegers
import idesyde.utils.Logger

// object ConMonitorObj extends IMonitorContradiction {

//   def onContradiction(cex: ContradictionException): Unit = {
//     println(cex.toString())
//   }
// }

final class CanSolveDepTasksToPartitionedMultiCore(using logger: Logger)
    extends ChocoExplorable[PeriodicWorkloadToPartitionedSharedMultiCore]
    with HasUtils
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasDiscretizationToIntegers
    with HasExtendedPrecedenceConstraints
    with HasActive4StageDuration
    with HasTimingConstraints {

  override def buildChocoModel(
      m: PeriodicWorkloadToPartitionedSharedMultiCore,
      objectivesUpperLimits: Set[(PeriodicWorkloadToPartitionedSharedMultiCore, Map[String, Double])],
      timeResolution: Long,
      memoryResolution: Long
  ): (Model, Map[String, IntVar]) = {
    val chocoModel = Model()
    val timeValues =
      (m.workload.periods ++ m.wcets.flatten ++ m.workload.relativeDeadlines)
    val memoryValues = m.platform.hardware.storageSizes ++
      m.workload.messagesMaxSizes ++
      m.workload.processSizes

    def double2int(s: Double): Int = discretized(
      if (timeResolution > Int.MaxValue.toLong) Int.MaxValue
      else if (timeResolution <= 0L) timeValues.size * 100
      else timeResolution.toInt,
      timeValues.max
    )(s)
    given Fractional[Long] = HasDiscretizationToIntegers.ceilingLongFractional
    def long2int(l: Long): Int = discretized(
      if (memoryResolution > Int.MaxValue) Int.MaxValue
      else if (memoryResolution <= 0L) memoryValues.size * 100
      else memoryResolution.toInt,
      memoryValues.max
    )(l)

    val periods    = m.workload.periods.map(double2int)
    val priorities = m.workload.prioritiesRateMonotonic.toArray
    val deadlines  = m.workload.relativeDeadlines.map(double2int)
    val wcets      = m.wcets.map(_.map(double2int))
    val maxUtilizations =
      m.platform.hardware.processingElems.map(p => m.maxUtilizations.getOrElse(p, 1.0))

    // println(wcets.map(_.mkString(",")).mkString("\n"))
    // println(deadlines.mkString(", "))
    // build the model so that it can be acessed later
    // memory module
    // val taskMapping = m.workload.processes.zipWithIndex.map((t, i) =>
    //   chocoModel.intVar(
    //     s"task_map($t)",
    //     m.platform.hardware.storageSizes.zipWithIndex
    //       .filter((ub, j) => m.workload.processSizes(i) <= ub)
    //       .map((m, j) => j)
    //       .toArray
    //   )
    // )
    // val dataBlockMapping = m.workload.dataChannels.zipWithIndex.map((c, i) =>
    //   chocoModel.intVar(
    //     s"data_map($c)",
    //     m.platform.hardware.storageSizes.zipWithIndex
    //       .filter((ub, j) => m.workload.messagesMaxSizes(i) <= ub)
    //       .map((m, j) => j)
    //       .toArray
    //   )
    // )
    val (taskMapping, dataBlockMapping, _) =
      postSingleProcessSingleMessageMemoryConstraints(
        chocoModel,
        m.workload.processSizes.map(long2int).toArray,
        m.workload.messagesMaxSizes.map(long2int).toArray,
        m.platform.hardware.storageSizes
          .map(long2int)
          .toArray
      )
    val taskExecution = m.workload.processes.zipWithIndex.map((t, i) =>
      chocoModel.intVar(
        s"task_exec($t)",
        m.platform.hardware.processingElems.zipWithIndex
          .filter((_, j) => m.wcets(i)(j) > -1)
          .map((m, j) => j)
          .toArray
      )
    )
    val responseTimes =
      m.workload.processes.zipWithIndex.map((t, i) =>
        chocoModel.intVar(
          s"rt($t)",
          // minimum WCET possible
          wcets(i)
            .filter(p => p > -1)
            .min,
          deadlines(i),
          true // keeping only bounds for the response time is enough and better
        )
      )
    val blockingTimes =
      m.workload.processes.zipWithIndex.map((t, i) =>
        chocoModel.intVar(
          s"blockingTimes($t)",
          // minimum WCET possible
          0,
          0, //deadlines(i) - wcets(i).filter(_ > 0).minOption.getOrElse(0),
          true // keeping only bounds for the response time is enough and better
        )
      )
    val releaseJitters =
      m.workload.processes.zipWithIndex.map((t, i) =>
        chocoModel.intVar(
          s"releaseJitters($t)",
          // minimum WCET possible
          0,
          deadlines(i) - wcets(i).filter(_ > 0).minOption.getOrElse(0),
          true // keeping only bounds for the response time is enough and better
        )
      )

    postInterProcessorJitters(
      chocoModel,
      taskExecution.toArray,
      responseTimes.toArray,
      releaseJitters.toArray,
      m.workload.interTaskOccasionalBlock
    )

    val processingElemsVirtualChannelInCommElem = m.platform.hardware.processingElems.map(p =>
      m.platform.hardware.communicationElems.zipWithIndex.map((c, i) =>
        chocoModel.intVar(
          s"vc($p, $c)",
          0,
          m.platform.hardware.communicationElementsMaxChannels(i),
          true
        )
      )
    )

    val (
      durationsExec,
      durationsFetch,
      durationsRead,
      durationsWrite,
      durations,
      totalVCPerCommElem
    ) = postActive4StageDurationsConstraints(
      chocoModel,
      wcets.map(_.toArray).toArray,
      m.platform.hardware.communicationElementsMaxChannels,
      m.platform.hardware.communicationElems.map(c => 0), // TODO: find how to incldue frames later
      (t: Int) => (ce: Int) =>
        long2int(m.workload.processSizes(t)) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce)),
      (t: Int) =>
        (c: Int) =>
          (ce: Int) =>
          m.workload.dataGraph
            .find((a, b, _) =>
              b == m.workload.tasks(t) && a == m.workload
                .dataChannels(c)
            )
            .map((_, _, l) => long2int(l) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce)))
            .getOrElse(0),
      (t: Int) =>
        (c: Int) =>
          (ce: Int) =>
            m.workload.dataGraph
            .find((a, b, _) =>
              a == m.workload.tasks(t) && b == m.workload
              .dataChannels(c)
            )
            .map((_, _, l) => long2int(l) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce)))
            .getOrElse(0),
      (s: Int) => (t: Int) => 
        m.platform.hardware.computedPaths(m.platform.hardware.platformElements(s))(m.platform.hardware.platformElements(t)).map(m.platform.hardware.communicationElems.indexOf),
      taskExecution.toArray,
      taskMapping,
      dataBlockMapping,
      processingElemsVirtualChannelInCommElem.map(_.toArray).toArray
    )

    postMinimalResponseTimesByBlocking(
      chocoModel,
      priorities,
      periods.toArray,
      maxUtilizations.toArray,
      durations,
      taskExecution.toArray,
      blockingTimes.toArray,
      releaseJitters.toArray,
      responseTimes.toArray
    )

    val utilizations = postMaximumUtilizations(
      chocoModel,
      priorities,
      periods.toArray,
      maxUtilizations.toArray,
      durations,
      taskExecution.toArray,
      blockingTimes.toArray,
      responseTimes.toArray
    )

    // val fixedPriorityConstraintsModule = FixedPriorityConstraintsModule(
    //   chocoModel,
    //   priorities,
    //   periods.toArray,
    //   deadlines.toArray,
    //   wcets.map(_.toArray).toArray,
    //   taskExecution.toArray,
    //   responseTimes.toArray,
    //   blockingTimes.toArray,
    //   durations
    // )


    // for each FP scheduler
    // rt >= bt + sum of all higher prio tasks in the same CPU
    postPartitionedFixedPrioriPreemtpiveConstraint(m.platform.runtimes.schedulers.zipWithIndex
      .filter((s, j) => m.platform.runtimes.isFixedPriority(j))
      .map((s, j) => j), 
      chocoModel,
    priorities,
    periods.toArray,
    deadlines.toArray,
    wcets.map(_.toArray).toArray,
    maxUtilizations.toArray,
    durations,
    taskExecution.toArray,
    blockingTimes.toArray,
    releaseJitters.toArray,
    responseTimes.toArray)
    
    // for each SC scheduler
    m.workload.tasks.zipWithIndex.foreach((task, i) => {
      m.platform.runtimes.schedulers.zipWithIndex
          .filter((s, j) => m.platform.runtimes.isCyclicExecutive(j))
          .foreach((s, j) => {
            postStaticCyclicExecutiveConstraint(
              chocoModel,
              (i: Int) => (j: Int) => m.workload.interTaskOccasionalBlock(i)(j),
              durations,
              taskExecution.toArray,
              responseTimes.toArray,
              blockingTimes.toArray,
            //val cons = Constraint(s"FPConstrats${j}", DependentWorkloadFPPropagator())
          )
      })
    })

    // Dealing with objectives
    val nUsedPEs = chocoModel.intVar(
      "nUsedPEs",
      1,
      m.platform.hardware.processingElems.length
    )
    // count different ones
    chocoModel.nValues(taskExecution.toArray, nUsedPEs).post()

    val solver = chocoModel.getSolver()
    chocoModel.setObjective(false, nUsedPEs)
    // if there is already a previous solution
    val minPrevSol = objectivesUpperLimits.minByOption((_, s) => s.values.min).foreach((_, sol) => {
      chocoModel.arithm(nUsedPEs, "<", sol.values.min.toInt).post() // there is only one in this case
    })
    solver.setSearch(
      Array(
        // SimpleWorkloadBalancingDecisionStrategy(
        //   (0 until m.platform.runtimes.schedulers.length).toArray,
        //   periods.toArray,
        //   taskExecution.toArray,
        //   utilizations,
        //   durations,
        //   wcets.map(_.toArray).toArray
        // ),
        Search.activityBasedSearch(taskExecution: _*),
        Search.activityBasedSearch(taskMapping: _*),
        Search.activityBasedSearch(dataBlockMapping: _*),
        Search.inputOrderLBSearch(nUsedPEs),
        // Search.activityBasedSearch(processingElemsVirtualChannelInCommElem.flatten:_*)
        // Search.minDomLBSearch(responseTimes: _*),
        // Search.minDomLBSearch(blockingTimes: _*)
        // Search.intVarSearch(
        //   FirstFail(chocoModel),
        //   IntDomainMin(),
        //   DecisionOperatorFactory.makeIntEq,
        //   (durationsRead.flatten ++ durationsWrite.flatten ++ durationsFetch.flatten ++
        //     durations.flatten ++ utilizations ++ taskCommunicationMapping.flatten ++ dataBlockCommMapping.flatten): _*
        // )
      ): _*
    )
    // chocoModel
    //   .getSolver()
    //   .plugMonitor(new IMonitorContradiction {
    //     def onContradiction(cex: ContradictionException): Unit = {
    //       println(cex.toString())
    //       println(chocoModel.getSolver().getDecisionPath().toString())
    //     }
    //   })

    chocoModel.getSolver().setLearningSignedClauses()
    // chocoModel.getSolver().setRestarts(FailCounter(chocoModel, m.workload.taskSizes.size * m.platform.runtimes.schedulers.size), LubyCutoffStrategy(m.workload.taskSizes.size * m.platform.runtimes.schedulers.size), 0)

    // chocoModel
    //   .getSolver()
    //   .plugMonitor(new IMonitorContradiction {
    //     def onContradiction(cex: ContradictionException): Unit = println(cex.toString())
    //   })

    (chocoModel, Map("nUsedPEs" -> nUsedPEs))
  }

  override def rebuildDecisionModel(
      m: PeriodicWorkloadToPartitionedSharedMultiCore,
      solution: Solution,
      timeResolution: Long,
      memoryResolution: Long
  ): (PeriodicWorkloadToPartitionedSharedMultiCore, Map[String, Double]) = {
    val timeValues =
      (m.workload.periods ++ m.wcets.flatten ++ m.workload.relativeDeadlines)
    val memoryValues = m.platform.hardware.storageSizes ++
      m.workload.messagesMaxSizes ++
      m.workload.processSizes
    def int2double(d: Int) = undiscretized(
      if (timeResolution > Int.MaxValue) Int.MaxValue
      else if (timeResolution <= 0L) timeValues.size * 100
      else timeResolution.toInt,
      timeValues.sum
    )(d)
    // val (discreteTimeValues, discreteMemoryValues) =
    //   computeTimeMultiplierAndMemoryDividerWithResolution(
    //     timeValues,
    //     memoryValues,
    //     if (timeResolution > Int.MaxValue) Int.MaxValue else timeResolution.toInt,
    //     if (memoryResolution > Int.MaxValue) Int.MaxValue else memoryResolution.toInt
    //   )
    val intVars = solution.retrieveIntVars(true).asScala
    val processesMemoryMapping: Vector[Int] =
      m.workload.processes.zipWithIndex.map((_, i) =>
        intVars
          .find(_.getName() == s"mapProcess($i)")
          .map(solution.getIntVal(_))
          .get
      )
    val messagesMemoryMapping: Vector[Int] =
      m.workload.dataChannels.zipWithIndex.map((_, i) =>
        intVars
          .find(_.getName() == s"mapMessage($i)")
          .map(solution.getIntVal(_))
          .get
      )
    val taskExecution: Vector[Int] =
      m.workload.processes.zipWithIndex.map((t, _) =>
        intVars
          .find(_.getName() == s"task_exec($t)")
          .map(solution.getIntVal(_))
          .get
      )
    val numVirtualChannelsForProcElem: Vector[Vector[IntVar]] =
      m.platform.hardware.processingElems.map(src =>
        m.platform.hardware.communicationElems.map(ce =>
          intVars.find(_.getName() == s"vc($src, $ce)").get
        )
      )
    val nUsedPEs = intVars.find(_.getName() == "nUsedPEs").get
    val processMappings = processesMemoryMapping.zipWithIndex
      .map((v, i) =>
        m.workload.tasks(i) -> m.platform.hardware.storageElems(processesMemoryMapping(i))
      )
      .toVector
    val processSchedulings = taskExecution.zipWithIndex
      .map((v, i) => m.workload.tasks(i) -> m.platform.runtimes.schedulers(taskExecution(i)))
      .toVector
    val channelMappings = messagesMemoryMapping.zipWithIndex
      .map((v, i) =>
        m.workload.dataChannels(i) -> m.platform.hardware.storageElems(messagesMemoryMapping(i))
      )
      .toVector
    val messageSlotAllocations = m.workload.dataChannels.zipWithIndex.map((c, ci) => c -> {
        // we have to look from the source perpective, since the sending processor is the one that allocates
        val mem = messagesMemoryMapping(ci)
        // TODO: this must be fixed later, it might clash correct slots
        val iter =
          for (
            (t, ti) <- m.workload.tasks.zipWithIndex;
            if m.workload.dataGraph.exists((a, b, _) => (t, c) == (a, b) || (t, c) == (b, a));
            p = taskExecution(ti);
            (ce, j) <- m.platform.hardware.communicationElems.zipWithIndex;
            // if solution.getIntVal(numVirtualChannelsForProcElem(p)(j)) > 0
            if numVirtualChannelsForProcElem(p)(j).getLB() > 0
          )
            yield ce -> (0 until m.platform.hardware.communicationElementsMaxChannels(j))
              .map(slot =>
                // (slot + j % m.platform.hardware.communicationElementsMaxChannels(j)) < solution
                //   .getIntVal(numVirtualChannelsForProcElem(p)(j))
                (slot + j % m.platform.hardware.communicationElementsMaxChannels(
                  j
                )) < numVirtualChannelsForProcElem(p)(j).getLB()
              )
              .toVector
        iter.toMap
      }).toMap
    // val channelSlotAllocations = ???
    (m.copy(
      processMappings = processMappings,
      processSchedulings = processSchedulings,
      channelMappings = channelMappings,
      channelSlotAllocations = messageSlotAllocations
    ), Map("nUsedPEs" -> nUsedPEs.getValue().toDouble))
  }

  // chocoModel.getSolver().plugMonitor(ConMonitorObj)

  // section for time multiplier calculation

  // create the variables that each module requires

  // // timing

  // // the objectives so far are just the minimization of used PEs
  // // the inMinusView is necessary to transform a minimization into
  // // a maximization

  // // create the methods that each mixing requires
  // // def sufficientRMSchedulingPoints(taskIdx: Int): Array[Rational] =
  // //   sourceForSyDeDecisionModel.sufficientRMSchedulingPoints(taskIdx)

  
  // def rebuildFromChocoOutput(output: Solution): Set[DecisionModel] =

  val uniqueIdentifier = "ChocoComDepTasksToMultiCore"

}
