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
import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.util.ArithmeticUtils
import org.apache.commons.math3.analysis.function.Sin
import forsyde.io.java.typed.viewers.ReactorActor
import forsyde.io.java.typed.viewers.ReactorTimer
import forsyde.io.java.typed.viewers.Signal

final case class ReactorMinusIdentificationRule()
    extends IdentificationRule[ReactorMinusApplication] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) = {
    val reactors =
      model.vertexSet.stream
        .toScala(LazyList)
        .filter(ReactorActor.conforms(_))
        .map(ReactorActor.safeCast(_).get)
        .toSet
    val timers =
      model.vertexSet.stream
        .toScala(LazyList)
        .filter(ReactorTimer.conforms(_))
        .map(ReactorTimer.safeCast(_).get)
        .toSet
    val isReactorMinus =
      reactors.forall(r =>
        timers.exists(t =>
          !AllDirectedPaths(model).getAllPaths(t.getViewedVertex, r.getViewedVertex, true, null).isEmpty
        )
      )
    // the model is indeed Reactor-, so proceed to build it
    if (isReactorMinus) {
      val periodicReactors = reactors.filter(r => 
        model.incomingEdgesOf(r.getViewedVertex).stream().map(_.getSource)
        .flatMap(ReactorTimer.safeCast(_).stream).anyMatch(timers.contains(_))
      )
      val dateReactiveReactors = reactors.filter(!periodicReactors.contains(_))
      // check if at every data chain has at least one periodic reactor
      // reactors.map(r1 =>
      //   reactors.map(r2 => {
      //     val paths = AllDirectedPaths(model).getAllPaths(r1, r2, true, 3).asScala;
      //     paths.foreach(p => {
      //       if (p.getLength == 2) {
      //         println(s"from ${r1.getIdentifier} to ${r2.getIdentifier}")
      //         paths.foreach(p => println(p.getVertexList))  
      //       }
      //     })
      //   })
      // )
      val signalTuples =
        for (
          r1 <- reactors;
          r2 <- reactors;
          path <- AllDirectedPaths(model).getAllPaths(r1.getViewedVertex, r2.getViewedVertex, true, 3).asScala;
          if path.getLength == 2
        ) yield ((r1, r2), Signal.safeCast(path.getVertexList.get(1)).get)
      val signals = signalTuples.toMap
      val periods = reactors
        .map(r => r -> calculatePeriod(model, r.getViewedVertex)
          // (
          //   t -> (
          //     t.getPeriodNumeratorPerSec() / 
          //     t.getPeriodDenominatorPerSec()
          //     ).toDouble
          // )
        )
        .toMap
      println(periods.map((k, v) => k.getViewedVertex.getIdentifier -> v))
      val decisionModel = ReactorMinusApplication(
        timers,
        periodicReactors,
        dateReactiveReactors,
        signals,
        periods,
        reactors
          .map(r => r -> 0)
          .toMap,
        signals.values
          .map(s => s -> 0)
          .toMap
      )
      (true, Option(decisionModel))
    } else (false, Option.empty)
  }

  def calculatePeriod(model: ForSyDeModel, v: Vertex): Fraction = {
    println(v.getIdentifier)
    if (ReactorTimer.conforms(v)) {
      val t = ReactorTimer.safeCast(v).get
      println("timer!")
      val f = Fraction(t.getOffsetNumeratorPerSec, t.getOffsetDenominatorPerSec)
      println(f)
      f
    } else if (ReactorActor.conforms(v) || Signal.conforms(v)) {
      println("not timer!")
      model
        .incomingEdgesOf(v)
        .stream()
        .map(_.getSource)
        .filter(i => ReactorTimer.conforms(i) || ReactorActor.conforms(i) || Signal.conforms(i))
        .map(calculatePeriod(model, _))
        // the GCD of a nunch of fractions n1/d1, n2/d2 ... is gcd(n1, n2,...)/lcm(d1, d2,...). You can check.
        .reduce((t1, t2) =>
          Fraction(
            ArithmeticUtils.gcd(t1.getNumerator, t2.getNumerator),
            ArithmeticUtils.lcm(t1.getDenominator, t2.getDenominator)
          )
        )
        .get
    } else Fraction(0)
  }
}
