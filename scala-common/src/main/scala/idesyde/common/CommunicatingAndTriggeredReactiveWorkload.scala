package idesyde.common

import spire.math.Rational
// import scalax.collection.immutable.Graph
// import scalax.collection.GraphPredef._
import scala.collection.mutable

import upickle.default._
import idesyde.core.DecisionModel
import java.{util => ju}

import scala.jdk.CollectionConverters._
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.traverse.TopologicalOrderIterator

final case class CommunicatingAndTriggeredReactiveWorkload(
    val tasks: Vector[String],
    val task_sizes: Vector[Long],
    val task_computational_needs: Vector[Map[String, Map[String, Long]]],
    val data_channels: Vector[String],
    val data_channel_sizes: Vector[Long],
    val data_graph_src: Vector[String],
    val data_graph_dst: Vector[String],
    val data_graph_message_size: Vector[Long],
    val periodic_sources: Vector[String],
    val periods_numerator: Vector[Long],
    val periods_denominator: Vector[Long],
    val offsets_numerator: Vector[Long],
    val offsetsDenominator: Vector[Long],
    val upsamples: Vector[String],
    val upsampleRepetitiveHolds: Vector[Long],
    val upsampleInitialHolds: Vector[Long],
    val downsamples: Vector[String],
    val downampleRepetitiveSkips: Vector[Long],
    val downampleInitialSkips: Vector[Long],
    val triggerGraphSrc: Vector[String],
    val triggerGraphDst: Vector[String],
    val hasORTriggerSemantics: Set[String]
) extends DecisionModel
    with CommunicatingExtendedDependenciesPeriodicWorkload
    with InstrumentedWorkloadMixin
    derives ReadWriter {

  lazy val dataGraph =
    for ((s, i) <- data_graph_src.zipWithIndex)
      yield (s, data_graph_dst(i), data_graph_message_size(i))

  lazy val triggerGraph = triggerGraphSrc.zip(triggerGraphDst)

  lazy val stimulusGraph = {
    val g = DefaultDirectedGraph[String, DefaultEdge](classOf[DefaultEdge])
    for (v      <- tasks ++ upsamples ++ downsamples ++ periodic_sources) g.addVertex(v)
    for ((s, t) <- triggerGraph) g.addEdge(s, t)
    // Graph.from(
    //   tasks ++ upsamples ++ downsamples ++ periodicSources,
    //   triggerGraph.map((s, t) => s ~> t)
    // )
    g
  }

  val (processes, periods, offsets, relative_deadlines) = {
    var gen              = mutable.Buffer[(String, Double, Double, Double)]()
    var propagatedEvents = mutable.Map[String, Set[(Double, Double, Double)]]()
    val topoSort         = TopologicalOrderIterator(stimulusGraph)
    while (topoSort.hasNext()) {
      val next = topoSort.next()
      // gather all incomin stimulus
      val incomingEvents = stimulusGraph
        .incomingEdgesOf(next)
        .asScala
        .map(stimulusGraph.getEdgeSource)
        .flatMap(pred => propagatedEvents.get(pred))
        .foldLeft(Set[(Double, Double, Double)]())((s1, s2) => s1 | s2)
      val events = if (periodic_sources.contains(next) || hasORTriggerSemantics.contains(next)) {
        incomingEvents
      } else {
        val maxP = incomingEvents.map((p, o, d) => p).max
        val minO = incomingEvents.map((p, o, d) => o).min
        val minD = incomingEvents.map((p, o, d) => d).min
        Set((maxP, minO, minD))
      }
      // decide what to do next based on the vertex type and its event merge semantics
      if (periodic_sources.contains(next)) {
        val idxSource = periodic_sources.indexOf(next)
        propagatedEvents(next) = Set(
          (
            periods_numerator(idxSource).toDouble / periods_denominator(
              idxSource
            ).toDouble, // period
            offsets_numerator(idxSource).toDouble / offsetsDenominator(
              idxSource
            ).toDouble, // offset
            periods_numerator(idxSource).toDouble / periods_denominator(
              idxSource
            ).toDouble // rel. deadline
          )
        )
      } else if (tasks.contains(next)) {
        propagatedEvents(next) = events
        gen ++= events.map((p, o, d) => (next, p, o, d))
      } else if (upsamples.contains(next)) {
        val idxUpsample = upsamples.indexOf(next)
        propagatedEvents(next) = events.map(e => {
          (
            e._1 / upsampleRepetitiveHolds(idxUpsample).toDouble,
            e._2 + (e._1 / upsampleInitialHolds(idxUpsample).toDouble),
            e._3 / upsampleRepetitiveHolds(idxUpsample).toDouble
          )
        })
      } else if (downsamples.contains(next)) {
        val idxDownsample = downsamples.indexOf(next)
        propagatedEvents(next) = events.map(e => {
          (
            e._1 * downampleRepetitiveSkips(idxDownsample).toDouble,
            e._2 + (e._1 * (downampleInitialSkips(idxDownsample).toDouble)),
            e._3 * (downampleRepetitiveSkips(idxDownsample).toDouble)
          )
        })
      }
    }
    // for (
    //   topoSort <- stimulusGraph.topologicalSort(); nextInner <- topoSort; next = nextInner.value
    // ) {}
    (
      gen.map((t, p, o, d) => t).toVector,
      gen.map((t, p, o, d) => p).toVector,
      gen.map((t, p, o, d) => o).toVector,
      gen.map((t, p, o, d) => d).toVector
    )
  }

  lazy val processComputationalNeeds =
    processes.map(name => task_computational_needs(tasks.indexOf(name)))

  lazy val processSizes = processes.map(name => task_sizes(tasks.indexOf(name)))

  lazy val affineControlGraph = {
    // first consider task-to-task connections
    var affineControlGraphEdges = mutable.Buffer[(Int, Int, Int, Int, Int, Int)]()
    for (
      srcTask <- tasks; dst <- stimulusGraph.outgoingEdgesOf(srcTask).asScala;
      dstTask = stimulusGraph
        .getEdgeTarget(dst);
      if tasks.contains(dstTask)
    ) {
      if (hasORTriggerSemantics.contains(dstTask)) {
        for (
          (srcEvent, i) <- processes.zipWithIndex
            .filter((p, i) => p == srcTask);
          (dstEvent, j) <- processes.zipWithIndex
            .filter((p, j) => p == dstTask);
          if periods(i) == periods(j)
        ) {
          affineControlGraphEdges :+= (i, j, 1, 0, 1, 0)
        }
      } else {
        for (
          (srcEvent, i) <- processes.zipWithIndex
            .filter((p, i) => p == srcTask);
          (dstEvent, j) <- processes.zipWithIndex
            .filter((p, j) => p == dstTask)
        ) {
          affineControlGraphEdges :+= (i, j, (periods(j) / periods(i)).ceil.toInt, 0, 1, 0)
        }
      }
    }
    // now consider upsampling connections
    for (
      (upsample, idxUpsample) <- upsamples.zipWithIndex;
      src                     <- stimulusGraph.incomingEdgesOf(upsample).asScala;
      dst                     <- stimulusGraph.outgoingEdgesOf(upsample).asScala;
      srcTask = stimulusGraph.getEdgeSource(src); dstTask = stimulusGraph.getEdgeTarget(dst);
      if tasks.contains(srcTask) && tasks.contains(dstTask)
    ) {
      if (hasORTriggerSemantics.contains(dstTask)) {
        for (
          (srcEvent, i) <- processes.zipWithIndex
            .filter((p, i) => p == srcTask);
          (dstEvent, j) <- processes.zipWithIndex
            .filter((p, j) => p == dstTask)
          if periods(j) * Rational(
            upsampleRepetitiveHolds(idxUpsample)
          ) == periods(i) &&
            offsets(j) - (periods(j) * Rational(
              upsampleInitialHolds(idxUpsample)
            )) == offsets(i)
        ) {
          affineControlGraphEdges :+= (i, j, upsampleRepetitiveHolds(
            idxUpsample
          ).toInt, upsampleInitialHolds(idxUpsample).toInt, 1, 0)
        }
      } else {
        for (
          (srcEvent, i) <- processes.zipWithIndex
            .filter((p, i) => p == srcTask);
          (dstEvent, j) <- processes.zipWithIndex
            .filter((p, j) => p == dstTask);
          pRatio = (periods(j) / periods(i));
          offset = ((offsets(j) - offsets(i)) / periods(i))
        ) {
          affineControlGraphEdges :+= (i, j, pRatio.ceil.toInt, offset.ceil.toInt, 1, 0)
        }
      }

    }
    // now finally consider downsample connections
    for (
      (downsample, idxDownsample) <- downsamples.zipWithIndex;
      src                         <- stimulusGraph.incomingEdgesOf(downsample).asScala;
      dst                         <- stimulusGraph.outgoingEdgesOf(downsample).asScala;
      srcTask = stimulusGraph.getEdgeSource(src); dstTask = stimulusGraph.getEdgeTarget(dst);
      if tasks.contains(srcTask) && tasks.contains(dstTask)
    ) {
      if (hasORTriggerSemantics.contains(dstTask)) {
        for (
          (srcEvent, i) <- processes.zipWithIndex
            .filter((p, i) => p == srcTask);
          (dstEvent, j) <- processes.zipWithIndex
            .filter((p, j) => p == dstTask)
          if periods(j) / Rational(
            downampleRepetitiveSkips(idxDownsample)
          ) == periods(i) &&
            offsets(j) + (periods(j) / Rational(
              downampleInitialSkips(idxDownsample)
            )) == offsets(i)
        )
          affineControlGraphEdges :+= (
            i,
            j,
            1,
            0,
            downampleRepetitiveSkips(idxDownsample).toInt,
            downampleInitialSkips(idxDownsample).toInt
          )
      } else {
        for (
          (srcEvent, i) <- processes.zipWithIndex
            .filter((p, i) => p == srcTask);
          (dstEvent, j) <- processes.zipWithIndex
            .filter((p, j) => p == dstTask);
          pRatio = (periods(i) / periods(j)).ceil.toInt;
          offset = ((offsets(j) - offsets(i)) / periods(j)).toDouble.toInt
        ) affineControlGraphEdges :+= (i, j, 1, 0, pRatio, offset)
      }
    }
    affineControlGraphEdges.toSet
  }

  override def asJsonString(): java.util.Optional[String] = try {
    java.util.Optional.of(write(this))
  } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try {
    java.util.Optional.of(writeBinary(this))
  } catch { case _ => java.util.Optional.empty() }

  def messagesMaxSizes = data_channel_sizes

  override def category() = "CommunicatingAndTriggeredReactiveWorkload"

  override def part(): ju.Set[String] =
    ((tasks ++ upsamples ++ downsamples ++ periodic_sources ++ data_channels).toSet ++ triggerGraph.toSet
      .map(_.toString)).asJava
}
