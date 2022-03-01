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
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import org.chocosolver.solver.variables.IntVar
import forsyde.io.java.typed.viewers.decision.results.AnalysedGenericProcessingModule
import org.chocosolver.solver.constraints.Constraint
import org.apache.commons.math3.util.FastMath
import idesyde.utils.BigFractionIsNumeric
import idesyde.exploration.explorers.SimpleWorkloadBalancingDecisionStrategy

final case class PeriodicTaskToSchedHWChoco(
    val sourceDecisionModel: PeriodicTaskToSchedHW
) extends ChocoCPDecisionModel with idesyde.identification.models.choco.FixedPriorityConstraintsMixin:

  given Ordering[Task]       = DependentDeadlineMonotonicOrdering(sourceDecisionModel.taskModel)
  given Numeric[BigFraction] = BigFractionIsNumeric()

  val coveredVertexes = sourceDecisionModel.coveredVertexes

  // section for time multiplier calculation
  val durations  = (sourceDecisionModel.taskModel.periods ++ sourceDecisionModel.wcets.flatten)
  var multiplier = 1L
  while (durations
        .map(_.multiply(multiplier))
        .exists(d => d.doubleValue < 1) && multiplier < Int.MaxValue) {
    multiplier *= 10
  }
  //scribe.debug(multiplier.toString)

  // do the same for memory numbers
  var memoryMultipler = 1L
  while (allMemorySizeNumbers().forall(_ >= Int.MaxValue.toLong)) {
    memoryMultipler *= 10L
  }
  // scribe.debug(memoryMultipler.toString)
  // scribe.debug(allMemorySizeNumbers().mkString("[", ",", "]"))

  // create the variables that each Mixin requires
  val periods = sourceDecisionModel.taskModel.periods.map(_.multiply(multiplier))
  val priorities = sourceDecisionModel.taskModel.priorities



  // build the model so that it can be acessed later
  val model = Model()
  // the true decision variables
  val taskExecution = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    // scribe.debug((sourceDecisionModel
    //     .wcets(i)
    //     .zipWithIndex
    //     .filter((p, j) => p.compareTo(BigFraction.MINUS_ONE) > 0)
    //     // keep only the cores which have proven schedulability tests in the execution
    //     .filter((p, j) =>
    //       sourceDecisionModel.schedHwModel.isStaticCycle(j) || sourceDecisionModel.schedHwModel
    //         .isFixedPriority(j)
    //     )
    //     .map((p, j) => j)).mkString("[", ",", "]"))
    model.intVar(
      "exe_" + t.getViewedVertex.getIdentifier,
      sourceDecisionModel
        .wcets(i)
        .zipWithIndex
        .filter((p, j) => p.compareTo(BigFraction.MINUS_ONE) > 0)
        // keep only the cores which have proven schedulability tests in the execution
        .filter((p, j) =>
          sourceDecisionModel.schedHwModel.isStaticCycle(j) || sourceDecisionModel.schedHwModel
            .isFixedPriority(j)
        )
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
  val dataBlockMapping = sourceDecisionModel.taskModel.dataBlocks.zipWithIndex.map((c, i) =>
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
      // scribe.debug(sourceDecisionModel
      //     .wcets(i)
      //     .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
      //     .min
      //     .multiply(multiplier)
      //     .doubleValue
      //     .ceil
      //     .toInt.toString)
      // scribe.debug(sourceDecisionModel.taskModel
      //     .relativeDeadlines(i)
      //     .multiply(multiplier)
      //     .doubleValue
      //     .floor
      //     .toInt.toString)
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
      // scribe.debug(
      //   s"B: ${sourceDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).doubleValue.floor.toInt} - " +
      //   s"W: ${sourceDecisionModel.wcets(i).filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0).min.multiply(multiplier).doubleValue.ceil.toInt}"
      // )
      model.intVar(
        "bt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        0,
        sourceDecisionModel.taskModel
          .relativeDeadlines(i)
          .multiply(multiplier)
          .doubleValue
          .floor
          .toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val wcExecution = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        s"exe_wc_${t.getIdentifier}_${s.getIdentifier}",
        if (
          sourceDecisionModel
            .wcets(i)(j)
            .compareTo(BigFraction.MINUS_ONE) > 0
        ) then
          Array(
            0,
            sourceDecisionModel
              .wcets(i)(j)
              .multiply(multiplier)
              .doubleValue
              .ceil
              .toInt
          )
        else Array(0)
      )
    )
  )
  val wcFetch = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
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
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
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
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
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
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
      wcExecution(i)(j).add(wcFetch(i)(j)).add(wcInput(i)(j)).add(wcOutput(i)(j)).intVar
    )
  )
  val channelFetchTime = sourceDecisionModel.taskModel.dataBlocks.zipWithIndex.map((c, i) =>
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((pj, j) =>
      val t = sourceDecisionModel.schedHwModel.hardware.maxTraversalTimePerBit(j).max
      model.intVar(
        s"tt${i}_${j}",
        0,
        if (t.compareTo(BigFraction.MINUS_ONE) > 0) then
          t.multiply(multiplier)
            .multiply(c.getMaxSize)
            .doubleValue
            .ceil
            .toInt
        else 0,
        true
      )
    )
  )
  val channelWriteTime = sourceDecisionModel.taskModel.dataBlocks.zipWithIndex.map((c, i) =>
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((pj, j) =>
      val t = sourceDecisionModel.schedHwModel.hardware.maxTraversalTimePerBit(j).max
      model.intVar(
        s"tt${i}_${j}",
        0,
        if (t.compareTo(BigFraction.MINUS_ONE) > 0) then
          t.multiply(multiplier)
            .multiply(c.getMaxSize)
            .doubleValue
            .ceil
            .toInt
        else 0,
        true
      )
    )
  )
  // memory aux variables
  // the +1 is for ceil
  val memoryUsage = sourceDecisionModel.schedHwModel.hardware.storageElems.map(mem =>
    //scribe.debug((mem.getSpaceInBits / memoryMultipler).toInt.toString)
    model.intVar(
      "load_" + mem.getViewedVertex.identifier,
      0,
      (mem.getSpaceInBits / memoryMultipler).toInt + 1
    )
  )
  // memory constraints
  model
    .binPacking(
      taskMapping ++ dataBlockMapping,
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
  // basic utilization
  sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((pe, j) => {
    val utilizations = sourceDecisionModel.taskModel.tasks.zipWithIndex.map((task, i) => wcet(i)(j).div(periods(i).doubleValue.floor.toInt).mul(100).intVar)
    model.sum(s"cpu_load_${pe.getIdentifier}", utilizations:_*).le(100)
  })
  // dependent-emerging blocking
  sourceDecisionModel.taskModel.reactiveStimulus.zipWithIndex.foreach((s, i) => {
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex
      .foreach((pe, j) => {
        responseTimes(i).ge(blockingTimes(i).add(wcet(i)(j))).post
      })
  })
  sourceDecisionModel.taskModel.reactiveStimulus.zipWithIndex.foreach((s, i) => {
    sourceDecisionModel.taskModel
          .reactiveStimulusSrcs(i)
          .foreach(src => {
            val dst = sourceDecisionModel.taskModel.reactiveStimulusDst(i)
            //scribe.debug(s"dst ${dst} and src ${src}")
            model.ifThen(
              taskExecution(dst).ne(taskExecution(src)).decompose,
              blockingTimes(dst).ge(responseTimes(src)).decompose
            )
          })
  })
  // for each FP scheduler
  // rt >= bt + sum of all higher prio tasks in the same CPU
  sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex
    .filter((s, j) => sourceDecisionModel.schedHwModel.isFixedPriority(j))
    .foreach((s, j) => {
      //DependentWorkloadFPPropagator(j, )
      postFixedPrioriPreemtpiveConstraint(j)
    })
  // sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex
  //     .filter((s, j) => sourceDecisionModel.schedHwModel.isFixedPriority(j))
  //     .foreach((s, j) => {
  //       model.post{new Constraint("WorkloadFP", new idesyde.exploration.explorers.DependentWorkloadFPPropagator(
  //         j,
  //         sourceDecisionModel.taskModel.priorities,
  //         sourceDecisionModel.taskModel.periods,
  //         taskExecutions,
  //         blockingTimes,
  //         responseTimes,
  //         wcets.zipWithIndex.map((ws, i) => ws(j))
  //       ))}
  //     })
  // for each SC scheduler
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((task, i) => {
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex
      .filter((s, j) => sourceDecisionModel.schedHwModel.isStaticCycle(j))
      .foreach((s, j) => {
        postStaticCyclicExecutiveConstraint(i, j)
        //val cons = Constraint(s"FPConstrats${j}", DependentWorkloadFPPropagator())
      })
  })
  // for the execution times
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((s, j) =>
      model.ifThenElse(
        taskExecution(i).eq(j).decompose,
        wcExecution(i)(j)
          .eq(sourceDecisionModel.wcets(i)(j).multiply(multiplier).doubleValue.floor.toInt + 1)
          .decompose,
        wcExecution(i)(j).eq(0).decompose
      )
    )
  )
  // for the Fetch times
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
      sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((sk, k) =>
        val tt = sourceDecisionModel.schedHwModel.hardware
          .maxTraversalTimePerBit(j)(k)
          .divide(multiplier)
          .multiply(memoryMultipler)
        model.ifThenElse(
          taskExecution(i).eq(k).and(taskMapping(i).eq(j)).decompose,
          wcFetch(i)(k)
            .ge(
              tt.multiply(sourceDecisionModel.taskModel.taskSizes(i)).doubleValue.ceil.toInt
            )
            .decompose,
          wcFetch(i)(j).eq(0).decompose
        )
      )
    })
  )
  // for the Data times
  /// channels
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
      sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
        sourceDecisionModel.taskModel.dataBlocks.zipWithIndex.foreach((c, ci) => {
          val transferred = sourceDecisionModel.taskModel.taskChannelReads(i)(ci).toInt
          val tt = sourceDecisionModel.schedHwModel.hardware
            .maxTraversalTimePerBit(j)(k)
            .divide(multiplier)
            .multiply(memoryMultipler)
            .multiply(transferred)
          model.ifThenElse(
            taskExecution(i).eq(k).and(dataBlockMapping(i).eq(j)).decompose,
            channelFetchTime(ci)(k)
              .ge(
                tt.doubleValue.ceil.toInt * transferred
              )
              .decompose,
            channelFetchTime(ci)(k).eq(0).decompose
          )
        })
      )
    })
  )
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
      model.ifThenElse(
        taskExecution(i).eq(k).decompose,
        wcInput(i)(k)
          .ge(
            model.sum(
              s"input_task${i}",
              sourceDecisionModel.taskModel.dataBlocks.zipWithIndex
                .filter((c, j) => sourceDecisionModel.taskModel.taskChannelReads(i).contains(j))
                .map((c, j) => {
                  channelFetchTime(j)(k)
                }): _*
            )
          )
          .decompose,
        wcInput(i)(k).eq(0).decompose
      )
    )
  )
  // for the Write back times
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
      sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
        sourceDecisionModel.taskModel.dataBlocks.zipWithIndex.foreach((c, ci) => {
          val transferred = sourceDecisionModel.taskModel.taskChannelWrites(i)(ci).toInt
          val tt = sourceDecisionModel.schedHwModel.hardware
            .maxTraversalTimePerBit(k)(j)
            .divide(multiplier)
            .multiply(memoryMultipler)
            .multiply(transferred)
          model.ifThenElse(
            taskExecution(i).eq(k).and(dataBlockMapping(i).eq(j)).decompose,
            channelWriteTime(ci)(k)
              .ge(
                tt.doubleValue.ceil.toInt * transferred
              )
              .decompose,
            channelWriteTime(ci)(k).eq(0).decompose
          )
        })
      )
    })
  )
  sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
      model.ifThenElse(
        taskExecution(i).eq(k).decompose,
        wcOutput(i)(k)
          .ge(
            model.sum(
              s"output_task${i}",
              sourceDecisionModel.taskModel.dataBlocks.zipWithIndex
                .filter((c, j) => sourceDecisionModel.taskModel.taskChannelWrites(i).contains(j))
                .map((c, j) => {
                  channelWriteTime(j)(k)
                }): _*
            )
          )
          .decompose,
        wcOutput(i)(k).eq(0).decompose
      )
    )
  )

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

  // create the methods that each mixing requires
  def sufficientRMSchedulingPoints(taskIdx: Int): Array[BigFraction] =
    sourceDecisionModel.sufficientRMSchedulingPoints(taskIdx)

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
        .ge(
          wcet(taskIdx)(schedulerIdx)
            .add(blockingTimes(taskIdx))
            .add(
              model
                .sum(
                  s"sc_interference${taskIdx}_${schedulerIdx}",
                  wcet.zipWithIndex
                    .filter((ws, k) => k != taskIdx)
                    .filterNot((ws, k) =>
                      // leave tasks k which i occasionally block
                      sourceDecisionModel.taskModel.interTaskAlwaysBlocks(taskIdx)(k)
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
    SimpleWorkloadBalancingDecisionStrategy(
      (0 until sourceDecisionModel.schedHwModel.allocatedSchedulers.length).toArray,
      periods.map(_.doubleValue.ceil.toInt),
      taskExecution,
      wcet
    ),
    Search.intVarSearch(
      FirstFail(model),
      IntDomainMin(),
      DecisionOperatorFactory.makeIntEq,
      (responseTimes ++ wcFetch.flatten ++ blockingTimes ++
        wcInput.flatten ++ wcOutput.flatten ++ channelFetchTime.flatten ++
        wcet.flatten ++ taskMapping ++ dataBlockMapping ++ channelWriteTime.flatten
        :+ nFreePEs): _*
    )
  )

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val rebuilt = ForSyDeSystemGraph()
    sourceDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) => {
      rebuilt.addVertex(t.getViewedVertex)
      val analysed         = AnalysedTask.enforce(t)
      val responseTimeFrac = BigFraction(responseTimes(i).getValue, multiplier).reduce
      val blockingTimeFrac = BigFraction(blockingTimes(i).getValue, multiplier).reduce
      scribe.debug(s"task ${t.getIdentifier} RT: (raw ${responseTimes(i).getValue}) ${responseTimeFrac.doubleValue}")
      scribe.debug(s"task ${t.getIdentifier} BT: (raw ${blockingTimes(i).getValue}) ${blockingTimeFrac.doubleValue}")
      analysed.setWorstCaseResponseTimeNumeratorInSecs(responseTimeFrac.getNumeratorAsLong)
      analysed.setWorstCaseResponseTimeDenominatorInSecs(responseTimeFrac.getDenominatorAsLong)
      analysed.setWorstCaseBlockingTimeNumeratorInSecs(blockingTimeFrac.getNumeratorAsLong)
      analysed.setWorstCaseBlockingTimeDenominatorInSecs(blockingTimeFrac.getDenominatorAsLong)

    })
    sourceDecisionModel.taskModel.dataBlocks.foreach(t => rebuilt.addVertex(t.getViewedVertex))
    sourceDecisionModel.schedHwModel.allocatedSchedulers.foreach(s =>
      rebuilt.addVertex(s.getViewedVertex)
    )
    sourceDecisionModel.schedHwModel.hardware.storageElems.foreach(m =>
      rebuilt.addVertex(m.getViewedVertex)
    )
    sourceDecisionModel.schedHwModel.hardware.processingElems.zipWithIndex.foreach((m, j) =>
      rebuilt.addVertex(m.getViewedVertex)
      val module = AnalysedGenericProcessingModule.enforce(m)
      module.setUtilization(
        wcet.zipWithIndex
          .filter((ws, i) => taskExecution(i).getValue == j)
          .map((ws, i) =>
            scribe.debug(s"task n ${i} Wcet: (raw ${wcet(i)(j)})")
            BigFraction(ws(j).getValue, multiplier)
              .divide(sourceDecisionModel.taskModel.periods(i))
              .doubleValue
          )
          .sum
      )
    )
    taskExecution.zipWithIndex.foreach((exe, i) => {
      val j         = output.getIntVal(exe)
      val task      = sourceDecisionModel.taskModel.tasks(i)
      val scheduler = sourceDecisionModel.schedHwModel.allocatedSchedulers(j)
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
    dataBlockMapping.zipWithIndex.foreach((mapping, i) => {
      val j       = output.getIntVal(mapping)
      val channel = sourceDecisionModel.taskModel.dataBlocks(i)
      val memory  = sourceDecisionModel.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(channel)
      rebuilt.connect(channel, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      GreyBox.enforce(memory)
      rebuilt.connect(memory, channel, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    rebuilt
  }

  def allMemorySizeNumbers() =
    (sourceDecisionModel.schedHwModel.hardware.storageElems.map(_.getSpaceInBits.toLong) ++
      sourceDecisionModel.taskModel.channelSizes ++
      sourceDecisionModel.taskModel.taskSizes).filter(_ > 0L)

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

end PeriodicTaskToSchedHWChoco
