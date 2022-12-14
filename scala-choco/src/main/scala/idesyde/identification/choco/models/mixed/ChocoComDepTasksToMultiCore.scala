package idesyde.identification.choco.models.mixed

import org.chocosolver.solver.Model

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import org.chocosolver.solver.Solution

import idesyde.identification.choco.ChocoStandardDecisionModel
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
import spire.math._
import spire.compat.fractional
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsModule
import idesyde.identification.choco.models.BaselineTimingConstraintsModule
import idesyde.identification.choco.models.workload.ExtendedPrecedenceConstraintsModule
import idesyde.identification.choco.models.workload.FixedPriorityConstraintsModule
import idesyde.identification.choco.models.mixed.Active4StageDurationModule
import idesyde.utils.CoreUtils
import idesyde.identification.choco.interfaces.ChocoModelMixin
import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore

// object ConMonitorObj extends IMonitorContradiction {

//   def onContradiction(cex: ContradictionException): Unit = {
//     println(cex.toString())
//   }
// }

final case class ChocoComDepTasksToMultiCore(
    val dse: PeriodicWorkloadToPartitionedSharedMultiCore
) extends ChocoStandardDecisionModel
    with ChocoModelMixin() {

  val coveredElements = dse.coveredElements

  val coveredElementRelations = dse.coveredElementRelations

  val chocoModel = Model()

  // chocoModel.getSolver().plugMonitor(ConMonitorObj)

  // section for time multiplier calculation
  val timeValues =
    (dse.workload.periods ++ dse.wcets.flatten ++ dse.workload.relativeDeadlines)
  var timeMultiplier = 1L
  while (
    timeValues
      .map(_ * (timeMultiplier))
      .exists(d => d.numerator <= d.denominator)
    &&
    timeValues
      .maxBy(t =>
        t * (timeMultiplier)
      ) < Int.MaxValue / 40 - 1 // the four is due to how the sum is done for wcet
  ) {
    timeMultiplier *= 10
  }
  // scribe.debug(timeMultiplier.toString)

  // do the same for memory numbers
  val memoryValues  = dse.platform.hardware.storageSizes
  var memoryDivider = 1L
  while (
    memoryValues.forall(
      CoreUtils.ceil(_, memoryDivider) >= Int.MaxValue / 100000
    ) && memoryDivider < Int.MaxValue
  ) {
    memoryDivider *= 10L
  }
  // scribe.debug(memoryMultipler.toString)
  // scribe.debug(allMemorySizeNumbers().mkString("[", ",", "]"))

  // create the variables that each module requires
  val periods    = dse.workload.periods.map(_ * (timeMultiplier))
  val priorities = dse.workload.prioritiesForDependencies
  val deadlines  = dse.workload.relativeDeadlines.map(_ * (timeMultiplier))
  val wcets      = dse.wcets.map(_.map(f => (f * (timeMultiplier))))
  val maxUtilizations =
    dse.platform.hardware.processingElems.map(p => dse.maxUtilizations.getOrElse(p, Rational(1)))

  // build the model so that it can be acessed later
  // memory module
  val taskMapping = dse.workload.processes.zipWithIndex.map((t, i) =>
    chocoModel.intVar(
      s"task_map($t)",
      dse.platform.hardware.storageSizes.zipWithIndex
        .filter((m, j) => dse.workload.processSizes(i) <= m)
        .map((m, j) => j)
    )
  )
  val dataBlockMapping = dse.workload.channels.zipWithIndex.map((c, i) =>
    chocoModel.intVar(
      s"data_map($c)",
      dse.platform.hardware.storageSizes.zipWithIndex
        .filter((m, j) => dse.workload.messagesMaxSizes(i) <= m)
        .map((m, j) => j)
    )
  )
  val memoryMappingModule = SingleProcessSingleMessageMemoryConstraintsModule(
    chocoModel,
    dse.workload.processSizes.map(CoreUtils.ceil(_, memoryDivider)).map(_.toInt),
    dse.workload.messagesMaxSizes.map(CoreUtils.ceil(_, memoryDivider)).map(_.toInt),
    dse.platform.hardware.storageSizes
      .map(CoreUtils.ceil(_, memoryDivider))
      .map(_.toInt)
  )

  // timing
  val taskExecution = dse.workload.processes.zipWithIndex.map((t, i) =>
    chocoModel.intVar(
      s"task_map($t)",
      dse.platform.hardware.processingElems.zipWithIndex
        .filter((m, j) => dse.wcets(i)(j) >= 0)
        .map((m, j) => j)
    )
  )
  val responseTimes =
    dse.workload.processes.zipWithIndex.map((t, i) =>
      chocoModel.intVar(
        s"rt($t)",
        // minimum WCET possible
        wcets(i)
          .filter(p => p > -1)
          .min
          .ceil
          .toInt,
        deadlines(i).floor.toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val blockingTimes =
    dse.workload.processes.zipWithIndex.map((t, i) =>
      chocoModel.intVar(
        s"bt($t)",
        // minimum WCET possible
        0,
        deadlines(i).floor.toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )

  val extendedPrecedenceConstraintsModule = ExtendedPrecedenceConstraintsModule(
    chocoModel,
    taskExecution,
    responseTimes,
    blockingTimes,
    dse.workload.interTaskOccasionalBlock
  )

  val taskCommMapping = dse.workload.processes.zipWithIndex.map((t, i) =>
    dse.platform.hardware.communicationElems.zipWithIndex.map((ce, j) =>
      chocoModel.boolVar(
        s"tcom_map($t, $ce)"
      )
    )
  )
  val dataBlockCommMapping =
    dse.workload.channels.zipWithIndex.map((c, i) =>
      dse.platform.hardware.communicationElems.zipWithIndex.map((ce, j) =>
        chocoModel.boolVar(
          s"dcom_map($c, $ce)"
        )
      )
    )

  val processingElemsVirtualChannelInCommElem = dse.platform.hardware.processingElems.map(p =>
    dse.platform.hardware.communicationElems.zipWithIndex.map((c, i) =>
      chocoModel.intVar(
        s"vc($p, $c)",
        0,
        dse.platform.hardware.communicationElementsMaxChannels(i),
        true
      )
    )
  )

  val active4StageDurationModule = Active4StageDurationModule(
    chocoModel,
    dse,
    taskExecution,
    memoryMappingModule.processesMemoryMapping,
    memoryMappingModule.messagesMemoryMapping,
    processingElemsVirtualChannelInCommElem
  )

  val baselineTimingConstraintsModule = BaselineTimingConstraintsModule(
    chocoModel,
    priorities,
    periods,
    maxUtilizations,
    active4StageDurationModule.durations,
    taskExecution,
    blockingTimes,
    responseTimes
  )

  val fixedPriorityConstraintsModule = FixedPriorityConstraintsModule(
    chocoModel,
    priorities,
    periods,
    deadlines,
    wcets,
    taskExecution,
    responseTimes,
    blockingTimes,
    active4StageDurationModule.durations
  )

  memoryMappingModule.postSingleProcessSingleMessageMemoryConstraints()

  active4StageDurationModule.postActive4StageDurationsConstraints()

  // timing constraints
  extendedPrecedenceConstraintsModule.postInterProcessorBlocking()

  // basic utilization
  baselineTimingConstraintsModule.postMinimalResponseTimesByBlocking()
  baselineTimingConstraintsModule.postMaximumUtilizations()

  // for each FP scheduler
  // rt >= bt + sum of all higher prio tasks in the same CPU
  dse.platform.runtimes.schedulers.zipWithIndex
    .filter((s, j) => dse.platform.runtimes.isFixedPriority(j))
    .foreach((s, j) => {
      fixedPriorityConstraintsModule.postFixedPrioriPreemtpiveConstraint(j)
    })
  // for each SC scheduler
  dse.workload.processes.zipWithIndex.foreach((task, i) => {
    dse.platform.runtimes.schedulers.zipWithIndex
      .filter((s, j) => dse.platform.runtimes.isCyclicExecutive(j))
      .foreach((s, j) => {
        postStaticCyclicExecutiveConstraint(active4StageDurationModule)(i, j)
        //val cons = Constraint(s"FPConstrats${j}", DependentWorkloadFPPropagator())
      })
  })

  // Dealing with objectives
  val nUsedPEs = chocoModel.intVar(
    "nUsedPEs",
    1,
    dse.platform.hardware.processingElems.length
  )
  // count different ones
  chocoModel.atMostNValues(taskExecution, nUsedPEs, true).post()

  // the objectives so far are just the minimization of used PEs
  // the inMinusView is necessary to transform a minimization into
  // a maximization
  override val modelMinimizationObjectives = Array(nUsedPEs)

  // create the methods that each mixing requires
  // def sufficientRMSchedulingPoints(taskIdx: Int): Array[Rational] =
  //   sourceForSyDeDecisionModel.sufficientRMSchedulingPoints(taskIdx)

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
      active4StageDurationModule: Active4StageDurationModule
  )(taskIdx: Int, schedulerIdx: Int): Unit =
    chocoModel.ifThen(
      taskExecution(taskIdx).eq(schedulerIdx).decompose(),
      responseTimes(taskIdx)
        .ge(
          active4StageDurationModule
            .durations(taskIdx)(schedulerIdx)
            .add(blockingTimes(taskIdx))
            .add(
              chocoModel
                .sum(
                  s"sc_interference${taskIdx}_${schedulerIdx}",
                  active4StageDurationModule.durations.zipWithIndex
                    .filter((ws, k) => k != taskIdx)
                    .filterNot((ws, k) =>
                      // leave tasks k which i occasionally block
                      dse.workload.interTaskAlwaysBlocks(taskIdx)(k)
                    )
                    .map((w, k) => w(schedulerIdx))
                    .toArray: _*
                )
            )
        )
        .decompose
    )

  override val strategies = Array(
    SimpleWorkloadBalancingDecisionStrategy(
      (0 until dse.platform.runtimes.schedulers.length).toArray,
      periods,
      taskExecution,
      baselineTimingConstraintsModule.utilizations,
      active4StageDurationModule.durations,
      executionTimes
    ),
    Search.inputOrderUBSearch(nUsedPEs),
    Search.activityBasedSearch(taskMapping: _*),
    Search.activityBasedSearch(dataBlockMapping: _*),
    Search.inputOrderLBSearch(responseTimes: _*),
    Search.inputOrderLBSearch(blockingTimes: _*)
    // Search.intVarSearch(
    //   FirstFail(chocoModel),
    //   IntDomainMin(),
    //   DecisionOperatorFactory.makeIntEq,
    //   (durationsRead.flatten ++ durationsWrite.flatten ++ durationsFetch.flatten ++
    //     durations.flatten ++ utilizations ++ taskCommunicationMapping.flatten ++ dataBlockCommMapping.flatten): _*
    // )
  )

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val rebuilt = ForSyDeSystemGraph()
    dse.workload.tasks.zipWithIndex.foreach((t, i) => {
      rebuilt.addVertex(t.getViewedVertex)
      val analysed         = AnalysedTask.enforce(t)
      val responseTimeFrac = Rational(responseTimes(i).getValue(), timeMultiplier)
      val blockingTimeFrac = Rational(blockingTimes(i).getValue(), timeMultiplier)
      // scribe.debug(s"task ${t.getIdentifier} RT: (raw ${responseTimes(i).getValue}) ${responseTimeFrac.doubleValue}")
      // scribe.debug(s"task ${t.getIdentifier} BT: (raw ${blockingTimes(i).getValue}) ${blockingTimeFrac.doubleValue}")
      analysed.setWorstCaseResponseTimeNumeratorInSecs(responseTimeFrac.numerator.toLong)
      analysed.setWorstCaseResponseTimeDenominatorInSecs(responseTimeFrac.denominator.toLong)
      analysed.setWorstCaseBlockingTimeNumeratorInSecs(blockingTimeFrac.numerator.toLong)
      analysed.setWorstCaseBlockingTimeDenominatorInSecs(blockingTimeFrac.denominator.toLong)

    })
    dse.workload.dataBlocks.foreach(t => rebuilt.addVertex(t.getViewedVertex))
    dse.schedHwModel.allocatedSchedulers.foreach(s => rebuilt.addVertex(s.getViewedVertex))
    dse.schedHwModel.hardware.storageElems.foreach(m => rebuilt.addVertex(m.getViewedVertex))
    dse.schedHwModel.hardware.processingElems.zipWithIndex.foreach((m, j) =>
      rebuilt.addVertex(m.getViewedVertex)
      val module = AnalysedGenericProcessingModule.enforce(m)
      module.setUtilization(
        active4StageDurationModule.durations.zipWithIndex
          .filter((ws, i) => taskExecution(i).isInstantiatedTo(j))
          .map((w, i) =>
            //scribe.debug(s"task n ${i} Wcet: (raw ${durations(i)(j)})")
            (Rational(w(j).getValue)
              / (periods(i))).toDouble
          )
          .sum
      )
    )
    taskExecution.zipWithIndex.foreach((exe, i) => {
      dse.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((scheduler, j) =>
        // val j         = output.getIntVal(exe)
        val task      = dse.workload.tasks(i)
        val scheduler = dse.schedHwModel.allocatedSchedulers(j)
        Scheduled.enforce(task).insertSchedulersPort(rebuilt, scheduler)
        GreyBox.enforce(scheduler).insertContainedPort(rebuilt, Visualizable.enforce(task))
      )
    })
    taskMapping.zipWithIndex.foreach((mapping, i) => {
      val j      = mapping.getValue() // output.getIntVal(mapping)
      val task   = dse.workload.tasks(i)
      val memory = dse.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(task).insertMappingHostsPort(rebuilt, memory)
      // rebuilt.connect(task, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      // GreyBox.enforce(memory)
      // rebuilt.connect(memory, task, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    dataBlockMapping.zipWithIndex.foreach((mapping, i) => {
      val j       = mapping.getValue() // output.getIntVal(mapping)
      val channel = dse.workload.dataBlocks(i)
      val memory  = dse.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(channel).insertMappingHostsPort(rebuilt, memory)
      // rebuilt.connect(channel, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      GreyBox.enforce(memory).insertContainedPort(rebuilt, Visualizable.enforce(channel))
      // rebuilt.connect(memory, channel, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    rebuilt
  }

  def allMemorySizeNumbers() =
    (dse.platform.hardware.storageSizes ++
      dse.workload.messagesMaxSizes ++
      dse.workload.processSizes).filter(_ > 0L)

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

}
