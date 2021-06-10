package idesyde.identification.rules

import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.models.ReactorMinusApplication
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.VertexTrait
import forsyde.io.java.core.{VertexPropertyAcessor => V}
import java.util.stream.Collectors
import scala.jdk.StreamConverters.*
import collection.JavaConverters.*

import org.jgrapht.alg.shortestpath.AllDirectedPaths
import forsyde.io.java.core.Vertex

final case class ReactorMinusIdentificationRule()
    extends IdentificationRule[ReactorMinusApplication] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) = {
    val reactors =
      model.vertexSet.stream
        .toScala(LazyList)
        .filter(_.hasTrait(VertexTrait.ReactorActor))
        .toSet
    val timers =
      model.vertexSet.stream
        .toScala(LazyList)
        .filter(_.hasTrait(VertexTrait.ReactorTimer))
        .toSet
    val periodicTuples =
      for (r <- reactors; t <- timers; if model.containsEdge(t, r)) yield (t, r)
    val periodicReactors =
      reactors.filter(periodicTuples.map((t, r) => r).contains(_))
    val dateReactiveReactors = reactors.filter(!periodicReactors.contains(_))
    // check if at every data chain has at least one periodic reactor
    val isReactorMinus =
      reactors.forall(r1 =>
        reactors.forall(r2 =>
          AllDirectedPaths(model)
            .getAllPaths(r1, r2, true, null)
            .asScala
            .forall(path =>
              !path.getVertexList.asScala.toSet
                .intersect(periodicReactors)
                .isEmpty
            )
        )
      )
    // the model is indeed Reactor-, so proceed to build it
    if (isReactorMinus) {
      val signalTuples =
        for (
          r1 <- reactors;
          r2 <- reactors;
          paths <- AllDirectedPaths(model).getAllPaths(r1, r2, true, 3).asScala
        ) yield ((r1, r2), paths.getVertexList.get(1))
      val signals = signalTuples.toMap
      val periods = periodicTuples
        .map((t, r) =>
          (
            r ->
              (V.getPeriodNumeratorPerSec(r).orElse(0) / V
                .getPeriodDenominatorPerSec(r)
                .orElse(1)).toDouble
          )
        )
        .toMap
      val decisionModel = ReactorMinusApplication(
        timers,
        periodicReactors,
        dateReactiveReactors,
        signals,
        periods,
        reactors
          .map(r => r -> V.getMaxMemorySizeInBytes(r).orElse(0).toInt)
          .toMap,
        signals.values
          .map(s =>
            s -> (V
              .getMaxElemSizeBytes(s)
              .orElse(0) * V.getMaxElemCount(s).orElse(0)).toInt
          )
          .toMap
      )
      (true, Option(decisionModel))
    } else (false, Option.empty)
    (false, Option.empty)
  }

}
