package idesyde.identification.rules

import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.models.ReactorMinusApplication
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.VertexTrait
import java.util.stream.Collectors
import scala.jdk.StreamConverters.*
import collection.JavaConverters.*

import org.jgrapht.alg.shortestpath.AllDirectedPaths
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.prototypes.ReactorActor
import forsyde.io.java.typed.prototypes.ReactorTimer
import forsyde.io.java.typed.prototypes.Signal
import org.apache.commons.math3.fraction.Fraction

final case class ReactorMinusIdentificationRule()
    extends IdentificationRule[ReactorMinusApplication] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) = {
    val reactors =
      model.vertexSet.stream
        .toScala(LazyList)
        .filter(ReactorActor.conforms(_))
        .map(v => v.asInstanceOf[ReactorActor])
        .toSet
    val timers =
      model.vertexSet.stream
        .toScala(LazyList)
        .filter(ReactorTimer.conforms(_))
        .map(v => v.asInstanceOf[ReactorTimer])
        .toSet
    val isReactorMinus =
      reactors.forall(r =>
        timers.exists(t =>
          !AllDirectedPaths(model).getAllPaths(t, r, true, null).isEmpty
        )
      )
    // the model is indeed Reactor-, so proceed to build it
    if (isReactorMinus) {
      val periodicReactors = reactors.filter(r => 
        model.incomingEdgesOf(e).stream().map(e => e.source)
      )
      val dateReactiveReactors = reactors.filter(!periodicReactors.contains(_))
      // check if at every data chain has at least one periodic reactor
      val signalTuples =
        for (
          r1 <- reactors;
          r2 <- reactors;
          paths <- AllDirectedPaths(model).getAllPaths(r1, r2, true, 3).asScala
        ) yield ((r1, r2), paths.getVertexList.get(1).asInstanceOf[Signal])
      val signals = signalTuples.toMap
      val periods = reactors
        .map(r => r // TODO: continue jhere
          // (
          //   t -> (
          //     t.getPeriodNumeratorPerSec() / 
          //     t.getPeriodDenominatorPerSec()
          //     ).toDouble
          // )
        )
        .toMap
      val decisionModel = ReactorMinusApplication(
        timers,
        periodicReactors,
        dateReactiveReactors,
        signals,
        periods,
        reactors
          .map(r => r -> 0)//V.getMaxMemorySizeInBytes(r).orElse(0).toInt)
          .toMap,
        signals.values
          .map(s =>
            s -> 0 //(V.getMaxElemSizeBytes(s).orElse(0) * V.getMaxElemCount(s).orElse(0)).toInt
          )
          .toMap
      )
      (true, Option(decisionModel))
    } else (false, Option.empty)
    (false, Option.empty)
  }

  def calculatePeriod(model: ForSyDeModel , v: Vertex): Fraction = v match {
    case v: ReactorTimer => Fraction(v.getOffsetNumeratorPerSec, v.getOffsetDenominatorPerSec)
    case _ => Fraction(0)
  }

}
