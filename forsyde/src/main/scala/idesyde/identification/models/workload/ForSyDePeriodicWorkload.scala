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
import org.jgrapht.traverse.TopologicalOrderIterator

import forsyde.io.java.core.Vertex

import forsyde.io.java.typed.viewers.impl.Executable
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.execution.ConstrainedTask
import idesyde.identification.ForSyDeDecisionModel

import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import forsyde.io.java.typed.viewers.impl.DataBlock
import java.{util => ju}
import org.jgrapht.Graph
import forsyde.io.java.typed.viewers.execution.Downsample
import forsyde.io.java.typed.viewers.execution.Upsample
import scala.collection.mutable
import forsyde.io.java.typed.viewers.execution.Stimulatable
import scala.collection.mutable.Buffer
import idesyde.utils.MultipliableFractional
import com.ibm.icu.impl.locale.LocaleDistance.Data
import forsyde.io.java.typed.viewers.execution.CommunicatingTask

/** Simplest periodic task set concerned in the literature. The task graph is generated from the
  * execution namespace in ForSyDe IO, which defines triggering mechanisms and how they propagate.
  *
  * The event horizon for analysis takes into consideration offsets and comes from Baruah,S.,L.
  * Rosier,andR. Howell:1990b, `Algorithms and Complexity Concerning thePreemptive Schedulingof
  * PeriodicReal-Time Tasks on One Processor'
  *
  * Assumptions that must be guaranteed:
  *   1. The periodic stimulus reaches any tasks in the model.
  */
case class ForSyDePeriodicWorkload(
    val tasks: Array[Task],
    val periodicStimulus: Array[PeriodicStimulus],
    val dataBlocks: Array[DataBlock],
    val executables: Array[Array[Executable]],
    val stimulusGraph: Graph[Task | PeriodicStimulus | Upsample | Downsample, DefaultEdge],
    val taskCommunicationGraph: Graph[CommunicatingTask | DataBlock, Long]
)(using MultipliableFractional[BigFraction])
    extends ForSyDeDecisionModel
    with PeriodicWorkloadMixin[BigFraction]:
  //extends PeriodicWorkload[Task, BigFraction]():

  override val coveredVertexes: Iterable[Vertex] =
    tasks.map(_.getViewedVertex()) ++
      executables.flatten.map(_.getViewedVertex) ++
      periodicStimulus.map(_.getViewedVertex) ++
      dataBlocks.map(_.getViewedVertex)

  def computeLCM(frac1: BigFraction, frac2: BigFraction) = BigFraction(
    ArithmeticUtils.lcm(frac1.getNumerator.longValue, frac2.getNumerator.longValue),
    ArithmeticUtils.gcd(frac1.getDenominator.longValue, frac2.getDenominator.longValue)
  )

  // build the graph of reactions to enable periodic reductions
  // scribe.debug(reactiveStimulusSrcs.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
  // scribe.debug(reactiveStimulusDst.mkString("[", ",", "]"))
  // val reactiveGraph: Graph[Integer, Integer] =
  //   if (!reactiveStimulusSrcs.isEmpty && !reactiveStimulusDst.isEmpty) then
  //     SparseIntDirectedGraph(
  //       tasks.length,
  //       reactiveStimulusSrcs.zipWithIndex
  //         .flatMap((r, i) => r.map(s => (s, i)))
  //         .map((s, i) =>
  //           Pair(
  //             s.asInstanceOf[Integer],
  //             reactiveStimulusDst(i).asInstanceOf[Integer]
  //           )
  //         )
  //         .toList
  //         .asJava,
  //       IncomingEdgesSupport.LAZY_INCOMING_EDGES
  //     )
  //   else
  //     SimpleDirectedGraph
  //       .createBuilder[Integer, Integer](() => 0.asInstanceOf[Integer])
  //       .addVertices((0 until tasks.length).map(_.asInstanceOf[Integer]).toArray: _*)
  //       .build
  // SparseIntDirectedGraph(
  //   tasks.length,
  //   ju.List.of()
  // )
  // val periods = tasks.map(t =>
  //   stimulusGraph.incomingEdgesOf(t).stream.map(e => stimulusGraph.getEdgeSource(e))
  //   .(src => src match {
  //     case per: PeriodicStimulus =>
  //       BigFraction(per.getPeriodNumerator, per.getPeriodDenominator)
  //     case _ => BigFraction.MINUS_ONE
  //   })
  //   )

  def taskComputationNeeds: Array[Map[String, Map[String, Long]]] =
    executables.map(execs =>
      execs
        .flatMap(e => InstrumentedExecutable.safeCast(e).toScala)
        .map(ie => ie.getOperationRequirements)
        .reduce((m1, m2) =>
          m2.forEach((group, groupMap) =>
            m1.merge(
              group,
              groupMap,
              (childMap1, childMap2) => {
                childMap2.forEach((k, v) => childMap1.merge(k, v, (v1, v2) => v1 + v2))
                childMap1
              }
            )
          )
          m1
        )
        .asScala
        .toMap
        .map((k, v) => k -> v.asScala.map((kk, vv) => kk -> vv.toLong).toMap)
    )

  // compute the affine relations
  var createdPerTasks = Buffer[(Task, BigFraction, BigFraction, BigFraction)]()
  val stimulusGraphTuples =
    mutable.Map.empty[Task | PeriodicStimulus | Upsample | Downsample, Array[
      (BigFraction, BigFraction, BigFraction)
    ]]
  val stimulusIter = TopologicalOrderIterator(stimulusGraph)
  while (stimulusIter.hasNext) {
    val next = stimulusIter.next
    // gather all incomin stimulus
    val incomingEvents = stimulusGraph
      .incomingEdgesOf(next)
      .stream
      .flatMap(e => stimulusGraphTuples(stimulusGraph.getEdgeSource(e)).asJavaSeqStream)
      .toScala(Array)
    // decide what to do next based on the vertex type and its event merge semantics
    stimulusGraphTuples(next) = Stimulatable
      .safeCast(next)
      .filter(s => !s.getHasORSemantics)
      .map(s => {
        Array(incomingEvents.maxBy(e => e._1))
      })
      .orElse(incomingEvents)
    // change the stimulus in the vertex depending on traits it has
    next match {
      case perStim: PeriodicStimulus =>
        stimulusGraphTuples(next) = Array(
          (
            BigFraction(perStim.getPeriodNumerator, perStim.getPeriodDenominator), // period
            BigFraction(perStim.getOffsetNumerator, perStim.getOffsetDenominator), // offset
            BigFraction(perStim.getPeriodNumerator, perStim.getPeriodDenominator)  // rel. deadline
          )
        )
      case task: Task =>
        val stimulus = stimulusGraphTuples(next)
        // do not forget to check if the periodic task has some sort
        // of tighter deadline
        createdPerTasks = createdPerTasks.appendAll(
          stimulus.map(s =>
            ConstrainedTask
              .safeCast(task)
              .map(t =>
                val deadline =
                  BigFraction(t.getRelativeDeadlineNumerator, t.getRelativeDeadlineDenominator)
                (
                  task,
                  s._1,
                  s._2,
                  if (deadline.compareTo(s._3) >= 0) then s._3 else deadline
                )
              )
              .orElse((task, s._1, s._2, s._3))
          )
        )
      case upsample: Upsample =>
        stimulusGraphTuples(next) = stimulusGraphTuples(next).map(e => {
          (
            e._1.divide(upsample.getRepetitivePredecessorHolds),
            e._2.add(e._1.divide(upsample.getInitialPredecessorHolds)),
            e._3
          )
        })
      case downsample: Downsample =>
        stimulusGraphTuples(next) = stimulusGraphTuples(next).map(e => {
          (
            e._1.multiply(downsample.getRepetitivePredecessorSkips),
            e._2.add(e._1.multiply(downsample.getInitialPredecessorSkips)),
            e._3
          )
        })
    }
  }
  // put the created tasks in the Mixin methods
  val periods           = createdPerTasks.map(_._2).toArray
  val offsets           = createdPerTasks.map(_._3).toArray
  val relativeDeadlines = createdPerTasks.map(_._4).toArray
  val numTasks          = createdPerTasks.length

  // now we create the precedence constraints via affine relations
  val affineStimulusGraphBuilder =
    SimpleDirectedGraph.createBuilder[Task, (Int, Int, Int, Int)](() => (1, 0, 1, 0))
  val affineStimulusGraph = affineStimulusGraphBuilder.buildAsUnmodifiable

  val affineRelationsGraph = {
    val affineRelationsGraphBuilder =
      SimpleDirectedGraph.createBuilder[Int, (Int, Int, Int, Int)](() => (1, 0, 1, 0))
    // first consider task-to-task connections
    stimulusGraph.vertexSet.stream
      .flatMap(v => Task.safeCast(v).stream)
      .forEach(dstTask => {
        stimulusGraph
          .incomingEdgesOf(dstTask)
          .stream
          .flatMap(e => Task.safeCast(stimulusGraph.getEdgeSource(e)).stream)
          .forEach(srcTask => {
            if (dstTask.getHasORSemantics)
              for (
                (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
                (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
                if dst._2 == src._2 && dst._3 == src._3
              ) affineRelationsGraphBuilder.addEdge(i, j, (1, 0, 1, 0))
            else
              for (
                (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
                (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
                pRatio = dst._2.divide(src._2).toDouble.ceil.toInt
              ) affineRelationsGraphBuilder.addEdge(i, j, (pRatio, 0, 1, 0))
          })
      })
    // now consider upsampling connections
    stimulusGraph.vertexSet.stream
      .flatMap(v => Upsample.safeCast(v).stream)
      .forEach(upsample => {
        stimulusGraph
          .incomingEdgesOf(upsample)
          .stream
          .flatMap(e => Task.safeCast(stimulusGraph.getEdgeSource(e)).stream)
          .forEach(srcTask => {
            stimulusGraph
              .outgoingEdgesOf(upsample)
              .stream
              .flatMap(e => Task.safeCast(stimulusGraph.getEdgeTarget(e)).stream)
              .forEach(dstTask => {
                if (dstTask.getHasORSemantics)
                  for (
                    (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
                    (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
                    if dst._2.multiply(upsample.getRepetitivePredecessorHolds) == src._2 &&
                      dst._3
                        .subtract(dst._2.multiply(upsample.getInitialPredecessorHolds)) == src._3
                  )
                    affineRelationsGraphBuilder.addEdge(
                      i,
                      j,
                      (
                        upsample.getRepetitivePredecessorHolds.toInt,
                        upsample.getInitialPredecessorHolds.toInt,
                        1,
                        0
                      )
                    )
                else
                  for (
                    (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
                    (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
                    pRatio = dst._2.divide(src._2).toDouble.ceil.toInt;
                    offset = dst._3.subtract(src._3).divide(src._2).toDouble.toInt
                  ) affineRelationsGraphBuilder.addEdge(i, j, (pRatio, offset, 1, 0))
              })
          })
      })
    // now finally consider downsample connections
    stimulusGraph.vertexSet.stream
      .flatMap(v => Downsample.safeCast(v).stream)
      .forEach(downsample => {
        stimulusGraph
          .incomingEdgesOf(downsample)
          .stream
          .flatMap(e => Task.safeCast(stimulusGraph.getEdgeSource(e)).stream)
          .forEach(srcTask => {
            stimulusGraph
              .outgoingEdgesOf(downsample)
              .stream
              .flatMap(e => Task.safeCast(stimulusGraph.getEdgeTarget(e)).stream)
              .forEach(dstTask => {
                if (dstTask.getHasORSemantics)
                  for (
                    (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
                    (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
                    if dst._2.divide(downsample.getRepetitivePredecessorSkips) == src._2 &&
                      dst._3
                        .add(dst._2.divide(downsample.getInitialPredecessorSkips)) == src._3
                  )
                    affineRelationsGraphBuilder.addEdge(
                      i,
                      j,
                      (
                        1,
                        0,
                        downsample.getRepetitivePredecessorSkips.toInt,
                        downsample.getInitialPredecessorSkips.toInt
                      )
                    )
                else
                  for (
                    (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
                    (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
                    pRatio = src._2.divide(dst._2).toDouble.ceil.toInt;
                    offset = dst._3.subtract(src._3).divide(dst._2).toDouble.toInt
                  ) affineRelationsGraphBuilder.addEdge(i, j, (1, 0, pRatio, offset))
              })
          })
      })
    affineRelationsGraphBuilder.buildAsUnmodifiable
  }

  // scribe.debug(reactiveStimulusSrcs.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
  // scribe.debug(reactiveStimulusDst.mkString("[", ",", "]"))
  // scribe.debug(periods.mkString("[", ",", "]"))
  // scribe.debug(noPrecedenceOffsets.mkString("[", ",", "]"))
  // scribe.debug(tasksNumInstancesInHyperPeriod.mkString("[", ",", "]"))

  // scribe.debug(
  //   precedences.map(row =>
  //     row.map(preds =>
  //         preds.map(_.toString).mkString("", ",", "") + s": ${preds.length}"
  //       ).mkString("[", ",", "]")
  //     ).mkString("[", "\n", "]")
  //   )

  // scribe.debug(offsets.mkString("[", ",", "]"))
  // scribe.debug(periods.mkString("[", ",", "]"))

  // scribe.debug(tasksNumInstances.mkString("[", ",", "]"))

  lazy val taskSizes = tasks.zipWithIndex.map((task, i) => {
    executables(i)
      .filter(e => InstrumentedExecutable.conforms(e))
      .map(e => InstrumentedExecutable.enforce(e).getSizeInBits.toLong)
      .sum
  })

  val numMessageQeues    = dataBlocks.length
  val messageQueuesSizes = dataBlocks.map(_.getMaxSizeInBits.toLong)

  lazy val communicationGraph = {
    val g = SimpleDirectedGraph.createBuilder[Int, Long](() => 0L)
    taskCommunicationGraph.edgeSet.forEach(l => {
      val src = taskCommunicationGraph.getEdgeSource(l)
      val dst = taskCommunicationGraph.getEdgeTarget(l)
      (src, dst) match {
        case (task, data): (Task, DataBlock) =>
          g.addEdge(tasks.indexOf(task), tasks.length + dataBlocks.indexOf(data), l)
        case (data, task): (DataBlock, Task) =>
          g.addEdge(tasks.length + dataBlocks.indexOf(data), tasks.indexOf(task), l)
        case _ =>
      }
    })
    g.buildAsUnmodifiable
  }

  override val uniqueIdentifier = "SimplePeriodicWorkload"

end ForSyDePeriodicWorkload
