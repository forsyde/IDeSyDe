package idesyde.identification.models.workload

import math.Numeric.Implicits.infixNumericOps
import math.Integral.Implicits.infixIntegralOps
import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import scala.collection.mutable.Queue
import java.util.stream.Collectors

import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.{ArithmeticUtils, MathUtils}
import org.jgrapht.graph.builder.GraphBuilder
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph
import org.jgrapht.alg.util.Pair
import org.jgrapht.opt.graph.sparse.IncomingEdgesSupport
import org.jgrapht.traverse.BreadthFirstIterator
import forsyde.io.java.typed.viewers.execution.DownsampleReactiveStimulus
import org.jgrapht.traverse.TopologicalOrderIterator
import com.google.common.math.IntMath

import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.execution.PeriodicTask

import forsyde.io.java.typed.viewers.impl.Executable
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.execution.Stimulus
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.execution.ReactiveTask
import forsyde.io.java.typed.viewers.execution.ReactiveStimulus
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.execution.ConstrainedTask
import forsyde.io.java.typed.viewers.execution.UpsampleReactiveStimulus
import idesyde.identification.DecisionModel

import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import forsyde.io.java.typed.viewers.execution.SimpleReactiveStimulus
import forsyde.io.java.typed.viewers.impl.DataBlock
import java.{util => ju}
import org.jgrapht.Graph

/** Simplest periodic task set concerned in the literature. The periods, offsets and relative
  * deadlines are all fixed at a task level. The only additional complexity are precedence are
  * possible precedence constraints between instances of each task.
  *
  * The event horizon for analysis takes into consideration offsets and comes from Baruah,S.,L.
  * Rosier,andR. Howell:1990b, `Algorithms and Complexity Concerning thePreemptive Schedulingof
  * PeriodicReal-Time Tasks on One Processor'
  *
  * @param periodicTasks
  *   @param periods
  * @param offsets
  *   @param relativeDeadlines
  * @param precendences
  */
case class SimplePeriodicWorkload(
    val periodicTasks: Array[PeriodicTask],
    val reactiveTasks: Array[ReactiveTask],
    val periodicStimulus: Array[PeriodicStimulus],
    val reactiveStimulus: Array[ReactiveStimulus],
    val executables: Array[Array[Executable]],
    val dataBlocks: Array[DataBlock],
    val reactiveStimulusSrcs: Array[Array[Int]],
    val reactiveStimulusDst: Array[Int],
    val taskChannelReads: Array[Array[Long]],
    val taskChannelWrites: Array[Array[Long]]
)(using Numeric[BigFraction])
    extends DecisionModel:
  //extends PeriodicWorkload[Task, BigFraction]():

  override val coveredVertexes: Iterable[Vertex] =
    periodicTasks.map(_.getViewedVertex()) ++
      periodicStimulus.map(_.getViewedVertex) ++
      executables.flatten.map(_.getViewedVertex) ++
      dataBlocks.map(_.getViewedVertex) ++
      reactiveStimulus.map(_.getViewedVertex)

  val tasks = periodicTasks ++ reactiveTasks

  val hyperPeriod: BigFraction = periodicStimulus
    .map(s => BigFraction(s.getPeriodNumerator, s.getPeriodDenominator))
    .reduce((frac1, frac2) =>
      // the LCM of a nunch of BigFractions n1/d1, n2/d2... is lcm(n1, n2,...)/gcd(d1, d2,...). You can check.
      BigFraction(
        ArithmeticUtils.lcm(frac1.getNumerator.longValue, frac2.getNumerator.longValue),
        ArithmeticUtils.gcd(frac1.getDenominator.longValue, frac2.getDenominator.longValue)
      )
    )

  // build the graph of reactions to enable periodic reductions
  // scribe.debug(reactiveStimulusSrcs.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
  // scribe.debug(reactiveStimulusDst.mkString("[", ",", "]"))
  val reactiveGraph: Graph[Integer, Integer] =
    if (!reactiveStimulusSrcs.isEmpty && !reactiveStimulusDst.isEmpty) then
      SparseIntDirectedGraph(
        tasks.length,
        reactiveStimulusSrcs.zipWithIndex
          .flatMap((r, i) => r.map(s => (s, i)))
          .map((s, i) =>
            Pair(
              s.asInstanceOf[Integer],
              reactiveStimulusDst(i).asInstanceOf[Integer]
            )
          )
          .toList
          .asJava,
        IncomingEdgesSupport.LAZY_INCOMING_EDGES
      )
    else
      SimpleDirectedGraph.createBuilder[Integer, Integer](() => 0.asInstanceOf[Integer])
        .addVertices((0 until tasks.length).map(_.asInstanceOf[Integer]).toArray:_*)
        .build
      // SparseIntDirectedGraph(
      //   tasks.length,
      //   ju.List.of()
      // )

  // do the computation by traversing the graph
  val periods = {
    var periodsMut = tasks.map(_ => hyperPeriod)
    val iter = TopologicalOrderIterator(
      reactiveGraph
    )
    while (iter.hasNext) {
      val idxTask = iter.next
      val curTask = tasks(idxTask)
      curTask match {
        case perTask: PeriodicTask =>
          val stimulus = periodicStimulus(periodicTasks.indexOf(perTask))
          periodsMut(idxTask) =
            BigFraction(stimulus.getPeriodNumerator, stimulus.getPeriodDenominator)
        case reactiveTask: ReactiveTask =>
          val stimulus = reactiveStimulus(reactiveStimulusDst.indexOf(idxTask))
          periodsMut(idxTask) = reactiveGraph
            .incomingEdgesOf(idxTask)
            .stream
            .map(reactiveGraph.getEdgeSource(_))
            .map(inTaskIdx => {
              DownsampleReactiveStimulus
                .safeCast(stimulus)
                .map(downsample =>
                  periodsMut(inTaskIdx).multiply(downsample.getRepetitivePredecessorSkips)
                )
                .or(() =>
                  UpsampleReactiveStimulus
                    .safeCast(stimulus)
                    .map(upsample =>
                      periodsMut(inTaskIdx).divide(upsample.getRepetitivePredecessorHolds)
                    )
                )
                .orElse(periodsMut(inTaskIdx))
            })
            .min((f1, f2) => f1.compareTo(f2))
            .get
      }
    }
    periodsMut
  }

  val noPrecedenceOffsets = {
    var offsetsMut = tasks.map(_ => hyperPeriod)
    val iter = TopologicalOrderIterator(
      reactiveGraph
    )
    while (iter.hasNext) {
      val idxTask = iter.next
      val curTask = tasks(idxTask)
      curTask match {
        case perTask: PeriodicTask =>
          val stimulus = periodicStimulus(periodicTasks.indexOf(perTask))
          offsetsMut(idxTask) =
            BigFraction(stimulus.getOffsetNumerator, stimulus.getOffsetDenominator)
        case reactiveTask: ReactiveTask =>
          val stimulus = reactiveStimulus(reactiveStimulusDst.indexOf(idxTask))
          offsetsMut(idxTask) = reactiveGraph
            .incomingEdgesOf(idxTask)
            .stream
            .map(reactiveGraph.getEdgeSource(_))
            .map(inTaskIdx => {
              DownsampleReactiveStimulus
                .safeCast(stimulus)
                .map(downsample =>
                  offsetsMut(inTaskIdx).add(
                    periods(inTaskIdx).multiply(downsample.getInitialPredecessorSkips)
                  )
                )
                .or(() =>
                  UpsampleReactiveStimulus
                    .safeCast(stimulus)
                    .map(upsample =>
                      offsetsMut(inTaskIdx)
                        .add(periods(inTaskIdx).divide(upsample.getInitialPredecessorHolds))
                    )
                )
                .orElse(BigFraction.ZERO)
            })
            .min((f1, f2) => f1.compareTo(f2))
            .get
      }
    }
    offsetsMut
  }

  // scribe.debug(reactiveStimulusSrcs.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
  // scribe.debug(reactiveStimulusDst.mkString("[", ",", "]"))
//  scribe.debug(periods.mkString("[", ",", "]"))
//  scribe.debug(noPrecedenceOffsets.mkString("[", ",", "]"))

  val tasksNumInstancesInHyperPeriod: Array[Int] =
    tasks.zipWithIndex.map((task, i) => hyperPeriod.divide(periods(i)).getNumeratorAsInt)
  //scribe.debug(tasksNumInstancesInHyperPeriod.mkString("[", ",", "]"))

  // val maximalOffsetDislocations: Array[Array[BigFraction]] =
  //   tasks.zipWithIndex.map((src, i) => {
  //     tasks.zipWithIndex.map((dst, j) => {
  //       reactiveStimulus.zipWithIndex
  //         .filter((stimulus, k) => {
  //           reactiveStimulusSrcs(k).contains(i) &&
  //           reactiveStimulusDst(k) == j
  //         })
  //         .map((stimulus, _) => {
  //           DownsampleReactiveStimulus
  //             .safeCast(stimulus)
  //             .map(downsample => {
  //               for (
  //                 n <- 0 until tasksNumInstancesInHyperPeriod(i);
  //                 m = n * downsample.getRepetitivePredecessorSkips + downsample.getInitialPredecessorSkips
  //               ) yield (m.toInt, n)
  //             })
  //             .or(() => {
  //               UpsampleReactiveStimulus
  //                 .safeCast(stimulus)
  //                 .map(upsample => {
  //                   for (
  //                     m <- 0 until tasksNumInstancesInHyperPeriod(j);
  //                     n = m * upsample.getRepetitivePredecessorHolds + upsample.getInitialPredecessorHolds
  //                   ) yield (m, n.toInt)
  //                 })
  //             })
  //             .orElseGet(() =>
  //               for (
  //                   m <- 0 until tasksNumInstancesInHyperPeriod(j)
  //               ) yield (m, m)
  //             )
  //         })
  //     })
  //   })

  // scribe.debug(
  //   precedences.map(row =>
  //     row.map(preds =>
  //         preds.map(_.toString).mkString("", ",", "") + s": ${preds.length}"
  //       ).mkString("[", ",", "]")
  //     ).mkString("[", "\n", "]")
  //   )

  val offsets = {
    var offsetsMut = noPrecedenceOffsets.clone
    val iter = TopologicalOrderIterator(
      reactiveGraph
    )
    while (iter.hasNext) {
      val idxTask = iter.next
      val curTask = tasks(idxTask)
      offsetsMut(idxTask) = reactiveGraph
        .incomingEdgesOf(idxTask)
        .stream
        .map(reactiveGraph.getEdgeSource(_))
        .map(inTaskIdx => {
          //scribe.debug(s"checking tasks ${inTaskIdx} to ${idxTask}")
          reactiveStimulus.zipWithIndex
            .filter((stimulus, k) => {
              reactiveStimulusSrcs(k).contains(inTaskIdx) &&
              reactiveStimulusDst(k) == idxTask
            })
            .map((stimulus, _) => {
              DownsampleReactiveStimulus
                .safeCast(stimulus)
                .map(downsample => {
                  val offsetDelta = offsetsMut(idxTask) - offsetsMut(inTaskIdx)
                  val periodDelta = periods(idxTask) - periods(inTaskIdx).multiply(
                    downsample.getRepetitivePredecessorSkips
                  )
                  maximumOffsetDislocation(tasksNumInstancesInHyperPeriod(inTaskIdx))(
                    offsetDelta,
                    periodDelta
                  )
                })
                .or(() => {
                  UpsampleReactiveStimulus
                    .safeCast(stimulus)
                    .map(upsample => {
                      val offsetDelta = offsetsMut(idxTask) - offsetsMut(inTaskIdx)
                      val periodDelta = periods(idxTask)
                        .multiply(upsample.getRepetitivePredecessorHolds) - periods(inTaskIdx)
                      maximumOffsetDislocation(tasksNumInstancesInHyperPeriod(idxTask))(
                        offsetDelta,
                        periodDelta
                      )
                    })
                })
                .orElseGet(() =>
                  val offsetDelta = offsetsMut(idxTask) - offsetsMut(inTaskIdx)
                  val periodDelta = periods(idxTask) - periods(inTaskIdx)
                  maximumOffsetDislocation(tasksNumInstancesInHyperPeriod(idxTask))(
                    offsetDelta,
                    periodDelta
                  )
                )
            }).max((f1, f2) => 
              f1.compareTo(f2)
            )
        })
        .filter(f => 
          f.compareTo(noPrecedenceOffsets(idxTask)) >= 0
        )
        .max((f1, f2) => f1.compareTo(f2))
        .orElse(noPrecedenceOffsets(idxTask))
    }
    offsetsMut
  }

  // scribe.debug(offsets.mkString("[", ",", "]"))
  // scribe.debug(periods.mkString("[", ",", "]"))

  //val periods = periodicStimulus.map(s => BigFraction(s.getPeriodNumerator, s.getPeriodDenominator))
  //val offsets = periodicStimulus.map(s => BigFraction(s.getOffsetNumerator, s.getOffsetDenominator))
  val relativeDeadlines =
    tasks.zipWithIndex.map((task, i) =>
      ConstrainedTask
        .safeCast(task)
        .map(conTask =>
          BigFraction(conTask.getRelativeDeadlineNumerator, conTask.getRelativeDeadlineDenominator)
        )
        .map(per => if (per.compareTo(periods(i)) < 0) then per else periods(i))
        .orElse(periods(i))
        .add(noPrecedenceOffsets(i))
        .subtract(offsets(i))
    )

  val largestOffset: BigFraction = offsets.max

  val eventHorizon: BigFraction =
    if (!largestOffset.equals(BigFraction.ZERO)) then hyperPeriod.multiply(2).add(largestOffset)
    else hyperPeriod

  val tasksNumInstances: Array[Int] =
    tasks.zipWithIndex.map((task, i) => eventHorizon.divide(periods(i)).getNumeratorAsInt)
  //scribe.debug(tasksNumInstances.mkString("[", ",", "]"))

  lazy val taskSizes = tasks.zipWithIndex.map((task, i) => {
    executables(i)
      .filter(e => InstrumentedExecutable.conforms(e))
      .map(e => InstrumentedExecutable.enforce(e).getSizeInBits.toLong)
      .sum
  })
  
  lazy val channelSizes = dataBlocks.map(_.getMaxSizeInBits.toLong)

  lazy val alwaysBlocksGraph = {
    val g = AsSubgraph(reactiveGraph)
    val occasionalEdges = g.edgeSet.stream
      .filter(e => {
        val i = g.getEdgeSource(e)
        val j = g.getEdgeTarget(e)
        // there is at least one instance without a follow-up
        reactiveStimulus.zipWithIndex
          .filter((stimulus, k) => {
            reactiveStimulusSrcs(k).contains(i) &&
            reactiveStimulusDst(k) == j
          })
          .exists((stimulus, _) => {
            DownsampleReactiveStimulus.conforms(stimulus)
            || UpsampleReactiveStimulus.conforms(stimulus)
          })
      })
      .collect(Collectors.toSet)
    g.removeAllEdges(occasionalEdges)
    g
  }

  lazy val (interTaskCanBlock, interTaskAlwaysBlocks) = {
    var canBlockMatrix    = Array.fill(tasks.length)(Array.fill(tasks.length)(false))
    var alwaysBlockMatrix = Array.fill(tasks.length)(Array.fill(tasks.length)(false))
    val canBlockPaths     = DijkstraManyToManyShortestPaths(reactiveGraph)
    val alwaysBlocksPaths = DijkstraManyToManyShortestPaths(alwaysBlocksGraph)
    tasks.zipWithIndex.foreach((ti, i) => {
      tasks.zipWithIndex.foreach((tj, j) => {
        if (i != j && canBlockPaths.getPath(i, j) != null) then canBlockMatrix(i)(j) = true
        if (i != j && alwaysBlocksPaths.getPath(i, j) != null) then alwaysBlockMatrix(i)(j) = true
      })
    })
    // scribe.debug(canBlockMatrix.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
    // scribe.debug(alwaysBlockMatrix.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
    (canBlockMatrix, alwaysBlockMatrix)
  }

  lazy val priorities = {
    var prioritiesMut = tasks.map(_ => tasks.length)
    val iter = TopologicalOrderIterator(
      reactiveGraph
    )
    while (iter.hasNext) {
      val idxTask = iter.next
      val curTask = tasks(idxTask)
      prioritiesMut(idxTask) = reactiveGraph
        .incomingEdgesOf(idxTask)
        .stream
        .map(reactiveGraph.getEdgeSource(_))
        .mapToInt(inTaskIdx => {
          prioritiesMut(inTaskIdx) - 1
        })
        .min
        .orElse(prioritiesMut(idxTask))
    }
    // scribe.debug(prioritiesMut.mkString("[", ",", "]"))
    prioritiesMut
  }

  def maximumOffsetDislocation(
      maxInstances: Long
  )(deltaOffset: BigFraction, deltaPeriod: BigFraction): BigFraction = {
    if (deltaPeriod.compareTo(BigFraction.ZERO) > 0) then
      deltaOffset.add(deltaPeriod.multiply(maxInstances))
    else if (
      deltaPeriod.compareTo(BigFraction.ZERO) <= 0 && deltaOffset.compareTo(BigFraction.ZERO) >= 0
    ) then
      deltaOffset
    else {
      val instance = deltaOffset.divide(deltaPeriod).negate.doubleValue.floor.toLong + 1
      deltaOffset.add(
        deltaPeriod.multiply(if (instance <= maxInstances) then instance else maxInstances)
      )
    }
  }

  override val uniqueIdentifier = "SimplePeriodicWorkload"

end SimplePeriodicWorkload
