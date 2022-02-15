package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoCPDecisionModel

import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph
import org.chocosolver.solver.Model
import org.apache.commons.math3.util.ArithmeticUtils
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import idesyde.identification.models.workload.PeriodicWorkload

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
import idesyde.exploration.explorers.SimpleListSchedulingDecisionStrategy
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import org.chocosolver.solver.variables.IntVar

final case class PeriodicTaskToSchedHWChoco(
    val sourceDecisionModel: PeriodicTaskToSchedHW
) extends ChocoCPDecisionModel:

  val coveredVertexes = sourceDecisionModel.coveredVertexes

  given Ordering[Task] = DependentDeadlineMonotonicOrdering(sourceDecisionModel.taskModel)

  // section for time multiplier calculation
  var multiplierForNoDenominator =
    (sourceDecisionModel.taskModel.periods ++ sourceDecisionModel.wcets.flatten)
      .map(_.getDenominatorAsLong)
      .reduce((d1, d2) => ArithmeticUtils.lcm(d1, d2))
  // while there are significant zeros that can be taken away
  // var tenthDivision = 1L
  // while (sourceDecisionModel.taskModel.periods.map(_.getNumeratorAsLong).min * multiplier % tenthDivision == 0)
  //   tenthDivision *= 10L
  val multiplier = multiplierForNoDenominator

  // do the same for memory numbers
  var memoryMultipler = 1L
  while (allMemorySizeNumbers().min % memoryMultipler == 0)
    memoryMultipler *= 10L

  // build the model so that it can be acessed later
  val model = Model()
  // the true decision variables
  val taskExecution = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "exe_" + t.getViewedVertex.getIdentifier,
      sourceDecisionModel
        .wcets(i)
        .zipWithIndex
        .filter((p, j) => p.compareTo(BigFraction.MINUS_ONE) > 0)
        .map((p, j) => j) // keep the processors where WCEt is defined
    )
  )
  val taskMapping = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    model.intVar(
      "map_" + t.getViewedVertex.getIdentifier,
      sourceDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex
        .filter((m, j) => sourceDecisionModel.taskModel.taskSizes(i) <= m.getSpaceInBits)
        .map((m, j) => j)
    )
  )
  val channelMapping = sourceDecisionModel.taskModel.channels.zipWithIndex.map((c, i) =>
    model.intVar(
      "map_" + c.getViewedVertex.getIdentifier,
      sourceDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex
        .filter((m, j) => sourceDecisionModel.taskModel.channelSizes(i) <= m.getSpaceInBits)
        .map((m, j) => j)
    )
  )
  // auxiliary variables
  val responseTimes =
    sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
      model.intVar(
        "rt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        sourceDecisionModel
          .wcets(i)
          .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
          .min
          .multiply(multiplier)
          .doubleValue
          .ceil
          .toInt,
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(multiplier)
          .doubleValue
          .floor
          .toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val blockingTimes =
    sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
      model.intVar(
        "bt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        0,
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(multiplier)
          .doubleValue
          .floor
          .toInt -
          sourceDecisionModel
            .wcets(i)
            .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
            .min
            .multiply(multiplier)
            .doubleValue
            .ceil
            .toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val wcExecution = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        s"exe_wc_${t.getIdentifier}_${s.getIdentifier}",
        Array(
          0,
          sourceDecisionModel
            .wcets(i)(j)
            .multiply(multiplier)
            .doubleValue
            .ceil
            .toInt
        )
      )
    )
  )
  val wcFetch = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        "fetch_wc" + t.getViewedVertex.getIdentifier + s.getIdentifier,
        0,
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(multiplier)
          .doubleValue
          .floor
          .toInt,
        true
      )
    )
  )
  val wcInput = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        "input_wc" + t.getViewedVertex.getIdentifier + s.getIdentifier,
        0,
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(multiplier)
          .doubleValue
          .floor
          .toInt,
        true
      )
    )
  )
  val wcOutput = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        "output_wc" + t.getViewedVertex.getIdentifier + s.getIdentifier,
        0,
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(multiplier)
          .doubleValue
          .floor
          .toInt,
        true
      )
    )
  )
  val wcet = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex.map((s, j) =>
      wcExecution(i)(j).add(wcFetch(i)(j)).add(wcInput(i)(j)).add(wcOutput(i)(j)).intVar
    )
  )
  sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex.map((s, j) =>
      model.ifThenElse(
        taskExecution(i).eq(j).decompose,
        wcExecution(i)(j)
          .eq(sourceDecisionModel.wcets(i)(j).multiply(multiplier).getNumeratorAsInt)
          .decompose,
        wcExecution(i)(j).eq(0).decompose
      )
    )
  )
  // memory aux variables
  // the +1 is for ceil
  val memoryUsage = sourceDecisionModel.schedHwModel.hardware.storageElems.map(mem =>
    model.intVar(
      "load_" + mem.getViewedVertex.identifier,
      0,
      (mem.getSpaceInBits / memoryMultipler).toInt + 1
    )
  )
  // memory constraints
  model
    .binPacking(
      taskMapping ++ channelMapping,
      sourceDecisionModel.taskModel.taskSizes
        .map(_ / memoryMultipler + 1)
        .map(_.toInt) ++ sourceDecisionModel.taskModel.channelSizes
        .map(_ / memoryMultipler + 1)
        .map(_.toInt),
      memoryUsage,
      0 // 0 offset for no minizinc
    )
    .post
  // timing constraints
  sourceDecisionModel.taskModel.reactiveStimulus.zipWithIndex.foreach((s, i) => {
    val src = sourceDecisionModel.taskModel.reactiveStimulusSrc(i)
    val dst = sourceDecisionModel.taskModel.reactiveStimulusDst(i)
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex.map((s, j) => {
      blockingTimes(dst).ge(responseTimes(src).add(wcet(src)(j))).post
      responseTimes(i).ge(blockingTimes(i).add(wcet(i)(j))).post
    })
  })
  // for each FP scheduler
  // rt >= bt + sum of all higher prio tasks in the same CPU
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((task, i) => {
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex
      .filter((s, j) => sourceDecisionModel.schedHwModel.isFixedPriority(j))
      .foreach((s, j) => {})
  })
  // for each SC scheduler
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((task, i) => {
    sourceDecisionModel.schedHwModel.schedulers.zipWithIndex
      .filter((s, j) => sourceDecisionModel.schedHwModel.isStaticCycle(j))
      .foreach((s, j) => {
        postStaticCyclicExecutiveConstraint(i, j)
      })
  })

  // Dealing with objectives
  val nUsedPEs = model.intVar(
    "nUsedPEs",
    0,
    sourceDecisionModel.schedHwModel.hardware.processingElems.length - 1
  )
  // count different ones
  model.atMostNValues(taskExecution, nUsedPEs, true).post
  // this flips the direction of the variables since the objective must be MAX
  val nFreePEs = model
    .intVar(sourceDecisionModel.schedHwModel.hardware.processingElems.length)
    .sub(nUsedPEs)
    .intVar

  def chocoModel: Model = model

  override def modelObjectives = Array(nFreePEs)

  /** This method sets up the Worst case schedulability test for a task.
    *
    * The mathetical 'representation' is responseTime(i) >= blockingTime(i) + wcet(i) + sum(wcet of
    * all higher prios in same scheduler)
    *
    * @param taskIdx
    *   the task to be posted (takes into account all others)
    * @param schedulerIdx
    *   the scheduler to be posted
    */
  def postStaticCyclicExecutiveConstraint(taskIdx: Int, schedulerIdx: Int): Unit =
    model.ifThen(
      taskExecution(taskIdx).eq(schedulerIdx).decompose,
      responseTimes(taskIdx)
        .eq(
          wcet(taskIdx)(schedulerIdx)
            .add(blockingTimes(taskIdx))
            .add(
              model
                .sum(
                  s"sc_interference${taskIdx}_${schedulerIdx}",
                  wcet.zipWithIndex
                    .filter((ws, k) => k != taskIdx)
                    .filter((ws, k) =>
                      sourceDecisionModel.taskModel.tasks(taskIdx) > sourceDecisionModel.taskModel
                        .tasks(k)
                    )
                    .map((ws, k) => {
                      ws(schedulerIdx)
                    })
                    .toArray: _*
                )
            )
        )
        .decompose
    )

  override val strategies = Array(
    SimpleListSchedulingDecisionStrategy(
      (0 until sourceDecisionModel.schedHwModel.schedulers.length).toArray,
      taskExecution,
      responseTimes
    ),
    Search.intVarSearch(
      FirstFail(model),
      IntDomainMin(),
      DecisionOperatorFactory.makeIntEq,
      (responseTimes ++ wcFetch.flatten ++
        wcInput.flatten ++ wcOutput.flatten ++
        wcet.flatten ++ taskMapping ++ channelMapping :+ nFreePEs): _*
    )
  )

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val rebuilt = ForSyDeSystemGraph()
    sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) => {
      rebuilt.addVertex(t.getViewedVertex)
      val analysed         = AnalysedTask.enforce(t)
      val responseTimeFrac = BigFraction(responseTimes(i).getValue, multiplier).reduce
      analysed.setWorstCaseResponseTimeNumeratorInSecs(responseTimeFrac.getNumeratorAsLong)
      analysed.setWorstCaseResponseTimeDenominatorInSecs(responseTimeFrac.getDenominatorAsLong)
    })
    sourceDecisionModel.taskModel.channels.foreach(t => rebuilt.addVertex(t.getViewedVertex))
    sourceDecisionModel.schedHwModel.schedulers.foreach(s => rebuilt.addVertex(s.getViewedVertex))
    sourceDecisionModel.schedHwModel.hardware.storageElems.foreach(m =>
      rebuilt.addVertex(m.getViewedVertex)
    )
    taskExecution.zipWithIndex.foreach((exe, i) => {
      val j         = output.getIntVal(exe)
      val task      = sourceDecisionModel.taskModel.tasks(i)
      val scheduler = sourceDecisionModel.schedHwModel.schedulers(j)
      Scheduled.enforce(task)
      rebuilt.connect(task, scheduler, "scheduler", EdgeTrait.DECISION_ABSTRACTSCHEDULING)
      GreyBox.enforce(scheduler)
      rebuilt.connect(scheduler, task, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    taskMapping.zipWithIndex.foreach((mapping, i) => {
      val j      = output.getIntVal(mapping)
      val task   = sourceDecisionModel.taskModel.tasks(i)
      val memory = sourceDecisionModel.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(task)
      rebuilt.connect(task, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      // GreyBox.enforce(memory)
      // rebuilt.connect(memory, task, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    channelMapping.zipWithIndex.foreach((mapping, i) => {
      val j       = output.getIntVal(mapping)
      val channel = sourceDecisionModel.taskModel.channels(i)
      val memory  = sourceDecisionModel.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(channel)
      rebuilt.connect(channel, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      GreyBox.enforce(memory)
      rebuilt.connect(memory, channel, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    rebuilt
  }

  def absoluteDeadlines(multiplier: Long = 1L) =
    sourceDecisionModel.taskModel.periodicTasks.zipWithIndex
      .map((t, i) =>
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(sourceDecisionModel.taskModel.tasksNumInstances(i))
          .add(sourceDecisionModel.taskModel.offsets(i))
      )

  def allMemorySizeNumbers() =
    (sourceDecisionModel.schedHwModel.hardware.storageElems.map(_.getSpaceInBits.toLong) ++
      sourceDecisionModel.taskModel.channelSizes ++
      sourceDecisionModel.taskModel.taskSizes)

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

end PeriodicTaskToSchedHWChoco
