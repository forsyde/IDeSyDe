package idesyde.choco

  import scala.jdk.CollectionConverters._

import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import idesyde.common.PeriodicWorkloadAndSDFServerToMultiCoreOld
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.solver.variables.IntVar
import idesyde.exploration.explorers.SimpleWorkloadBalancingDecisionStrategy
import org.chocosolver.solver.search.strategy.Search
import idesyde.identification.choco.models.sdf.CompactingMultiCoreMapping
import idesyde.core.Explorer
import idesyde.core.ExplorationSolution
import org.chocosolver.solver.search.limits.FailCounter
import org.chocosolver.solver.search.restart.LubyCutoff
import org.jgrapht.alg.connectivity.ConnectivityInspector
import org.jgrapht.alg.connectivity.KosarajuStrongConnectivityInspector
import org.jgrapht.graph.AsGraphUnion
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.graph.DefaultEdge
import scala.collection.mutable.Buffer
import org.jgrapht.Graph
import idesyde.common.PartitionedSharedMemoryMultiCore
import idesyde.common.SDFApplicationWithFunctions
import org.chocosolver.solver.constraints.extension.Tuples
import org.jgrapht.alg.shortestpath.FloydWarshallShortestPaths

final class CanSolvePeriodicWorkloadAndSDFServersToMulticore
    extends ChocoExplorable[PeriodicWorkloadAndSDFServerToMultiCoreOld]
    with HasDiscretizationToIntegers
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasActive4StageDuration
    with HasTimingConstraints
    with HasSDFSchedulingAnalysisAndConstraints
    with HasExtendedPrecedenceConstraints
    with CanSolveMultiObjective {

  def buildChocoModel(
      m: PeriodicWorkloadAndSDFServerToMultiCoreOld,
      objectivesUpperLimits: Set[ExplorationSolution],
      configuration: Explorer.Configuration
  ): (Model, Map[String, IntVar]) = {
    val chocoModel = Model()
    val execMax    = m.wcets.flatten.max
    val commMax    = m.platform.hardware.maxTraversalTimePerBit.flatten.map(_.toDouble).max
    val timeValues =
      m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
        .map(
          _.toDouble
        ) ++ m.tasksAndSDFs.workload.periods.zip(m.tasksAndSDFs.workload.relative_deadlines).map((a, b) => scala.math.max(a, b))
    val memoryValues =
      m.platform.hardware.storageSizes ++ m.tasksAndSDFs.sdfApplications.sdfMessages
        .map((src, _, _, mSize, p, c, tok) =>
          mSize
        ) ++ m.tasksAndSDFs.workload.messagesMaxSizes ++ m.tasksAndSDFs.workload.processSizes
    // val (discreteTimeValues, discreteMemoryValues) =
    //   computeTimeMultiplierAndMemoryDividerWithResolution(
    //     timeValues,
    //     memoryValues,
    //     if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue else configuration.timeDiscretizationFactor.toInt,
    //     if (configuration.memoryDiscretizationFactor > Int.MaxValue) Int.MaxValue else configuration.memoryDiscretizationFactor.toInt
    //   )
    def double2int(s: Double) = discretized(
      if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue
      else if (configuration.timeDiscretizationFactor <= 0L) timeValues.size * 1000
      else configuration.timeDiscretizationFactor.toInt,
      timeValues.sum
    )(s)
    given Fractional[Long] = HasDiscretizationToIntegers.ceilingLongFractional
    def long2int(l: Long) = discretized(
      if (configuration.memoryDiscretizationFactor > Int.MaxValue) Int.MaxValue
      else if (configuration.memoryDiscretizationFactor <= 0L) memoryValues.size * 100
      else configuration.memoryDiscretizationFactor.toInt,
      memoryValues.max
    )(l)

    val periods    = m.tasksAndSDFs.workload.periods.map(double2int)
    val priorities = m.tasksAndSDFs.workload.prioritiesRateMonotonic.toArray
    val discretizedRelDeadlines  = m.tasksAndSDFs.workload.relative_deadlines.map(double2int)
    val discretizedWcets      = m.wcets.map(_.map(double2int))
    val maxUtilizations =
      m.platform.hardware.processingElems.map(p => 1.0) // TODO: add this later properly
    val messagesBufferingSizes = m.tasksAndSDFs.sdfApplications.sdfMessages
      .map((src, _, _, mSize, p, c, tok) =>
        val s = (m.tasksAndSDFs.sdfApplications.sdfRepetitionVectors(
          m.tasksAndSDFs.sdfApplications.actorsIdentifiers.indexOf(src)
        ) * p + tok) * mSize
        long2int(s)
      )
      .toArray

    // build the model so that it can be acessed later
    // memory module
    val taskExecution = 
      m.tasksAndSDFs.workload.processes.zipWithIndex
        .map((t, i) =>
          chocoModel.intVar(
            s"processExecution($t)",
            m.platform.hardware.processingElems.zipWithIndex
              .filter((_, j) => m.wcets(i)(j) > -1)
              .filter((_, j) => m.platform.runtimes.isFixedPriority(j))
              .map((m, j) => j)
              .toArray
          ))
    val actorExecution = 
      m.tasksAndSDFs.sdfApplications.actorsIdentifiers.zipWithIndex
        .map((t, i) =>
          chocoModel.intVar(
            s"processExecution($t)",
            m.platform.hardware.processingElems.zipWithIndex
              .filter((_, j) => m.wcets(i)(j) > -1)
              .filter((_, j) => m.platform.runtimes.isFixedPriority(j) || m.platform.runtimes.isBareMetal(j))
              .map((m, j) => j)
              .toArray
          )
        )
    // println(taskExecution.mkString(", "))
        
    val processExecution =
      taskExecution ++ actorExecution
    val (processMapping, messageMapping, _) =
      postSingleProcessSingleMessageMemoryConstraints(
        chocoModel,
        m.tasksAndSDFs.workload.processSizes
          .map(long2int)
          .toArray ++ m.tasksAndSDFs.sdfApplications.actorSizes.map(long2int),
        m.tasksAndSDFs.workload.messagesMaxSizes.map(long2int).toArray ++ messagesBufferingSizes,
        m.platform.hardware.storageSizes
          .map(long2int)
          .toArray
      )
    val responseTimes =
      m.tasksAndSDFs.workload.processes.zipWithIndex.map((t, i) =>
        chocoModel.intVar(
          s"rt($t)",
          // minimum WCET possible
          discretizedWcets(i)
            .filter(p => p > -1)
            .min,
          discretizedRelDeadlines(i),
          true // keeping only bounds for the response time is enough and better
        )
      )
    val blockingTimes =
      m.tasksAndSDFs.workload.processes.zipWithIndex.map((t, i) =>
        chocoModel.intVar(
          s"blockingTimes($t)",
          // minimum WCET possible
          0,
          0, //deadlines(i) - wcets(i).filter(_ > 0).minOption.getOrElse(0),
          true // keeping only bounds for the response time is enough and better
        )
      )
    val releaseJitters =
      m.tasksAndSDFs.workload.processes.zipWithIndex.map((t, i) =>
        chocoModel.intVar(
          s"releaseJitters($t)",
          // minimum WCET possible
          0,
          discretizedRelDeadlines(i) - discretizedWcets(i).filter(_ > 0).minOption.getOrElse(0),
          true // keeping only bounds for the response time is enough and better
        )
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

    val numMappedElements = chocoModel.intVar("nUsedPEs", 0, m.platform.runtimes.schedulers.size, true)
    chocoModel.nValues(processExecution.toArray, numMappedElements).post()

    val (
      durationsExec,
      durationsFetch,
      durationsRead,
      durationsWrite,
      durations,
      totalVCPerCommElem
    ) = postActive4StageDurationsConstraints(
      chocoModel,
      discretizedWcets.map(_.toArray).toArray,
      m.platform.hardware.communicationElementsMaxChannels,
      m.platform.hardware.communicationElems.map(c => 0), // TODO: find how to include frames later
      (t: Int) => (ce: Int) =>
        if (t < m.tasksAndSDFs.workload.task_sizes.length) {
          long2int(m.tasksAndSDFs.workload.processSizes(t)) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce))
        } else {
          val tOffset = t - m.tasksAndSDFs.workload.task_sizes.length
          long2int(m.tasksAndSDFs.sdfApplications.actorSizes(tOffset)) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce))
        },
      (t: Int) =>
        (c: Int) =>
          (ce: Int) =>
          if (t < m.tasksAndSDFs.workload.tasks.size && c < m.tasksAndSDFs.workload
                .data_channels.size) {
            m.tasksAndSDFs.workload.dataGraph
              .find((a, b, _) =>
                b == m.tasksAndSDFs.workload.tasks(t) && a == m.tasksAndSDFs.workload
                  .data_channels(c)
              )
              .map((_, _, l) => long2int(l) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce)))
              .getOrElse(0)
          } else if (0 <= t - m.tasksAndSDFs.workload.tasks.size && t - m.tasksAndSDFs.workload.tasks.size < m.tasksAndSDFs.sdfApplications.actorsIdentifiers.size 
                  && 0 <= c - m.tasksAndSDFs.workload.data_channels.size && c - m.tasksAndSDFs.workload.data_channels.size < m.tasksAndSDFs.sdfApplications.sdfMessages.size) {
            val a = m.tasksAndSDFs.sdfApplications.actorsIdentifiers(t - m.tasksAndSDFs.workload.tasks.size)
            val (src, _, cs, l, p, _, _) = m.tasksAndSDFs.sdfApplications.sdfMessages(c - m.tasksAndSDFs.workload.data_channels.size)
            if (src == a) long2int(l * p) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce)) else 0
          } else {0},
      (t: Int) =>
        (c: Int) =>
          (ce: Int) =>
          if (t < m.tasksAndSDFs.workload.tasks.size && c < m.tasksAndSDFs.workload
                .data_channels.size) {
            m.tasksAndSDFs.workload.dataGraph
              .find((a, b, _) =>
                a == m.tasksAndSDFs.workload.tasks(t) && b == m.tasksAndSDFs.workload
                  .data_channels(c)
              )
              .map((_, _, l) => long2int(l) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce)))
              .getOrElse(0)
          } else if (0 <= t - m.tasksAndSDFs.workload.tasks.size && t - m.tasksAndSDFs.workload.tasks.size < m.tasksAndSDFs.sdfApplications.actorsIdentifiers.size && 0 <= c - m.tasksAndSDFs.workload
                .data_channels.size && c - m.tasksAndSDFs.workload
                .data_channels.size < m.tasksAndSDFs.sdfApplications.sdfMessages.size) {
            val a = m.tasksAndSDFs.sdfApplications.actorsIdentifiers(t - m.tasksAndSDFs.workload.tasks.size)
            val (_, dst, cs, l, _, cons, _) = m.tasksAndSDFs.sdfApplications.sdfMessages(c - m.tasksAndSDFs.workload.data_channels.size)
            if (dst == a) then long2int(l * cons) / double2int(m.platform.hardware.communicationElementsBitPerSecPerChannel(ce)) else 0
          } else 0,
      (s: Int) => (t: Int) =>
        m.platform.hardware.computedPaths(m.platform.hardware.platformElements(s))(m.platform.hardware.platformElements(t)).map(m.platform.hardware.communicationElems.indexOf),
      processExecution.toArray,
      processMapping,
      messageMapping,
      processingElemsVirtualChannelInCommElem.map(_.toArray).toArray
    )

    val taskDurations = durations.take(m.tasksAndSDFs.workload.tasks.size)
    val actorDurations = durations.drop(m.tasksAndSDFs.workload.tasks.size)

    postMapChannelsWithConsumers(
      m.platform,
      m.tasksAndSDFs.sdfApplications,
      chocoModel,
      actorExecution.toArray,
      messageMapping.drop(m.tasksAndSDFs.workload.messagesMaxSizes.size).toArray
    )

    postInterProcessorJitters(
      chocoModel,
      taskExecution.toArray,
      responseTimes.toArray,
      releaseJitters.toArray,
      m.tasksAndSDFs.workload.interTaskOccasionalBlock
    )

    postMinimalResponseTimesByBlocking(
      chocoModel,
      priorities,
      periods.toArray,
      maxUtilizations.toArray,
      taskDurations,
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
      taskDurations,
      taskExecution.toArray,
      blockingTimes.toArray,
      responseTimes.toArray
    )

    postPartitionedFixedPrioriPreemtpiveConstraint(
      m.platform.runtimes.schedulers.zipWithIndex
      .filter((s, j) => m.platform.runtimes.isFixedPriority(j))
      .map((s, j) => j), 
    chocoModel,
  priorities,
  periods.toArray,
  discretizedRelDeadlines.toArray,
  discretizedWcets.map(_.toArray).toArray,
  maxUtilizations.toArray,
  taskDurations,
  taskExecution.toArray,
  blockingTimes.toArray,
  releaseJitters.toArray,
  responseTimes.toArray)
    // for each SC scheduler
    m.platform.runtimes.schedulers.zipWithIndex
        .filter((s, j) => m.platform.runtimes.isCyclicExecutive(j))
        .foreach((s, j) => {
          postStaticCyclicExecutiveConstraint(
            chocoModel,
            (i: Int) => (j: Int) => m.tasksAndSDFs.workload.interTaskOccasionalBlock(i)(j),
            taskDurations,
            taskExecution.toArray,
            responseTimes.toArray,
            blockingTimes.toArray,
          //val cons = Constraint(s"FPConstrats${j}", DependentWorkloadFPPropagator())
        )
    })

    for ((aexec, i) <- actorExecution.zipWithIndex; (u,j) <- utilizations.zipWithIndex) {
      chocoModel.ifThen(chocoModel.arithm(u, "=", 100), chocoModel.arithm(aexec, "!=", j))
    }

    val actorEffectiveDuration = actorDurations.zipWithIndex.map((d, i) => {
      val mappedPEUtilization = chocoModel.element(s"mappedPEUtilization($i)", utilizations, actorExecution(i), 0)
      val remainingUtilization = chocoModel.intAffineView(-1, mappedPEUtilization, 100)
      val effectiveD = chocoModel.intVar(s"actorEffectiveDuration($i)", d.getLB(), Math.min(d.getUB() * 100, Int.MaxValue - 1), true) //d.div(remainingUtilization).mul(100).intVar() 
      chocoModel.arithm(remainingUtilization, "*", effectiveD, "=", chocoModel.intScaleView(d, 100)).post()
      effectiveD
    })

    val (
      jobOrder,
      mappedJobsPerElement,
      invThroughputs,
      _
    ) = postSDFTimingAnalysis(
      chocoModel,
      m.tasksAndSDFs.sdfApplications.actorsIdentifiers,
      m.tasksAndSDFs.sdfApplications.sdfDisjointComponents.map(_.toVector).toVector,
      m.tasksAndSDFs.sdfApplications.sdfMessages.map((s, t, _, l, p, _, _) => (s, t, l * p)),
      m.tasksAndSDFs.sdfApplications.jobsAndActors,
      m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraph,
      m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraphWithCycles,
      // m.tasksAndSDFs.sdfApplications.jobsAndActors.flatMap(s =>
      //   m.tasksAndSDFs.sdfApplications.jobsAndActors
      //     .filter(t =>
      //       m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraph
      //       .containsEdge(s, t)
      //         // .get(s)
      //         // .isDirectPredecessorOf(m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraph.get(t))
      //     )
      //     .map(t => (s, t))
      // ),
      // m.tasksAndSDFs.sdfApplications.jobsAndActors.flatMap(s =>
      //   m.tasksAndSDFs.sdfApplications.jobsAndActors
      //     .filter(t =>
      //       m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraphWithCycles
      //       .containsEdge(t, s)
      //         // .get(s)
      //         // .isDirectPredecessorOf(m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraphWithCycles.get(t))
      //     )
      //     .map(t => (s, t))
      // ),
      m.platform.runtimes.schedulers,
      m.tasksAndSDFs.sdfApplications.sdfRepetitionVectors,
      (i: Int) => actorExecution(m.tasksAndSDFs.sdfApplications.actorsIdentifiers.indexOf(m.tasksAndSDFs.sdfApplications.jobsAndActors(i)._1)),
      actorExecution.toArray,
      actorEffectiveDuration,
      m.tasksAndSDFs.sdfApplications.sdfMessages.map(message => m.platform.runtimes.schedulers.map(s1 => m.platform.runtimes.schedulers.map(s2 => chocoModel.intVar(0)).toArray).toArray).toArray
    )

    val goalThs = invThroughputs.zipWithIndex.filter((v, i) => {
      m.tasksAndSDFs.sdfApplications.minimumActorThroughputs(i) <= 0.0
    })
    for ((v, i) <- invThroughputs.zipWithIndex; if !goalThs.contains((v, i))) {
      chocoModel
        .arithm(v, "<=", double2int(1.0 / m.tasksAndSDFs.sdfApplications.minimumActorThroughputs(i)))
        .post()
    }
    val uniqueGoalPerSubGraphThs = goalThs
      .groupBy((v, i) =>
        m.tasksAndSDFs.sdfApplications.sdfDisjointComponents
          .map(_.toVector)
          .indexWhere(as => as.contains(m.tasksAndSDFs.sdfApplications.actorsIdentifiers(i)))
      )
      .map((k, v) => v.head._1)
    val objs = Array(numMappedElements) ++ uniqueGoalPerSubGraphThs
    createAndApplyMOOPropagator(
      chocoModel,
      objs,
      objectivesUpperLimits
      .map(sol => (sol.solved(), sol.objectives().asScala))
      .map((s, o) =>
        o.map((k, v) =>
          if (uniqueGoalPerSubGraphThs.exists(_.getName().equals(k))) k -> double2int(v)
          else k                                                           -> v.toInt
        )
        .filter((k, v) => objs.exists(_.getName().equals(k)))
        .toMap
      )
    )

    // chocoModel.getSolver().setLearningSignedClauses()
    chocoModel.getSolver().setRestarts(FailCounter(chocoModel, processExecution.size * m.platform.runtimes.schedulers.size), LubyCutoff(processExecution.size * m.platform.runtimes.schedulers.size), 0)
    chocoModel.getSolver().setNoGoodRecordingFromRestarts()

    chocoModel.getSolver().setSearch(
      Array(
        Search.activityBasedSearch(processExecution:_*),
        Search.activityBasedSearch(processMapping: _*),
        Search.activityBasedSearch(messageMapping: _*),
        Search.minDomLBSearch(goalThs.map((v, i) => v):_*),
        Search.minDomLBSearch(numMappedElements),
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
    (chocoModel, objs.map(o => o.getName() -> o).toMap)
  }

  def rebuildDecisionModel(
      m: PeriodicWorkloadAndSDFServerToMultiCoreOld,
      solution: Solution,
      configuration: Explorer.Configuration
  ): ExplorationSolution = {
    val timeValues =
      m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
        .map(
          _.toDouble
        ) ++ m.tasksAndSDFs.workload.periods.zip(m.tasksAndSDFs.workload.relative_deadlines).map((a, b) => scala.math.max(a, b))
    val memoryValues =
      m.platform.hardware.storageSizes ++ m.tasksAndSDFs.sdfApplications.sdfMessages
        .map((src, _, _, mSize, p, c, tok) =>
          mSize
        ) ++ m.tasksAndSDFs.workload.messagesMaxSizes ++ m.tasksAndSDFs.workload.processSizes
    def int2double(d: Int) = undiscretized(
      if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue
      else if (configuration.timeDiscretizationFactor <= 0L) timeValues.size * 1000
      else configuration.timeDiscretizationFactor.toInt,
      timeValues.sum
    )(d)
    // val (discreteTimeValues, discreteMemoryValues) =
    //   computeTimeMultiplierAndMemoryDividerWithResolution(
    //     timeValues,
    //     memoryValues,
    //     if (configuration.timeDiscretizationFactor > Int.MaxValue) Int.MaxValue else configuration.timeDiscretizationFactor.toInt,
    //     if (configuration.memoryDiscretizationFactor > Int.MaxValue) Int.MaxValue else configuration.memoryDiscretizationFactor.toInt
    //   )
    val intVars = solution.retrieveIntVars(true).asScala
    // println(intVars.filter(v => v.getName().contains("effect") || v.getName().contains("utilization")).mkString(", "))
    val tasksMemoryMapping: Vector[Int] =
      m.tasksAndSDFs.workload.processes.zipWithIndex.map((_, i) =>
        intVars
          .find(_.getName() == s"mapProcess($i)")
          .map(solution.getIntVal(_))
          .get
      )
    val actorsMemoryMapping: Vector[Int] =
      m.tasksAndSDFs.sdfApplications.actorsIdentifiers.zipWithIndex.map((_, prei) =>
        val i = prei + m.tasksAndSDFs.workload.processes.length
        intVars
          .find(_.getName() == s"mapProcess($i)")
          .map(solution.getIntVal(_))
          .get
      )
    val dataChannelsMemoryMapping: Vector[Int] =
      m.tasksAndSDFs.workload.data_channels.zipWithIndex.map((_, i) =>
        intVars
          .find(_.getName() == s"mapMessage($i)")
          .map(solution.getIntVal(_))
          .get
      )
    val sdfMessageMemoryMappings = m.tasksAndSDFs.sdfApplications.channelsIdentifiers.zipWithIndex.map((c, i) => {
        val messageIdx =
          m.tasksAndSDFs.sdfApplications.sdfMessages.indexWhere((_, _, ms, _, _, _, _) => ms.contains(c))
          + m.tasksAndSDFs.workload.data_channels.length
        val mapping = intVars
          .find(_.getName() == s"mapMessage($messageIdx)")
          .map(solution.getIntVal(_))
          .get
        c -> m.platform.hardware.storageElems(mapping)
      })
    val taskExecution = 
      m.tasksAndSDFs.workload.processes.zipWithIndex
        .map((t, i) =>
        intVars.find(
            _.getName() == s"processExecution($t)"
         ).map(solution.getIntVal(_))
         .get)
    val actorExecution = 
      m.tasksAndSDFs.sdfApplications.actorsIdentifiers.zipWithIndex
        .map((t, i) =>
         intVars.find(
            _.getName() == s"processExecution($t)"
         ).map(solution.getIntVal(_))
         .get
      )
    val jobOrder: Vector[IntVar] = m.tasksAndSDFs.sdfApplications.jobsAndActors
      .map((a, q) => intVars.find(_.getName() == s"jobOrder($a, $q)").get)
      .toVector
    val numVirtualChannelsForProcElem: Vector[Vector[IntVar]] =
      m.platform.hardware.processingElems.map(src =>
        m.platform.hardware.communicationElems.map(ce =>
          intVars.find(_.getName() == s"vc($src, $ce)").get
        )
      )
    val numMappedElements = intVars.find(_.getName() == "nUsedPEs").get
    val invThs = intVars.filter(_.getName().startsWith("invTh"))
    val dataChannelsSlotAllocations = m.tasksAndSDFs.workload.data_channels.zipWithIndex.map((c, ci) => c -> {
        // we have to look from the source perpective, since the sending processor is the one that allocates
        val mem = dataChannelsMemoryMapping(ci)
        // TODO: this must be fixed later, it might clash correct slots
        val iter =
          for (
            (t, ti) <- m.tasksAndSDFs.workload.tasks.zipWithIndex;
            if m.tasksAndSDFs.workload.dataGraph.exists((a, b, _) => (t, c) == (a, b) || (t, c) == (b, a));
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
      val sdfChannelsSlotAllocations = sdfMessageMemoryMappings.map((c, mem) => c -> {
        val iter =
          for (
            (t, ti) <- m.tasksAndSDFs.sdfApplications.actorsIdentifiers.zipWithIndex
            if m.tasksAndSDFs.sdfApplications.dataflowGraphs(0).exists((a, b, _) => (t, c) == (a, b) || (t, c) == (b, a));
            p = actorExecution(ti);
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
    val utilizationPerRuntime = m.platform.runtimes.schedulers.zipWithIndex.filter((s, i) => m.platform.runtimes.isFixedPriority(i)).map((s, i) => 
      intVars.find(_.getName().startsWith(s"utilization($i)")).get.getValue().toDouble / 100.0
    )
    ExplorationSolution(
      (Map("nUsedPEs" -> numMappedElements.getValue().toDouble.asInstanceOf[java.lang.Double]) ++ invThs.map(v => v.getName() -> int2double(v.getValue()).asInstanceOf[java.lang.Double]).toMap).asJava,
      m.copy(
      processesSchedulings = taskExecution.zipWithIndex.map((mi, ti) => m.tasksAndSDFs.workload.processes(ti) -> m.platform.runtimes.schedulers(mi))  ++ actorExecution.zipWithIndex.map((mi, ti) => m.tasksAndSDFs.sdfApplications.actorsIdentifiers(ti) -> m.platform.runtimes.schedulers(mi)),
      processesMappings = tasksMemoryMapping.zipWithIndex.map((mi, ti) => m.tasksAndSDFs.workload.processes(ti) -> m.platform.hardware.storageElems(mi))  ++ actorsMemoryMapping.zipWithIndex.map((mi, ti) => m.tasksAndSDFs.sdfApplications.actorsIdentifiers(ti) -> m.platform.hardware.storageElems(mi)),
      messagesMappings = dataChannelsMemoryMapping.zipWithIndex.map((mi, ci) => m.tasksAndSDFs.workload.data_channels(ci) -> m.platform.hardware.storageElems(mi)) ++ sdfMessageMemoryMappings,
      messageSlotAllocations = dataChannelsSlotAllocations ++ sdfChannelsSlotAllocations,
      sdfOrderBasedSchedules = m.platform.runtimes.schedulers.zipWithIndex.map((s, si) => {
        val unordered = for (
          ((aId, q), i) <- m.tasksAndSDFs.sdfApplications.jobsAndActors.zipWithIndex;
          a = m.tasksAndSDFs.sdfApplications.actorsIdentifiers.indexOf(aId);
          if actorExecution(a) == si
        ) yield (aId, jobOrder(i).getLB())
        unordered.sortBy((a, o) => o).map((a, _) => a)
      }),
      sdfServerUtilization = utilizationPerRuntime.map(u => 1.0 - u),
      tasksAndSDFs = m.tasksAndSDFs.copy(sdfApplications = m.tasksAndSDFs.sdfApplications.copy(minimumActorThroughputs = m.tasksAndSDFs.sdfApplications.actorsIdentifiers.zipWithIndex.map((a, i) => invThs.find(_.getName().startsWith(s"invTh($a)")).map(_.getValue()).map(int2double).map(ith => 1.0 / ith * m.tasksAndSDFs.sdfApplications.sdfRepetitionVectors(i)).getOrElse(0.0))))
    )
    )
  }

  def postMapChannelsWithConsumers(
      m: PartitionedSharedMemoryMultiCore,
      sdf: SDFApplicationWithFunctions,
      chocoModel: Model,
      actorsMemoryMapping: Array[IntVar],
      messagesMemoryMapping: Array[IntVar]
  ): Unit = {
    // we make sure that the input messages are always mapped with the consumers so that
    // it would be synthetizeable later. Otherwise the model becomes irrealistic
    val shortestPath = FloydWarshallShortestPaths(m.hardware.topology)
    val closestMemory = m.hardware.processingElems.map(pe => pe -> m.hardware.storageElems.minBy(me => shortestPath.getPathWeight(pe, me)))
    actorsMemoryMapping.zipWithIndex.foreach((aMap, a) => {
      messagesMemoryMapping.zipWithIndex.foreach((cMap, c) => {
        val (s, t, cs, _, _, _, _) = sdf.sdfMessages(c)
        if (sdf.actorsIdentifiers(a) == t) {
          var tuples = Tuples()
          for ((pe, me) <- closestMemory) {
            tuples.add(m.hardware.processingElems.indexOf(pe), m.hardware.storageElems.indexOf(me))
          }
          chocoModel.table(Array(aMap, cMap), tuples).post()
        }
      })
    })
  }

}
