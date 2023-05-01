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
import idesyde.identification.choco.models.BaselineTimingConstraintsModule
import idesyde.identification.choco.models.workload.ExtendedPrecedenceConstraintsModule
import idesyde.identification.choco.models.workload.FixedPriorityConstraintsModule
import idesyde.identification.choco.models.mixed.Active4StageDurationModule
import idesyde.utils.HasUtils
import idesyde.identification.choco.interfaces.ChocoModelMixin
import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.core.DecisionModel
import idesyde.identification.common.StandardDecisionModel
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
    with HasDiscretizationToIntegers {

  override def buildChocoModel(
      m: PeriodicWorkloadToPartitionedSharedMultiCore,
      timeResolution: Long,
      memoryResolution: Long
  ): Model = {
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
      timeValues.sum
    )(s)
    given Fractional[Long] = HasDiscretizationToIntegers.ceilingLongFractional
    def long2int(l: Long): Int = discretized(
      if (memoryResolution > Int.MaxValue) Int.MaxValue
      else if (memoryResolution <= 0L) memoryValues.size * 100
      else memoryResolution.toInt,
      memoryValues.max
    )(l)

    val periods    = m.workload.periods.map(double2int)
    val priorities = m.workload.prioritiesForDependencies
    val deadlines  = m.workload.relativeDeadlines.map(double2int)
    val wcets      = m.wcets.map(_.map(double2int))
    val maxUtilizations =
      m.platform.hardware.processingElems.map(p => m.maxUtilizations.getOrElse(p, 1.0))

    // build the model so that it can be acessed later
    // memory module
    val taskMapping = m.workload.processes.zipWithIndex.map((t, i) =>
      chocoModel.intVar(
        s"task_map($t)",
        m.platform.hardware.storageSizes.zipWithIndex
          .filter((ub, j) => m.workload.processSizes(i) <= ub)
          .map((m, j) => j)
          .toArray
      )
    )
    val dataBlockMapping = m.workload.dataChannels.zipWithIndex.map((c, i) =>
      chocoModel.intVar(
        s"data_map($c)",
        m.platform.hardware.storageSizes.zipWithIndex
          .filter((ub, j) => m.workload.messagesMaxSizes(i) <= ub)
          .map((m, j) => j)
          .toArray
      )
    )
    val (processesMemoryMapping, messagesMemoryMapping, _) =
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
          s"bt($t)",
          // minimum WCET possible
          0,
          deadlines(i),
          true // keeping only bounds for the response time is enough and better
        )
      )
    val extendedPrecedenceConstraintsModule = ExtendedPrecedenceConstraintsModule(
      chocoModel,
      taskExecution.toArray,
      responseTimes.toArray,
      blockingTimes.toArray,
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

    val active4StageDurationModule = Active4StageDurationModule(
      chocoModel,
      m,
      wcets.map(_.toArray).toArray,
      m.workload.processSizes
        .map(d =>
          m.platform.hardware.communicationElementsBitPerSecPerChannel
            .map(b => double2int(d.toDouble / b))
            .toArray
        )
        .toArray,
      m.workload.messagesMaxSizes
        .map(d =>
          m.platform.hardware.communicationElementsBitPerSecPerChannel
            .map(b => double2int(d.toDouble / b))
            .toArray
        )
        .toArray,
      taskExecution.toArray,
      processesMemoryMapping,
      messagesMemoryMapping,
      processingElemsVirtualChannelInCommElem.map(_.toArray).toArray
    )

    val baselineTimingConstraintsModule = BaselineTimingConstraintsModule(
      chocoModel,
      priorities,
      periods.toArray,
      maxUtilizations.toArray,
      active4StageDurationModule.durations,
      taskExecution.toArray,
      blockingTimes.toArray,
      responseTimes.toArray
    )

    val fixedPriorityConstraintsModule = FixedPriorityConstraintsModule(
      chocoModel,
      priorities,
      periods.toArray,
      deadlines.toArray,
      wcets.map(_.toArray).toArray,
      taskExecution.toArray,
      responseTimes.toArray,
      blockingTimes.toArray,
      active4StageDurationModule.durations
    )

    active4StageDurationModule.postActive4StageDurationsConstraints()

    // timing constraints
    extendedPrecedenceConstraintsModule.postInterProcessorBlocking()

    // basic utilization
    baselineTimingConstraintsModule.postMinimalResponseTimesByBlocking()
    baselineTimingConstraintsModule.postMaximumUtilizations()

    // for each FP scheduler
    // rt >= bt + sum of all higher prio tasks in the same CPU
    m.platform.runtimes.schedulers.zipWithIndex
      .filter((s, j) => m.platform.runtimes.isFixedPriority(j))
      .foreach((s, j) => {
        fixedPriorityConstraintsModule.postFixedPrioriPreemtpiveConstraint(j)
      })
    // for each SC scheduler
    m.workload.tasks.zipWithIndex.foreach((task, i) => {
      m.platform.runtimes.schedulers.zipWithIndex
        .filter((s, j) => m.platform.runtimes.isCyclicExecutive(j))
        .foreach((s, j) => {
          postStaticCyclicExecutiveConstraint(
            m,
            chocoModel,
            taskExecution.toArray,
            responseTimes.toArray,
            blockingTimes.toArray,
            active4StageDurationModule
          )(i, j)
          //val cons = Constraint(s"FPConstrats${j}", DependentWorkloadFPPropagator())
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
    solver.setSearch(
      Array(
        SimpleWorkloadBalancingDecisionStrategy(
          (0 until m.platform.runtimes.schedulers.length).toArray,
          periods.toArray,
          taskExecution.toArray,
          baselineTimingConstraintsModule.utilizations,
          active4StageDurationModule.durations,
          wcets.map(_.toArray).toArray
        ),
        Search.inputOrderUBSearch(nUsedPEs),
        Search.activityBasedSearch(taskMapping: _*),
        Search.activityBasedSearch(dataBlockMapping: _*),
        Search.minDomLBSearch(responseTimes: _*),
        Search.minDomLBSearch(blockingTimes: _*),
        Search.minDomLBSearch(processesMemoryMapping: _*),
        Search.minDomLBSearch(messagesMemoryMapping: _*)
        // Search.intVarSearch(
        //   FirstFail(chocoModel),
        //   IntDomainMin(),
        //   DecisionOperatorFactory.makeIntEq,
        //   (durationsRead.flatten ++ durationsWrite.flatten ++ durationsFetch.flatten ++
        //     durations.flatten ++ utilizations ++ taskCommunicationMapping.flatten ++ dataBlockCommMapping.flatten): _*
        // )
      ): _*
    )

    chocoModel
  }

  override def rebuildDecisionModel(
      m: PeriodicWorkloadToPartitionedSharedMultiCore,
      solution: Solution,
      timeResolution: Long,
      memoryResolution: Long
  ): PeriodicWorkloadToPartitionedSharedMultiCore = {
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
      m.workload.processes.zipWithIndex.map((t, _) =>
        intVars
          .find(_.getName() == s"task_map($t)")
          .map(solution.getIntVal(_))
          .get
      )
    val messagesMemoryMapping: Vector[Int] =
      m.workload.dataChannels.zipWithIndex.map((c, _) =>
        intVars
          .find(_.getName() == s"data_map($c)")
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
    val processMappings = processesMemoryMapping.zipWithIndex
      .map((v, i) =>
        m.workload.tasks(i) -> m.platform.hardware.storageElems(processesMemoryMapping(i))
      )
      .toVector
    val processSchedulings = taskExecution.zipWithIndex
      .map((v, i) =>
        m.workload.tasks(i) -> m.platform.runtimes.schedulers(messagesMemoryMapping(i))
      )
      .toVector
    val channelMappings = messagesMemoryMapping.zipWithIndex
      .map((v, i) =>
        m.workload.dataChannels(i) -> m.platform.hardware.storageElems(taskExecution(i))
      )
      .toVector
    // val channelSlotAllocations = ???
    val full = m.copy(
      processMappings = processMappings,
      processSchedulings = processSchedulings,
      channelMappings = channelMappings
    )
    m
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

  /** This method sets up the Worst case schedulability test for a task.
    *
    * The mathetical 'representation' is responseTime(i) >= blockingTime(i) + durations(i) +
    * sum(durations of all higher prios in same scheduler)
    *
    * @param taskIdx
    *   the task to be posted (takes into account all others)
    * @param schedulerIdx
    *   the scheduler to be posted
    */
  def postStaticCyclicExecutiveConstraint(
      m: PeriodicWorkloadToPartitionedSharedMultiCore,
      chocoModel: Model,
      taskExecution: Array[IntVar],
      responseTimes: Array[IntVar],
      blockingTimes: Array[IntVar],
      active4StageDurationModule: Active4StageDurationModule
  )(taskIdx: Int, schedulerIdx: Int): Unit =
    chocoModel.ifThen(
      taskExecution(taskIdx).eq(schedulerIdx).decompose(),
      responseTimes(taskIdx)
        .ge(
          active4StageDurationModule
            .durations(taskIdx)
            .add(blockingTimes(taskIdx))
            .add(
              chocoModel
                .sum(
                  s"sc_interference${taskIdx}_${schedulerIdx}",
                  active4StageDurationModule.durations.zipWithIndex
                    .filter((ws, k) => k != taskIdx)
                    .filterNot((ws, k) =>
                      // leave tasks k which i occasionally block
                      m.workload.interTaskAlwaysBlocks(taskIdx)(k)
                    )
                    .map((w, k) => w)
                    .toArray: _*
                )
            )
        )
        .decompose
    )

  // def rebuildFromChocoOutput(output: Solution): Set[DecisionModel] =

  val uniqueIdentifier = "ChocoComDepTasksToMultiCore"

}
