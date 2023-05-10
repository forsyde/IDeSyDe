package idesyde.choco

import idesyde.identification.common.models.mixed.TasksAndSDFServerToMultiCore
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution

trait CanSolveTasksAndSDFServersToMulticore
    extends ChocoExplorable[TasksAndSDFServerToMultiCore]
    with HasDiscretizationToIntegers
    with HasSingleProcessSingleMessageMemoryConstraints
    with HasActive4StageDuration
    with HasTimingConstraints {

  def buildChocoModel(
      m: TasksAndSDFServerToMultiCore,
      timeResolution: Long,
      memoryResolution: Long
  ): Model = {
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
    val messagesSizes = m.tasksAndSDFs.sdfApplications.sdfMessages
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
              .map((m, j) => j)
              .toArray
          ))
        
    val processExecution =
      taskExecution ++ m.tasksAndSDFs.sdfApplications.actorsIdentifiers.zipWithIndex
        .map((t, i) =>
          chocoModel.intVar(
            s"processExecution($t)",
            m.platform.hardware.processingElems.zipWithIndex
              .filter((_, j) => m.wcets(i)(j) > -1)
              .map((m, j) => j)
              .toArray
          )
        )
    val (processMapping, messageMapping, _) =
      postSingleProcessSingleMessageMemoryConstraints(
        chocoModel,
        m.tasksAndSDFs.workload.processSizes
          .map(long2int)
          .toArray ++ m.tasksAndSDFs.sdfApplications.actorSizes.map(long2int),
        m.tasksAndSDFs.workload.messagesMaxSizes.map(long2int).toArray ++ messagesSizes,
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
        .toArray ++ messagesSizes.map(d => m.platform.hardware.communicationElementsBitPerSecPerChannel
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
              .map((_, _, l) => l)
              .getOrElse(0L)
          } else if (t < m.tasksAndSDFs.sdfApplications.actorsIdentifiers.size && c < m.tasksAndSDFs.sdfApplications.sdfMessages.size) {
            val a = m.tasksAndSDFs.sdfApplications.actorsIdentifiers(t - m.tasksAndSDFs.workload.tasks.size)
            val cIdx = m.tasksAndSDFs.sdfApplications.channelsIdentifiers(c - m.tasksAndSDFs.workload.dataChannels.size)
            m.tasksAndSDFs.sdfApplications.dataflowGraphs(0).find((src, dst, l) => src == a && dst == cIdx).map((_,_,l) => l.toLong).getOrElse(0L)
          } else 0L,
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
          } else if (t < m.tasksAndSDFs.sdfApplications.actorsIdentifiers.size && c < m.tasksAndSDFs.sdfApplications.sdfMessages.size) {
            val a = m.tasksAndSDFs.sdfApplications.actorsIdentifiers(t - m.tasksAndSDFs.workload.tasks.size)
            val cIdx = m.tasksAndSDFs.sdfApplications.channelsIdentifiers(c - m.tasksAndSDFs.workload.dataChannels.size)
            m.tasksAndSDFs.sdfApplications.dataflowGraphs(0).find((src, dst, l) => src == cIdx && dst == a).map((_,_,l) => l.toLong).getOrElse(0L)
          } else 0L,
      processExecution.toArray,
      processMapping,
      messageMapping,
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
      durations,
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
            durations,
            taskExecution.toArray,
            responseTimes.toArray,
            blockingTimes.toArray,
          //val cons = Constraint(s"FPConstrats${j}", DependentWorkloadFPPropagator())
        )
    })

    chocoModel
  }

  def rebuildDecisionModel(
      m: TasksAndSDFServerToMultiCore,
      solution: Solution,
      timeResolution: Long,
      memoryResolution: Long
  ): TasksAndSDFServerToMultiCore = m
}
