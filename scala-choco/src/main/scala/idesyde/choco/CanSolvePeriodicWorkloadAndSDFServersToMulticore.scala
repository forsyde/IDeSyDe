package idesyde.choco

import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import idesyde.common.PeriodicWorkloadAndSDFServerToMultiCore
import idesyde.utils.Logger
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.solver.variables.IntVar

final class CanSolvePeriodicWorkloadAndSDFServersToMulticore(using logger: Logger)
    extends ChocoExplorable[PeriodicWorkloadAndSDFServerToMultiCore]
    with HasDiscretizationToIntegers
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasActive4StageDuration
    with HasTimingConstraints
    with HasSDFSchedulingAnalysisAndConstraints
    with CanSolveMultiObjective {

  def buildChocoModel(
      m: PeriodicWorkloadAndSDFServerToMultiCore,
      timeResolution: Long,
      memoryResolution: Long,
      objsUpperBounds: Vector[Vector[Int]] = Vector.empty
  ): (Model, Vector[IntVar]) = {
    val chocoModel = Model()
    val execMax    = m.wcets.flatten.max
    val commMax    = m.platform.hardware.maxTraversalTimePerBit.flatten.map(_.toDouble).max
    val timeValues =
      m.wcets.flatten ++ m.platform.hardware.maxTraversalTimePerBit.flatten
        .map(
          _.toDouble
        ) ++ m.tasksAndSDFs.workload.periods ++ m.wcets.flatten ++ m.tasksAndSDFs.workload.relativeDeadlines
    val memoryValues =
      m.platform.hardware.storageSizes ++ m.tasksAndSDFs.sdfApplications.sdfMessages
        .map((src, _, _, mSize, p, c, tok) =>
          mSize
        ) ++ m.tasksAndSDFs.workload.messagesMaxSizes ++ m.tasksAndSDFs.workload.processSizes
    // val (discreteTimeValues, discreteMemoryValues) =
    //   computeTimeMultiplierAndMemoryDividerWithResolution(
    //     timeValues,
    //     memoryValues,
    //     if (timeResolution > Int.MaxValue) Int.MaxValue else timeResolution.toInt,
    //     if (memoryResolution > Int.MaxValue) Int.MaxValue else memoryResolution.toInt
    //   )
    def double2int(s: Double) = discretized(
      if (timeResolution > Int.MaxValue) Int.MaxValue
      else if (timeResolution <= 0L) timeValues.size * 100
      else timeResolution.toInt,
      timeValues.sum
    )(s)
    given Fractional[Long] = HasDiscretizationToIntegers.ceilingLongFractional
    def long2int(l: Long) = discretized(
      if (memoryResolution > Int.MaxValue) Int.MaxValue
      else if (memoryResolution <= 0L) memoryValues.size * 100
      else memoryResolution.toInt,
      memoryValues.max
    )(l)

    val periods    = m.tasksAndSDFs.workload.periods.map(double2int)
    val priorities = m.tasksAndSDFs.workload.prioritiesForDependencies
    val deadlines  = m.tasksAndSDFs.workload.relativeDeadlines.map(double2int)
    val wcets      = m.wcets.map(_.map(double2int))
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
    val sdfMessagesMaxSizes = m.tasksAndSDFs.sdfApplications.sdfMessages
      .map((src, _, _, mSize, p, c, tok) =>
        long2int(p * mSize)
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
              .map((m, j) => j)
              .toArray
          )
        )
        
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
          wcets(i)
            .filter(p => p > -1)
            .min,
          deadlines(i),
          true // keeping only bounds for the response time is enough and better
        )
      )
    val blockingTimes =
      m.tasksAndSDFs.workload.processes.zipWithIndex.map((t, i) =>
        chocoModel.intVar(
          s"bt($t)",
          // minimum WCET possible
          0,
          deadlines(i),
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
      m.tasksAndSDFs.workload.processSizes
        .map(d =>
          m.platform.hardware.communicationElementsBitPerSecPerChannel
            .map(b => double2int(d.toDouble / b))
            .toArray
        )
        .toArray ++ m.tasksAndSDFs.sdfApplications.actorSizes.map(d =>
          m.platform.hardware.communicationElementsBitPerSecPerChannel
            .map(b => double2int(d.toDouble / b))
            .toArray
        ).toArray,
      m.tasksAndSDFs.workload.messagesMaxSizes
        .map(d =>
          m.platform.hardware.communicationElementsBitPerSecPerChannel
            .map(b => double2int(d.toDouble / b))
            .toArray
        )
        .toArray ++ sdfMessagesMaxSizes.map(d => m.platform.hardware.communicationElementsBitPerSecPerChannel
            .map(b => double2int(d.toDouble / b))
            .toArray),
      m.platform.hardware.communicationElementsMaxChannels,
      (s: Int) => (t: Int) =>
        m.platform.hardware.computedPaths(m.platform.hardware.platformElements(s))(m.platform.hardware.platformElements(t)).map(m.platform.hardware.communicationElems.indexOf),
      (t: Int) =>
        (c: Int) =>
          if (t < m.tasksAndSDFs.workload.tasks.size && m.tasksAndSDFs.workload
                .dataChannels.size < c) {
            m.tasksAndSDFs.workload.dataGraph
              .find((a, b, _) =>
                a == m.tasksAndSDFs.workload.tasks(t) && b == m.tasksAndSDFs.workload
                  .dataChannels(c)
              )
              .map((_, _, l) => long2int(l))
              .getOrElse(0)
          } else if (0 <= t - m.tasksAndSDFs.workload.tasks.size && t - m.tasksAndSDFs.workload.tasks.size < m.tasksAndSDFs.sdfApplications.actorsIdentifiers.size 
                  && 0 <= c - m.tasksAndSDFs.workload.dataChannels.size && c - m.tasksAndSDFs.workload.dataChannels.size < m.tasksAndSDFs.sdfApplications.sdfMessages.size) {
            val a = m.tasksAndSDFs.sdfApplications.actorsIdentifiers(t - m.tasksAndSDFs.workload.tasks.size)
            val (src, _, cs, l, p, _, _) = m.tasksAndSDFs.sdfApplications.sdfMessages(c - m.tasksAndSDFs.workload.dataChannels.size)
            if (src == a) long2int(l * p) else 0
          } else {0},
      (t: Int) =>
        (c: Int) =>
          if (t < m.tasksAndSDFs.workload.tasks.size && m.tasksAndSDFs.workload
                .dataChannels.size < c) {
            m.tasksAndSDFs.workload.dataGraph
              .find((a, b, _) =>
                b == m.tasksAndSDFs.workload.tasks(t) && a == m.tasksAndSDFs.workload
                  .dataChannels(c)
              )
              .map((_, _, l) => l)
              .getOrElse(0L)
          } else if (0 <= t - m.tasksAndSDFs.workload.tasks.size && t - m.tasksAndSDFs.workload.tasks.size < m.tasksAndSDFs.sdfApplications.actorsIdentifiers.size && 0 <= c - m.tasksAndSDFs.workload
                .dataChannels.size && c - m.tasksAndSDFs.workload
                .dataChannels.size < m.tasksAndSDFs.sdfApplications.sdfMessages.size) {
            val a = m.tasksAndSDFs.sdfApplications.actorsIdentifiers(t - m.tasksAndSDFs.workload.tasks.size)
            val (_, dst, cs, l, _, cons, _) = m.tasksAndSDFs.sdfApplications.sdfMessages(c - m.tasksAndSDFs.workload.dataChannels.size)
            if (dst == a) then long2int(l * cons) else 0
          } else 0,
      processExecution.toArray,
      processMapping,
      messageMapping,
      processingElemsVirtualChannelInCommElem.map(_.toArray).toArray
    )

    val taskDurations = durations.take(m.tasksAndSDFs.workload.tasks.size)
    val actorDurations = durations.drop(m.tasksAndSDFs.workload.tasks.size)

    postMinimalResponseTimesByBlocking(
      chocoModel,
      priorities,
      periods.toArray,
      maxUtilizations.toArray,
      taskDurations,
      taskExecution.toArray,
      blockingTimes.toArray,
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

    m.platform.runtimes.schedulers.zipWithIndex
      .filter((s, j) => m.platform.runtimes.isFixedPriority(j))
      .foreach((s, j) => {
        postFixedPrioriPreemtpiveConstraint(j, 
        chocoModel,
      priorities,
      periods.toArray,
      deadlines.toArray,
      wcets.map(_.toArray).toArray,
      maxUtilizations.toArray,
      taskDurations,
      taskExecution.toArray,
      blockingTimes.toArray,
      responseTimes.toArray)
      })
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
      numMappedElements
    ) = postSDFTimingAnalysis(
      chocoModel,
      m.tasksAndSDFs.sdfApplications.actorsIdentifiers,
      m.tasksAndSDFs.sdfApplications.sdfDisjointComponents.map(_.toVector).toVector,
      m.tasksAndSDFs.sdfApplications.sdfMessages.map((s, t, _, l, p, _, _) => (s, t, l * p)),
      m.tasksAndSDFs.sdfApplications.jobsAndActors,
      m.tasksAndSDFs.sdfApplications.jobsAndActors.flatMap(s =>
        m.tasksAndSDFs.sdfApplications.jobsAndActors
          .filter(t =>
            m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraph
              .get(s)
              .isDirectPredecessorOf(m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraph.get(t))
          )
          .map(t => (s, t))
      ),
      m.tasksAndSDFs.sdfApplications.jobsAndActors.flatMap(s =>
        m.tasksAndSDFs.sdfApplications.jobsAndActors
          .filter(t =>
            m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraphWithCycles
              .get(s)
              .isDirectPredecessorOf(m.tasksAndSDFs.sdfApplications.firingsPrecedenceGraphWithCycles.get(t))
          )
          .map(t => (s, t))
      ),
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
    createAndApplyMOOPropagator(chocoModel, objs, objsUpperBounds)

    // chocoModel
    //   .getSolver()
    //   .plugMonitor(new IMonitorContradiction {
    //     def onContradiction(cex: ContradictionException): Unit = println(cex.toString())
    //   })
    (chocoModel, objs.toVector)
  }

  def rebuildDecisionModel(
      m: PeriodicWorkloadAndSDFServerToMultiCore,
      solution: Solution,
      timeResolution: Long,
      memoryResolution: Long
  ): PeriodicWorkloadAndSDFServerToMultiCore = {
    m
  }
}
