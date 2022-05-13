package idesyde.identification.models.choco

import idesyde.identification.interfaces.ChocoCPForSyDeDecisionModel

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
    val sourceForSyDeDecisionModel: PeriodicTaskToSchedHW
) extends ChocoCPForSyDeDecisionModel
    with FixedPriorityConstraintsMixin
    with BaselineTimingConstraintsMixin:

  given Ordering[Task]       = DependentDeadlineMonotonicOrdering(sourceForSyDeDecisionModel.taskModel)
  given Numeric[BigFraction] = BigFractionIsNumeric()

  val coveredVertexes = sourceForSyDeDecisionModel.coveredVertexes

  // section for time multiplier calculation
  val timeValues = (sourceForSyDeDecisionModel.taskModel.periods ++ sourceForSyDeDecisionModel.wcets.flatten)
  var multiplier = 1L
  while (
    timeValues
      .map(_.multiply(multiplier))
      .exists(d => d.doubleValue < 1) && multiplier < Int.MaxValue
  ) {
    multiplier *= 10
  }
  //scribe.debug(multiplier.toString)

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
  val periods         = sourceForSyDeDecisionModel.taskModel.periods.map(_.multiply(multiplier))
  val priorities      = sourceForSyDeDecisionModel.taskModel.priorities
  val deadlines       = sourceForSyDeDecisionModel.taskModel.relativeDeadlines.map(_.multiply(multiplier))
  val wcets           = sourceForSyDeDecisionModel.wcets.map(a => a.map(_.multiply(multiplier)))
  val maxUtilizations = sourceForSyDeDecisionModel.maxUtilization

  // build the model so that it can be acessed later
  val model = Model()
  // the true decision variables
  val taskExecution = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
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
    model.intVar(
      "exe_" + t.getViewedVertex.getIdentifier,
      sourceForSyDeDecisionModel
        .wcets(i)
        .zipWithIndex
        .filter((p, j) => p.compareTo(BigFraction.MINUS_ONE) > 0)
        // keep only the cores which have proven schedulability tests in the execution
        .filter((p, j) =>
          sourceForSyDeDecisionModel.schedHwModel.isStaticCycle(j) || sourceForSyDeDecisionModel.schedHwModel
            .isFixedPriority(j)
        )
        .map((p, j) => j) // keep the processors where WCEt is defined
    )
  )
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
        .filter((m, j) => sourceForSyDeDecisionModel.taskModel.channelSizes(i) <= m.getSpaceInBits)
        .map((m, j) => j)
    )
  )
  // auxiliary variables
  val responseTimes =
    sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
      // scribe.debug(sourceForSyDeDecisionModel
      //     .wcets(i)
      //     .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
      //     .min
      //     .multiply(multiplier)
      //     .doubleValue
      //     .ceil
      //     .toInt.toString)
      // scribe.debug(sourceForSyDeDecisionModel.taskModel
      //     .relativeDeadlines(i)
      //     .multiply(multiplier)
      //     .doubleValue
      //     .floor
      //     .toInt.toString)
      model.intVar(
        "rt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        sourceForSyDeDecisionModel
          .wcets(i)
          .filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0)
          .min
          .multiply(multiplier)
          .doubleValue
          .ceil
          .toInt,
        deadlines(i).doubleValue.floor.toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val blockingTimes =
    sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
      // scribe.debug(
      //   s"B: ${sourceForSyDeDecisionModel.taskModel.relativeDeadlines(i).multiply(multiplier).doubleValue.floor.toInt} - " +
      //   s"W: ${sourceForSyDeDecisionModel.wcets(i).filter(p => p.compareTo(BigFraction.MINUS_ONE) > 0).min.multiply(multiplier).doubleValue.ceil.toInt}"
      // )
      model.intVar(
        "bt_" + t.getViewedVertex.getIdentifier,
        // minimum WCET possible
        0,
        deadlines(i).doubleValue.floor.toInt,
        true // keeping only bounds for the response time is enough and better
      )
    )
  val wcExecution = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        s"exe_wc_${t.getIdentifier}_${s.getIdentifier}",
        if (
          sourceForSyDeDecisionModel
            .wcets(i)(j)
            .compareTo(BigFraction.MINUS_ONE) > 0
        ) then
          Array(
            0,
            sourceForSyDeDecisionModel
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
  val wcFetch = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        "fetch_wc" + t.getViewedVertex.getIdentifier + s.getIdentifier,
        0,
        deadlines(i).doubleValue.floor.toInt,
        true
      )
    )
  )
  val wcInput = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        "input_wc" + t.getViewedVertex.getIdentifier + s.getIdentifier,
        0,
        deadlines(i).doubleValue.floor.toInt,
        true
      )
    )
  )
  val wcOutput = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
      model.intVar(
        "output_wc" + t.getViewedVertex.getIdentifier + s.getIdentifier,
        0,
        deadlines(i).doubleValue.floor.toInt,
        true
      )
    )
  )
  val durations = sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((s, j) =>
      wcExecution(i)(j).add(wcFetch(i)(j)).add(wcInput(i)(j)).add(wcOutput(i)(j)).intVar
    )
  )
  val channelFetchTime = sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.map((c, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((pj, j) =>
      val t = sourceForSyDeDecisionModel.schedHwModel.hardware.maxTraversalTimePerBit(j).max
      model.intVar(
        s"tt${i}_${j}",
        0,
        if (t.compareTo(BigFraction.MINUS_ONE) > 0) then
          t.multiply(multiplier)
            .multiply(c.getMaxSizeInBits)
            .doubleValue
            .ceil
            .toInt
        else 0,
        true
      )
    )
  )
  val channelWriteTime = sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.map((c, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((pj, j) =>
      val t = sourceForSyDeDecisionModel.schedHwModel.hardware.maxTraversalTimePerBit(j).max
      model.intVar(
        s"tt${i}_${j}",
        0,
        if (t.compareTo(BigFraction.MINUS_ONE) > 0) then
          t.multiply(multiplier)
            .multiply(c.getMaxSizeInBits)
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
  val memoryUsage = sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.map(mem =>
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
      sourceForSyDeDecisionModel.taskModel.taskSizes
        .map(_ / memoryMultipler + 1)
        .map(_.toInt) ++ sourceForSyDeDecisionModel.taskModel.channelSizes
        .map(_ / memoryMultipler + 1)
        .map(_.toInt),
      memoryUsage,
      0 // 0 offset for no minizinc
    )
    .post
  // timing constraints
  // basic utilization
  postMinimalResponseTimesByBlocking()
  postMaximumUtilizations()
  // sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((pe, j) => {
  //   val maxUtilization = sourceForSyDeDecisionModel.maxUtilization(j)
  //   val utilization = model.sum(
  //     s"cpu_${pe.getIdentifier}_load",
  //     sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.map((task, i) =>
  //       durations(i)(j).mul(100).div(periods(i).doubleValue.floor.toInt + 1).intVar
  //     ): _*
  //   )
  //   model.arithm(utilization, "<=", maxUtilization.multiply(100).doubleValue.ceil.toInt).post
  // })
  // dependent-emerging blocking
  // sourceForSyDeDecisionModel.taskModel.reactiveStimulus.zipWithIndex.foreach((s, i) => {
  //   sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex
  //     .foreach((pe, j) => {
  //       responseTimes(i).ge(blockingTimes(i).add(durations(i)(j))).post
  //     })
  // })
  sourceForSyDeDecisionModel.taskModel.reactiveStimulus.zipWithIndex.foreach((s, i) => {
    sourceForSyDeDecisionModel.taskModel
      .reactiveStimulusSrcs(i)
      .foreach(src => {
        val dst = sourceForSyDeDecisionModel.taskModel.reactiveStimulusDst(i)
        //scribe.debug(s"dst ${dst} and src ${src}")
        model.ifThen(
          taskExecution(dst).ne(taskExecution(src)).decompose,
          blockingTimes(dst).ge(responseTimes(src)).decompose
        )
      })
  })
  // for the execution times
  sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((s, j) =>
      model.ifThenElse(
        taskExecution(i).eq(j).decompose,
        wcExecution(i)(j)
          .eq(sourceForSyDeDecisionModel.wcets(i)(j).multiply(multiplier).doubleValue.floor.toInt + 1)
          .decompose,
        wcExecution(i)(j).eq(0).decompose
      )
    )
  )
  // for the Fetch times
  sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
      sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.map((sk, k) =>
        val tt = sourceForSyDeDecisionModel.schedHwModel.hardware
          .maxTraversalTimePerBit(j)(k)
          .divide(multiplier)
          .multiply(memoryMultipler)
        model.ifThenElse(
          taskExecution(i).eq(k).and(taskMapping(i).eq(j)).decompose,
          wcFetch(i)(k)
            .ge(
              tt.multiply(sourceForSyDeDecisionModel.taskModel.taskSizes(i)).doubleValue.ceil.toInt
            )
            .decompose,
          wcFetch(i)(j).eq(0).decompose
        )
      )
    })
  )
  // for the Data times
  /// channels
  sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
      sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
        sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.foreach((c, ci) => {
          val transferred = sourceForSyDeDecisionModel.taskModel.taskChannelReads(i)(ci).toInt
          val tt = sourceForSyDeDecisionModel.schedHwModel.hardware
            .maxTraversalTimePerBit(j)(k)
            .divide(multiplier)
            .multiply(memoryMultipler)
            .multiply(transferred)
          model.ifThenElse(
            taskExecution(i).eq(k).and(dataBlockMapping(ci).eq(j)).decompose,
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
  sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
      model.ifThenElse(
        taskExecution(i).eq(k).decompose,
        wcInput(i)(k)
          .ge(
            model.sum(
              s"input_task${i}",
              sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex
                .filter((c, j) => sourceForSyDeDecisionModel.taskModel.taskChannelReads(i).contains(j))
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
  sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.zipWithIndex.foreach((mj, j) => {
      sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
        sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex.foreach((c, ci) => {
          val transferred = sourceForSyDeDecisionModel.taskModel.taskChannelWrites(i)(ci).toInt
          val tt = sourceForSyDeDecisionModel.schedHwModel.hardware
            .maxTraversalTimePerBit(k)(j)
            .divide(multiplier)
            .multiply(memoryMultipler)
            .multiply(transferred)
          model.ifThenElse(
            taskExecution(i).eq(k).and(dataBlockMapping(ci).eq(j)).decompose,
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
  sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) =>
    sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers.zipWithIndex.foreach((sk, k) =>
      model.ifThenElse(
        taskExecution(i).eq(k).decompose,
        wcOutput(i)(k)
          .ge(
            model.sum(
              s"output_task${i}",
              sourceForSyDeDecisionModel.taskModel.dataBlocks.zipWithIndex
                .filter((c, j) => sourceForSyDeDecisionModel.taskModel.taskChannelWrites(i).contains(j))
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
  val nUsedPEs = model.intVar(
    "nUsedPEs",
    0,
    sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.length - 1
  )
  // count different ones
  model.atMostNValues(taskExecution, nUsedPEs, true).post
  // this flips the direction of the variables since the objective must be MAX
  val nFreePEs = model
    .intVar(sourceForSyDeDecisionModel.schedHwModel.hardware.processingElems.length)
    .sub(nUsedPEs)
    .intVar

  def chocoModel: Model = model

  override def modelObjectives = Array(nFreePEs)

  // create the methods that each mixing requires
  def sufficientRMSchedulingPoints(taskIdx: Int): Array[BigFraction] =
    sourceForSyDeDecisionModel.sufficientRMSchedulingPoints(taskIdx)

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
      taskExecution(taskIdx).eq(schedulerIdx).decompose,
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
      (responseTimes ++ wcFetch.flatten ++ blockingTimes ++
        wcInput.flatten ++ wcOutput.flatten ++ channelFetchTime.flatten ++
        durations.flatten ++ taskMapping ++ dataBlockMapping ++ channelWriteTime.flatten
        ++ utilizations
        :+ nFreePEs): _*
    )
  )

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = {
    val rebuilt = ForSyDeSystemGraph()
    sourceForSyDeDecisionModel.taskModel.tasks.zipWithIndex.foreach((t, i) => {
      rebuilt.addVertex(t.getViewedVertex)
      val analysed         = AnalysedTask.enforce(t)
      val responseTimeFrac = BigFraction(responseTimes(i).getValue, multiplier).reduce
      val blockingTimeFrac = BigFraction(blockingTimes(i).getValue, multiplier).reduce
      // scribe.debug(s"task ${t.getIdentifier} RT: (raw ${responseTimes(i).getValue}) ${responseTimeFrac.doubleValue}")
      // scribe.debug(s"task ${t.getIdentifier} BT: (raw ${blockingTimes(i).getValue}) ${blockingTimeFrac.doubleValue}")
      analysed.setWorstCaseResponseTimeNumeratorInSecs(responseTimeFrac.getNumeratorAsLong)
      analysed.setWorstCaseResponseTimeDenominatorInSecs(responseTimeFrac.getDenominatorAsLong)
      analysed.setWorstCaseBlockingTimeNumeratorInSecs(blockingTimeFrac.getNumeratorAsLong)
      analysed.setWorstCaseBlockingTimeDenominatorInSecs(blockingTimeFrac.getDenominatorAsLong)

    })
    sourceForSyDeDecisionModel.taskModel.dataBlocks.foreach(t => rebuilt.addVertex(t.getViewedVertex))
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
          .filter((ws, i) => taskExecution(i).getValue == j)
          .map((ws, i) =>
            //scribe.debug(s"task n ${i} Wcet: (raw ${durations(i)(j)})")
            BigFraction(ws(j).getValue)
              .divide(periods(i))
              .doubleValue
          )
          .sum
      )
    )
    taskExecution.zipWithIndex.foreach((exe, i) => {
      val j         = output.getIntVal(exe)
      val task      = sourceForSyDeDecisionModel.taskModel.tasks(i)
      val scheduler = sourceForSyDeDecisionModel.schedHwModel.allocatedSchedulers(j)
      Scheduled.enforce(task)
      rebuilt.connect(task, scheduler, "scheduler", EdgeTrait.DECISION_ABSTRACTSCHEDULING)
      GreyBox.enforce(scheduler)
      rebuilt.connect(scheduler, task, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    taskMapping.zipWithIndex.foreach((mapping, i) => {
      val j      = output.getIntVal(mapping)
      val task   = sourceForSyDeDecisionModel.taskModel.tasks(i)
      val memory = sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(task)
      rebuilt.connect(task, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      // GreyBox.enforce(memory)
      // rebuilt.connect(memory, task, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    dataBlockMapping.zipWithIndex.foreach((mapping, i) => {
      val j       = output.getIntVal(mapping)
      val channel = sourceForSyDeDecisionModel.taskModel.dataBlocks(i)
      val memory  = sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems(j)
      MemoryMapped.enforce(channel)
      rebuilt.connect(channel, memory, "mappingHost", EdgeTrait.DECISION_ABSTRACTMAPPING)
      GreyBox.enforce(memory)
      rebuilt.connect(memory, channel, "contained", EdgeTrait.VISUALIZATION_VISUALCONTAINMENT)
    })
    rebuilt
  }

  def allMemorySizeNumbers() =
    (sourceForSyDeDecisionModel.schedHwModel.hardware.storageElems.map(_.getSpaceInBits.toLong) ++
      sourceForSyDeDecisionModel.taskModel.channelSizes ++
      sourceForSyDeDecisionModel.taskModel.taskSizes).filter(_ > 0L)

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

end PeriodicTaskToSchedHWChoco
