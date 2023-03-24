package idesyde.identification.choco.models.mixed

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
import spire.math._
import spire.compat.fractional
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import org.chocosolver.solver.exception.ContradictionException
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsModule
import idesyde.identification.choco.models.BaselineTimingConstraintsModule
import idesyde.identification.choco.models.workload.ExtendedPrecedenceConstraintsModule
import idesyde.identification.choco.models.workload.FixedPriorityConstraintsModule
import idesyde.identification.choco.models.mixed.Active4StageDurationModule
import idesyde.utils.HasUtils
import idesyde.identification.choco.interfaces.ChocoModelMixin
import idesyde.identification.common.models.workload.CommunicatingExtendedDependenciesPeriodicWorkload
import idesyde.identification.common.models.mixed.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.identification.DecisionModel
import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.choco.ChocoDecisionModel

// object ConMonitorObj extends IMonitorContradiction {

//   def onContradiction(cex: ContradictionException): Unit = {
//     println(cex.toString())
//   }
// }

final case class ChocoComDepTasksToMultiCore(
    val dse: PeriodicWorkloadToPartitionedSharedMultiCore
) extends StandardDecisionModel
    with ChocoDecisionModel
    with HasUtils {

  val coveredElements = dse.coveredElements

  val coveredElementRelations = dse.coveredElementRelations

  override def dominates[D <: DecisionModel](other: D): Boolean = other match {
    case o: ChocoComDepTasksToMultiCore =>
      dse.dominates(o.dse)
    case _ => super.dominates(other)
  }

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
      .map(t => t * (timeMultiplier))
      .sum < Int.MaxValue / 10 - 1 // the four is due to how the sum is done for wcet
  ) {
    timeMultiplier *= 10
  }
  // scribe.debug(timeMultiplier.toString)

  // do the same for memory numbers
  val memoryValues  = dse.platform.hardware.storageSizes
  var memoryDivider = 1L
  while (
    memoryValues.forall(
      ceil(_, memoryDivider) >= Int.MaxValue / 100000
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
        .toArray
    )
  )
  val dataBlockMapping = dse.workload.dataChannels.zipWithIndex.map((c, i) =>
    chocoModel.intVar(
      s"data_map($c)",
      dse.platform.hardware.storageSizes.zipWithIndex
        .filter((m, j) => dse.workload.messagesMaxSizes(i) <= m)
        .map((m, j) => j)
        .toArray
    )
  )
  val memoryMappingModule = SingleProcessSingleMessageMemoryConstraintsModule(
    chocoModel,
    dse.workload.processSizes.map(ceil(_, memoryDivider)).map(_.toInt).toArray,
    dse.workload.messagesMaxSizes.map(ceil(_, memoryDivider)).map(_.toInt).toArray,
    dse.platform.hardware.storageSizes
      .map(ceil(_, memoryDivider))
      .map(_.toInt)
      .toArray
  )

  // timing
  val taskExecution = dse.workload.processes.zipWithIndex.map((t, i) =>
    chocoModel.intVar(
      s"task_map($t)",
      dse.platform.hardware.processingElems.zipWithIndex
        .filter((m, j) => dse.wcets(i)(j) > -1)
        .map((m, j) => j)
        .toArray
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
    taskExecution.toArray,
    responseTimes.toArray,
    blockingTimes.toArray,
    dse.workload.interTaskOccasionalBlock
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
    taskExecution.toArray,
    memoryMappingModule.processesMemoryMapping,
    memoryMappingModule.messagesMemoryMapping,
    processingElemsVirtualChannelInCommElem.map(_.toArray).toArray,
    timeMultiplier,
    memoryDivider
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
  dse.workload.tasks.zipWithIndex.foreach((task, i) => {
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
  chocoModel.nValues(taskExecution.toArray, nUsedPEs).post()

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
                      dse.workload.interTaskAlwaysBlocks(taskIdx)(k)
                    )
                    .map((w, k) => w)
                    .toArray: _*
                )
            )
        )
        .decompose
    )

  override val strategies = Array(
    SimpleWorkloadBalancingDecisionStrategy(
      (0 until dse.platform.runtimes.schedulers.length).toArray,
      periods.toArray,
      taskExecution.toArray,
      baselineTimingConstraintsModule.utilizations,
      active4StageDurationModule.durations,
      wcets.map(_.map(_.ceil.toInt).toArray).toArray
    ),
    Search.inputOrderUBSearch(nUsedPEs),
    Search.activityBasedSearch(taskMapping: _*),
    Search.activityBasedSearch(dataBlockMapping: _*),
    Search.minDomLBSearch(responseTimes: _*),
    Search.minDomLBSearch(blockingTimes: _*),
    Search.minDomLBSearch(memoryMappingModule.processesMemoryMapping: _*),
    Search.minDomLBSearch(memoryMappingModule.messagesMemoryMapping: _*)
    // Search.intVarSearch(
    //   FirstFail(chocoModel),
    //   IntDomainMin(),
    //   DecisionOperatorFactory.makeIntEq,
    //   (durationsRead.flatten ++ durationsWrite.flatten ++ durationsFetch.flatten ++
    //     durations.flatten ++ utilizations ++ taskCommunicationMapping.flatten ++ dataBlockCommMapping.flatten): _*
    // )
  )

  def rebuildFromChocoOutput(output: Solution): Set[DecisionModel] = {
    val processMappings = memoryMappingModule.processesMemoryMapping.zipWithIndex
      .map((v, i) =>
        dse.workload.tasks(i) -> dse.platform.hardware.storageElems(output.getIntVal(v))
      )
      .toVector
    val processSchedulings = taskExecution.zipWithIndex
      .map((v, i) => dse.workload.tasks(i) -> dse.platform.runtimes.schedulers(output.getIntVal(v)))
      .toVector
    val channelMappings = memoryMappingModule.messagesMemoryMapping.zipWithIndex
      .map((v, i) =>
        dse.workload.dataChannels(i) -> dse.platform.hardware.storageElems(output.getIntVal(v))
      )
      .toVector
    // val channelSlotAllocations = ???
    val full = dse.copy(
      processMappings = processMappings,
      processSchedulings = processSchedulings,
      channelMappings = channelMappings
    )
    Set(full)
  }

  def allMemorySizeNumbers() =
    (dse.platform.hardware.storageSizes ++
      dse.workload.messagesMaxSizes ++
      dse.workload.processSizes).filter(_ > 0L)

  val uniqueIdentifier = "ChocoComDepTasksToMultiCore"

}
