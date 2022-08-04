package idesyde.identification.models.choco.workload

import idesyde.identification.interfaces.ChocoCPForSyDeDecisionModel

import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph
import org.chocosolver.solver.Model
import org.apache.commons.math3.util.ArithmeticUtils
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.Task
import idesyde.identification.models.workload.ForSyDePeriodicWorkload

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import org.chocosolver.solver.Solution
import forsyde.io.java.typed.viewers.decision.Scheduled
import forsyde.io.java.core.EdgeTrait
import forsyde.io.java.typed.viewers.visualization.GreyBox
import forsyde.io.java.typed.viewers.decision.MemoryMapped
import forsyde.io.java.typed.viewers.platform.runtime.FixedPriorityScheduler
import forsyde.io.java.typed.viewers.execution.Task
import idesyde.identification.models.workload.DependentDeadlineMonotonicOrdering

import math.Ordering.Implicits.infixOrderingOps
import forsyde.io.java.typed.viewers.decision.results.AnalysedTask
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.selectors.variables.FirstFail
import org.chocosolver.solver.search.strategy.selectors.values.IntDomainMin
import org.chocosolver.solver.search.strategy.assignments.DecisionOperator
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import org.chocosolver.solver.variables.IntVar
import forsyde.io.java.typed.viewers.decision.results.AnalysedGenericProcessingModule
import org.chocosolver.solver.constraints.Constraint
import org.apache.commons.math3.util.FastMath
import idesyde.utils.BigFractionIsNumeric
import idesyde.exploration.explorers.SimpleWorkloadBalancingDecisionStrategy
import idesyde.utils.BigFractionIsMultipliableFractional
import idesyde.utils.MultipliableFractional
import org.chocosolver.solver.variables.BoolVar
import forsyde.io.java.typed.viewers.visualization.Visualizable
import idesyde.identification.models.choco.FixedPriorityConstraintsMixin
import idesyde.identification.models.choco.BaselineTimingConstraintsMixin
import idesyde.identification.models.choco.ExtendedPrecedenceConstraintsMixin
import idesyde.identification.models.choco.Active4StageDurationMixin
import idesyde.identification.models.choco.BaselineMemoryConstraints

final case class PeriodicTaskToSchedHWChoco(
    val sourceForSyDeDecisionModel: PeriodicTaskToSchedHW
) extends ChocoCPForSyDeDecisionModel
    with FixedPriorityConstraintsMixin
    with BaselineTimingConstraintsMixin
    with ExtendedPrecedenceConstraintsMixin
    with Active4StageDurationMixin
    with BaselineMemoryConstraints {

  given MultipliableFractional[BigFraction] = BigFractionIsMultipliableFractional()
  //given Ordering[Task]       = DependentDeadlineMonotonicOrdering(sourceForSyDeDecisionModel.taskModel)

  val coveredVertexes = sourceForSyDeDecisionModel.coveredVertexes

  // section for time multiplier calculation
  val timeValues =
    (sourceForSyDeDecisionModel.taskModel.periods ++ sourceForSyDeDecisionModel.wcets.flatten ++ sourceForSyDeDecisionModel.taskModel.relativeDeadlines)
  var timeMultiplier = 1L
  while (
    timeValues
      .map(_.multiply(timeMultiplier))
      .exists(d => d.doubleValue < 1) && timeMultiplier < Int.MaxValue / 4
  ) {
    timeMultiplier *= 10
  }
  // scribe.debug(timeMultiplier.toString)

  // do the same for memory numbers
  var memoryMultipler = 1L
  while (
    allMemorySizeNumbers().forall(_ / memoryMultipler >= 100) && memoryMultipler < Int.MaxValue
  ) {
    memoryMultipler *= 10L
  }
  // scribe.debug(memoryMultipler.toString)
  // scribe.debug(allMemorySizeNumbers().mkString("[", ",", "]"))

  // create the variables that each Mixin requires
  val periods    = sourceForSyDeDecisionModel.taskModel.periods.map(_.multiply(timeMultiplier))
  def priorities = sourceForSyDeDecisionModel.taskModel.prioritiesForDependencies
  val scaledDeadlines =
    sourceForSyDeDecisionModel.taskModel.relativeDeadlines.map(_.multiply(timeMultiplier))
  def deadlines       = scaledDeadlines
  val wcets           = sourceForSyDeDecisionModel.wcets.map(a => a.map(_.multiply(timeMultiplier)))
  def maxUtilizations = sourceForSyDeDecisionModel.maxUtilization

  // build the model so that it can be acessed later
  val model = Model()
  // the true decision variables
  val taskExecution = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.map((p, j) =>
      if (sourceForSyDeDecisionModel.wcets(i)(j).compareTo(BigFraction.ZERO) > 0) {
        model.boolVar("exe_" + t.getIdentifier)
      } else {
        model.boolVar("exe_" + t.getIdentifier, false)
      }
    //  model.boolVar(
    // "exe_" + t.getViewedVertex.getIdentifier,
    // sourceForSyDeDecisionModel
    //   .wcets(i)(p)
    //   .zipWithIndex
    //   .filter((p, j) => p.compareTo(BigFraction.MINUS_ONE) > 0)
    //   // keep only the cores which have proven schedulability tests in the execution
    //   .filter((p, j) =>
    //     sourceForSyDeDecisionModel.schedHwModel.isStaticCycle(j) || sourceForSyDeDecisionModel.schedHwModel
    //       .isFixedPriority(j)
    //   )
    //   .map((p, j) => j) // keep the processors where WCEt is defined
    )
  )
  // scribe.debug((sourceForSyDeDecisionModel
  //     .wcets(i)
  //     .zipWithIndex
  //     .filter((p, j) => p.compareTo(BigFraction.MINUS_ONE) > 0)
  //     // keep only the cores which have proven schedulability tests in the execution
  //     .filter((p, j) =>
  //       sourceForSyDeDecisionModel.schedHwModel.isStaticCycle(j) || sourceForSyDeDecisionModel.schedHwModel
  //         .isFixedPriority(j)
  //     )
  //     .map((p, j) => j)).mkString("[", ",", "]"))
  // )
  val taskMapping = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "map_" + t.getViewedVertex.getIdentifier,
      sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex
        .filter((m, j) => sourceForSyDeDecisionModel.taskModel.taskSizes(i) <= m.getSpaceInBits)
        .map((m, j) => j)
    )
  )
  val dataBlockMapping = sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.map((c, i) =>
    model.intVar(
      "map_" + c.getViewedVertex.getIdentifier,
      sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex
        .filter((m, j) =>
          sourceForSyDeDecisionModel.taskModel.messageQueuesSizes(i) <= m.getSpaceInBits
        )
        .map((m, j) => j)
    )
  )
  val taskCommMapping = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.communicationElems.zipWithIndex.map((ce, j) =>
      model.boolVar("map_" + t.getViewedVertex.getIdentifier + "_comm_" + ce.getIdentifier)
    )
  )
  val dataBlockCommMapping =
    sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.map((c, i) =>
      sourceForSyDeDecisionModel.schedHwModel.hardware.communicationElems.zipWithIndex.map(
        (ce, j) =>
          model.boolVar("map_" + c.getViewedVertex.getIdentifier + "_comm_" + ce.getIdentifier)
      )
    )
  def taskCommunicationMapping = taskCommMapping
  def dataCommunicationMapping = dataBlockCommMapping

  // tasks and data must be mapped to at least one location
  taskExecution.foreach(ts => model.or(ts: _*))
  // def taskReadsMessage = sourceForSyDeDecisionModel.taskModel.tasks.
  // --- durations ----
  // scribe.debug(sourceForSyDeDecisionModel.wcets.map(_.map(f => f.multiply(timeMultiplier).getNumeratorAsInt.toString).mkString("[", ",", "]")).mkString("[", ",", "]"))
  // scribe.debug(sourceForSyDeDecisionModel.wcets.map(_.map(f => f.toString).mkString("[", ",", "]")).mkString("[", ",", "]"))
  def executionTime: Array[Array[Int]] =
    sourceForSyDeDecisionModel.wcets.map(_.map(f => f.multiply(timeMultiplier).getNumeratorAsInt))
  // !-- durations ----
  // auxiliary variables
  val responseTimes =
    sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
      // scribe.debug(t.getIdentifier + ": " + wcets(i)
      //     .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
      //     .min
      //     .doubleValue
      //     .ceil
      //     .toInt.toString + " dead line " +
      // scaledDeadlines(i)
      //     .doubleValue
      //     .floor
      //     .toInt.toString)
      model.intVar(
        "rt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        wcets(i)
          .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
          .min
          .doubleValue
          .ceil
          .toInt,
        scaledDeadlines(i).doubleValue.floor.toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val blockingTimes =
    sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
      // scribe.debug(
      //   s"B: ${sourceForSyDeDecisionModel.taskModel.relativescaledDeadlines(i).doubleValue.floor.toInt} - " +
      //   s"W: ${sourceForSyDeDecisionModel.wcets(i).filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0).min.multiply(timeMultiplier).doubleValue.ceil.toInt}"
      // )
      model.intVar(
        "bt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        0,
        scaledDeadlines(i).doubleValue.floor.toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val durationsExec = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.map((p, j) =>
      model.intVar(
        s"exe_wc_${t.getIdentifier}",
        if (
          sourceForSyDeDecisionModel
            .wcets(i)(j)
            .compareTo(BigFraction.MINUS_ONE) > 0
        ) then
          Array(
            0,
            sourceForSyDeDecisionModel
              .wcets(i)(j)
              .multiply(timeMultiplier)
              .doubleValue
              .ceil
              .toInt
          )
        else Array(0)
      )
    )
  )
  val durationsFetch = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.map((p, j) =>
      model.intVar(
        "fetch_wc" + t.getViewedVertex.getIdentifier,
        0,
        scaledDeadlines(i).doubleValue.floor.toInt,
        true
      )
    )
  )
  val durationsRead = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.map((p, j) =>
      model.intVar(
        "input_wc" + t.getViewedVertex.getIdentifier,
        0,
        scaledDeadlines(i).doubleValue.floor.toInt,
        true
      )
    )
  )
  val durationsWrite = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.map((p, j) =>
      model.intVar(
        "output_wc" + t.getViewedVertex.getIdentifier,
        0,
        scaledDeadlines(i).doubleValue.floor.toInt,
        true
      )
    )
  )
  val durations = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.map((p, j) =>
      durationsExec(i)(j)
        .add(durationsFetch(i)(j))
        .add(durationsRead(i)(j))
        .add(durationsWrite(i)(j))
        .intVar
    )
  )
  val taskTravelTime = sourceForSyDeDecisionModel.taskModel.taskSizes.map(d =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.communicationModuleBandWidthBitPerSec.map(b =>
      // TODO: check if this is truly conservative (pessimistic) or not
      b.multiply(timeMultiplier).divide(memoryMultipler).multiply(d).intValue
    )
  )
  val dataTravelTime = sourceForSyDeDecisionModel.taskModel.dataBlocks.map(d =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.communicationModuleBandWidthBitPerSec.map(b =>
      // TODO: check if this is truly conservative (pessimistic) or not
      b.multiply(timeMultiplier)
        .divide(memoryMultipler)
        .divide(d.getMaxSizeInBits)
        .reciprocal
        .doubleValue
        .floor
        .toInt + 1
    )
  )

  def taskReadsData  = sourceForSyDeDecisionModel.taskModel.taskReadsMessageQueue
  def taskWritesData = sourceForSyDeDecisionModel.taskModel.taskWritesMessageQueue

  // memory aux variables
  // the +1 is for ceil
  val memoryUsage = sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.map(mem =>
    //scribe.debug((mem.getSpaceInBits / memoryMultipler).toInt.toString)
    model.intVar(
      "load_" + mem.getViewedVertex.identifier,
      0,
      (mem.getSpaceInBits / memoryMultipler).toInt + 1
    )
  )
  def dataMapping = dataBlockMapping
  val taskSize = sourceForSyDeDecisionModel.taskModel.taskSizes
    .map(_ / memoryMultipler + 1)
    .map(_.toInt)
  val dataSize = sourceForSyDeDecisionModel.taskModel.messageQueuesSizes
    .map(_ / memoryMultipler + 1)
    .map(_.toInt)
  // memory constraints
  postTaskAndDataMemoryConstraints()
  // Members declared in idesyde.identification.models.choco.ExtendedPrecedenceConstraintsMixin
  def canBeFollowedBy = sourceForSyDeDecisionModel.taskModel.interTaskOccasionalBlock

  // for other computation
  def allowedProc2MemoryDataPaths =
    sourceForSyDeDecisionModel.schedHwModel.hardware.routesProc2Memory
  postActive4StageDurationsConstraints()

  // timing constraints
  postInterProcessorBlocking()

  // basic utilization
  postMinimalResponseTimesByBlocking()
  postMaximumUtilizations()

  // for each FP scheduler
  // rt >= bt + sum of all higher prio tasks in the same CPU
  sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex
    .filter((s, j) => sourceForSyDeDecisionModel.schedHwModel.isFixedPriority(j))
    .foreach((s, j) => {
      postFixedPrioriPreemtpiveConstraint(j)
    })
  // for each SC scheduler
  sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((task, i) => {
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex
      .filter((s, j) => sourceForSyDeDecisionModel.schedHwModel.isStaticCycle(j))
      .foreach((s, j) => {
        postStaticCyclicExecutiveConstraint(i, j)
        //val cons = Constraint(s"FPConstrats${j}", DependentWorkloadFPPropagator())
      })
  })

  // symmetries
  // sourceForSyDeDecisionModel.schedHwModel.topologicallySymmetricGroups.map(symGroup => {
  //   symGroup.map(sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.indexOf(_)).map(idx => {
  //     // model.count(s"count_exec_${idx}", )
  //   })
  // })
  // sourceForSyDeDecisionModel.schedHwModel.schedulers.zipWithIndex.foreach((p, i) => {

  // })

  // Dealing with objectives
  val peIsUsed =
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.map((pe, j) =>
      model.or(taskExecution.map(t => t(j)): _*).reify
    )
  val nUsedPEs = model.intVar(
    "nUsedPEs",
    1,
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.length
  )
  // count different ones
  model.sum(peIsUsed, "=", nUsedPEs).post
  // this flips the direction of the variables since the objective must be MAX
  // val nFreePEs = model
  // .intVar(sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.length)
  // .sub(nUsedPEs)
  // .intVar

  def chocoModel: Model = model
  // the objectives so far are just the minimization of used PEs
  // the inMinusView is necessary to transform a minimization into
  // a maximization
  override def modelObjectives = Array(model.intMinusView(nUsedPEs))

  // create the methods that each mixing requires
  // def sufficientRMSchedulingPoints(taskIdx: Int): Array[BigFraction] =
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
  def postStaticCyclicExecutiveConstraint(taskIdx: Int, schedulerIdx: Int): Unit =
    model.ifThen(
      taskExecution(taskIdx)(schedulerIdx),
      responseTimes(taskIdx)
        .ge(
          durations(taskIdx)(schedulerIdx)
            .add(blockingTimes(taskIdx))
            .add(
              model
                .sum(
                  s"sc_interference${taskIdx}_${schedulerIdx}",
                  durations.zipWithIndex
                    .filter((ws, k) => k != taskIdx)
                    .filterNot((ws, k) =>
                      // leave tasks k which i occasionally block
                      sourceForSyDeDecisionModel.taskModel.interTaskAlwaysBlocks(taskIdx)(k)
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
      (0 until sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.length).toArray,
      periods,
      taskExecution,
      utilizations,
      durations
    ),
    Search.intVarSearch(
      FirstFail(model),
      IntDomainMin(),
      DecisionOperatorFactory.makeIntEq,
      (taskMapping ++ dataBlockMapping ++ responseTimes ++ durationsExec.flatten ++ blockingTimes ++
        durationsRead.flatten ++ durationsWrite.flatten ++ durationsFetch.flatten ++
        durations.flatten ++ utilizations :+ nUsedPEs): _*
    )
  )

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val rebuilt = ForSyDeSystemGraph()
    sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) => {
      rebuilt.addVertex(t.getViewedVertex)
      val analysed         = AnalysedTask.enforce(t)
      val responseTimeFrac = BigFraction(responseTimes(i).getValue, timeMultiplier).reduce
      val blockingTimeFrac = BigFraction(blockingTimes(i).getValue, timeMultiplier).reduce
      // scribe.debug(s"task ${t.getIdentifier} RT: (raw ${responseTimes(i).getValue}) ${responseTimeFrac.doubleValue}")
      // scribe.debug(s"task ${t.getIdentifier} BT: (raw ${blockingTimes(i).getValue}) ${blockingTimeFrac.doubleValue}")
      analysed.setWorstCaseResponseTimeNumeratorInSecs(responseTimeFrac.getNumeratorAsLong)
      analysed.setWorstCaseResponseTimeDenominatorInSecs(responseTimeFrac.getDenominatorAsLong)
      analysed.setWorstCaseBlockingTimeNumeratorInSecs(blockingTimeFrac.getNumeratorAsLong)
      analysed.setWorstCaseBlockingTimeDenominatorInSecs(blockingTimeFrac.getDenominatorAsLong)

    })
    sourceForSyDeDecisionModel.taskModel.dataBlocks.foreach(t =>
      rebuilt.addVertex(t.getViewedVertex)
    )
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.foreach(s =>
      rebuilt.addVertex(s.getViewedVertex)
    )
    sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.foreach(m =>
      rebuilt.addVertex(m.getViewedVertex)
    )
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.foreach((m, j) =>
      rebuilt.addVertex(m.getViewedVertex)
      val module = AnalysedGenericProcessingModule.enforce(m)
      module.setUtilization(
        durations.zipWithIndex
          .filter((ws, i) => taskExecution(i)(j).isInstantiatedTo(1))
          .map((w, i) =>
            //scribe.debug(s"task n ${i} Wcet: (raw ${durations(i)(j)})")
            BigFraction(w(j).getValue)
              .divide(periods(i))
              .doubleValue
          )
          .sum
      )
    )
    taskExecution.zipWithIndex.foreach((exe, i) => {
      sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach(
        (scheduler, j) =>
          // val j         = output.getIntVal(exe)
          val task      = sourceForSyDeDecisionModel.taskModel.tasks(i)
          val scheduler = sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers(j)
          Scheduled.enforce(task).insertSchedulersPort(rebuilt, scheduler)
          GreyBox.enforce(scheduler).insertContainedPort(rebuilt, Visualizable.enforce(task))
      )
    })
    taskMapping.zipWithIndex.foreach((mapping, i) => {
      val j      = output.getIntVal(mapping)
      val task   = sourceForSyDeDecisionModel.taskModel.tasks(i)
      val memory = sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(task).insertMappingHostsPort(rebuilt, memory)
      // rebuilt.connect(task, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      // GreyBox.enforce(memory)
      // rebuilt.connect(memory, task, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    dataBlockMapping.zipWithIndex.foreach((mapping, i) => {
      val j       = output.getIntVal(mapping)
      val channel = sourceForSyDeDecisionModel.taskModel.dataBlocks(i)
      val memory  = sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(channel).insertMappingHostsPort(rebuilt, memory)
      // rebuilt.connect(channel, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      GreyBox.enforce(memory).insertContainedPort(rebuilt, Visualizable.enforce(channel))
      // rebuilt.connect(memory, channel, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    rebuilt
  }

  def allMemorySizeNumbers() =
    (sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.map(_.getSpaceInBits.toLong) ++
      sourceForSyDeDecisionModel.taskModel.messageQueuesSizes ++
      sourceForSyDeDecisionModel.taskModel.taskSizes).filter(_ > 0L)

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

}
