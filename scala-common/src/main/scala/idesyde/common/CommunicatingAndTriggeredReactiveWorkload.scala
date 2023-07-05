package idesyde.common

import spire.math.Rational
import scalax.collection.immutable.Graph
import scalax.collection.GraphPredef._
import scala.collection.mutable

import upickle.default._
import idesyde.core.CompleteDecisionModel

final case class CommunicatingAndTriggeredReactiveWorkload(
    val tasks: Vector[String],
    val taskSizes: Vector[Long],
    val taskComputationalNeeds: Vector[Map[String, Map[String, Long]]],
    val dataChannels: Vector[String],
    val dataChannelSizes: Vector[Long],
    val dataGraphSrc: Vector[String],
    val dataGraphDst: Vector[String],
    val dataGraphMessageSize: Vector[Long],
    val periodicSources: Vector[String],
    val periodsNumerator: Vector[Long],
    val periodsDenominator: Vector[Long],
    val offsetsNumerator: Vector[Long],
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
) extends StandardDecisionModel
    with CompleteDecisionModel
    with CommunicatingExtendedDependenciesPeriodicWorkload
    with InstrumentedWorkloadMixin
    derives ReadWriter {

  lazy val dataGraph =
    for ((s, i) <- dataGraphSrc.zipWithIndex) yield (s, dataGraphDst(i), dataGraphMessageSize(i))

  lazy val triggerGraph = triggerGraphSrc.zip(triggerGraphDst)
  val coveredElements =
    (tasks ++ upsamples ++ downsamples ++ periodicSources ++ dataChannels).toSet ++ triggerGraph.toSet
      .map(_.toString)

  lazy val stimulusGraph = Graph.from(
    tasks ++ upsamples ++ downsamples ++ periodicSources,
    triggerGraph.map((s, t) => s ~> t)
  )

  val (processes, periods, offsets, relativeDeadlines) = {
    var gen              = mutable.Buffer[(String, Double, Double, Double)]()
    var propagatedEvents = mutable.Map[String, Set[(Double, Double, Double)]]()
    for (
      topoSort <- stimulusGraph.topologicalSort(); nextInner <- topoSort; next = nextInner.value
    ) {
      // gather all incomin stimulus
      val incomingEvents = nextInner.diPredecessors
        .flatMap(pred => propagatedEvents.get(pred.value))
        .foldLeft(Set[(Double, Double, Double)]())((s1, s2) => s1 | s2)
      val events = if (periodicSources.contains(next) || hasORTriggerSemantics.contains(next)) {
        incomingEvents
      } else {
        val maxP = incomingEvents.map((p, o, d) => p).max
        val minO = incomingEvents.map((p, o, d) => o).min
        val minD = incomingEvents.map((p, o, d) => d).min
        Set((maxP, minO, minD))
      }
      // decide what to do next based on the vertex type and its event merge semantics
      if (periodicSources.contains(next)) {
        val idxSource = periodicSources.indexOf(next)
        propagatedEvents(next) = Set(
          (
            periodsNumerator(idxSource).toDouble / periodsDenominator(idxSource).toDouble, // period
            offsetsNumerator(idxSource).toDouble / offsetsDenominator(idxSource).toDouble, // offset
            periodsNumerator(idxSource).toDouble / periodsDenominator(
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
    (
      gen.map((t, p, o, d) => t).toVector,
      gen.map((t, p, o, d) => p).toVector,
      gen.map((t, p, o, d) => o).toVector,
      gen.map((t, p, o, d) => d).toVector
    )
  }

  lazy val processComputationalNeeds =
    processes.map(name => taskComputationalNeeds(tasks.indexOf(name)))

  lazy val processSizes = processes.map(name => taskSizes(tasks.indexOf(name)))

  lazy val affineControlGraph = {
    // first consider task-to-task connections
    var affineControlGraphEdges = mutable.Buffer[(Int, Int, Int, Int, Int, Int)]()
    for (
      srcTask <- tasks; dst <- stimulusGraph.get(srcTask).diSuccessors; dstTask = dst.value;
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
      src                     <- stimulusGraph.get(upsample).diPredecessors;
      dst                     <- stimulusGraph.get(upsample).diSuccessors;
      srcTask = src.value; dstTask = dst.value;
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
      src                         <- stimulusGraph.get(downsample).diPredecessors;
      dst                         <- stimulusGraph.get(downsample).diSuccessors;
      srcTask = src.value; dstTask = dst.value;
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

  def bodyAsText: String = write(this)

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  def messagesMaxSizes = dataChannelSizes

  def category = "CommunicatingAndTriggeredReactiveWorkload"
}
