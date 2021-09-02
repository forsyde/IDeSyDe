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
import forsyde.io.java.typed.viewers.LinguaFrancaActor
import forsyde.io.java.typed.viewers.LinguaFrancaTimer
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaElement
import forsyde.io.java.typed.viewers.LinguaFrancaReaction

final case class LinguaFrancaMinusIdentificationRule()
    extends IdentificationRule[ReactorMinusApplication] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) = {
    val vertexes = model.vertexSet.asScala
    val elements = vertexes
      .filter(LinguaFrancaElement.conforms(_))
      .map(LinguaFrancaElement.safeCast(_).get)
      .toSet
    val reactors = vertexes
      .filter(LinguaFrancaReactor.conforms(_))
      .map(LinguaFrancaReactor.safeCast(_).get)
      .toSet
    val reactions = vertexes
      .filter(LinguaFrancaReaction.conforms(_))
      .map(LinguaFrancaReaction.safeCast(_).get)
      .toSet
    val signals = vertexes
      .filter(LinguaFrancaSignal.conforms(_))
      .map(LinguaFrancaSignal.safeCast(_).get)
      .toSet
    val timers = vertexes
      .filter(LinguaFrancaTimer.conforms(_))
      .map(LinguaFrancaTimer.safeCast(_).get)
      .toSet
    given Set[LinguaFrancaElement]  = elements
    given Set[LinguaFrancaReactor]  = reactors
    given Set[LinguaFrancaReaction] = reactions
    given Set[LinguaFrancaSignal]   = signals
    given Set[LinguaFrancaTimer]    = timers
    if (conformsToReactorMinus(model)) {
      val periodFunction = timers
        .flatMap(t =>
          reactions
            .filter(r => model.containsEdge(t.getViewedVertex, r.getViewedVertex))
            .map(r => (r, t))
        )
        .map((r, t) =>
          r -> Fraction(
            t.getPeriodNumeratorPerSec,
            t.getPeriodDenominatorPerSec
          )
        )
        .toMap
      (true, Option.empty)
    } else {
      (true, Option.empty)
    }
  }

  def calculatePeriod(model: ForSyDeModel, v: Vertex): Fraction = {
    if (LinguaFrancaTimer.conforms(v)) {
      val t = LinguaFrancaTimer.safeCast(v).get
      Fraction(t.getPeriodNumeratorPerSec, t.getPeriodDenominatorPerSec)
    } else if (LinguaFrancaReactor.conforms(v) || LinguaFrancaSignal.conforms(v)) {
      model
        .incomingEdgesOf(v)
        .stream()
        .map(_.getSource)
        .filter(i =>
          LinguaFrancaTimer.conforms(i) || LinguaFrancaReactor.conforms(i)
            || LinguaFrancaSignal.conforms(i)
        )
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

  def conformsToReactorMinus(model: ForSyDeModel)(using
      elements: Set[LinguaFrancaElement],
      reactors: Set[LinguaFrancaReactor],
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean = hasOnlyAcceptableTraits(model) && isNotHierarchical(
    model
  ) && noReactionIsLoose(model)
    && onlyPeriodicOrPure(model) && allReactionsPeriodicable(model)

  def hasOnlyAcceptableTraits(model: ForSyDeModel)(using
      elements: Set[LinguaFrancaElement]
  ): Boolean =
    elements.forall(v =>
      LinguaFrancaReactor.conforms(v) ||
        LinguaFrancaTimer.conforms(v) ||
        LinguaFrancaReaction.conforms(v) ||
        LinguaFrancaSignal.conforms(v)
    )

  def isNotHierarchical(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor]
  ): Boolean =
    reactors.forall(_.getChildrenActorsPort(model).isEmpty)

  /** Checks if all reactions are either periodic or pure.
    */
  def onlyPeriodicOrPure(model: ForSyDeModel)(using
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean = reactions.forall(r =>
    (timers.count(t => model.containsEdge(t.getViewedVertex, r.getViewedVertex)) == 1 && !reactions
      .exists(r2 => model.containsEdge(r2.getViewedVertex, r.getViewedVertex)))
      || (timers.count(t => model.containsEdge(t.getViewedVertex, r.getViewedVertex)) == 0)
  )

  /** Checks if every timer can reach every reaction in the model. I.e. there is at least one simple
    * path between them.
    */
  def allReactionsPeriodicable(model: ForSyDeModel)(using
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean =
    reactions.forall(r =>
      timers.exists(t =>
        !AllDirectedPaths(model)
          .getAllPaths(t.getViewedVertex, r.getViewedVertex, true, null)
          .isEmpty
      )
    )

  def noReactionIsLoose(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean =
    reactions.forall(r => reactors.exists(a => a.getReactionsPort(model).asScala.contains(r)))

  def computeSizesFunction(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor],
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Map[LinguaFrancaReactor | LinguaFrancaTimer | LinguaFrancaReaction, Int] = {
    // val reactorSizes = reactors.map(_.getStateSizesInBits)
    Map.empty
  }
}
