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
    val channels = vertexes
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
    given Set[LinguaFrancaSignal]   = channels
    given Set[LinguaFrancaTimer]    = timers
    if (conformsToReactorMinus(model)) {
      (
        true,
        Option(
          ReactorMinusApplication(
            pureReactions = filterOnlyPure(model),
            periodicReactions = filterOnlyPeriodic(model),
            reactors = reactors,
            channels = channelsAsReactionConnections(model),
            containmentFunction = deriveContainmentFunction(model),
            priorityRelation = computePriorityRelation(model),
            periodFunction = computePeriodFunction(model),
            sizeFunction = computeSizesFunction(model)
          )
        )
      )
    } else {
      (true, Option.empty)
    }
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
    *
    * @param model
    *   The input model to check
    * @return
    *   whether all reactions are either periodic or pure.
    */
  def onlyPeriodicOrPure(model: ForSyDeModel)(using
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean = 
    filterOnlyPeriodic(model).intersect(filterOnlyPure(model)).isEmpty

  def filterOnlyPeriodic(model: ForSyDeModel)(using
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Set[LinguaFrancaReaction] =
    reactions.filter(r => timers.count(t => model.hasConnection(t, r)) == 1 && !reactions
      .exists(r2 => model.hasConnection(r2, r)))

  def filterOnlyPure(model: ForSyDeModel)(using
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Set[LinguaFrancaReaction] =
    reactions.filter(r => (timers.count(t => model.hasConnection(t, r)) == 0))

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
  ): Boolean = reactions.forall(r => reactors.exists(a => a.getReactionsPort(model).contains(r)))

  def computeSizesFunction(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor],
      channels: Set[LinguaFrancaSignal],
      reactions: Set[LinguaFrancaReaction]
  ): Map[LinguaFrancaReactor | LinguaFrancaSignal | LinguaFrancaReaction, Long] = {
    val elemSet: Set[LinguaFrancaReactor | LinguaFrancaSignal | LinguaFrancaReaction] =
      (reactors ++ channels ++ reactions)
    elemSet
      .map(e =>
        e -> (e match {
          case s: LinguaFrancaSignal   => s.getSizeInBits
          case a: LinguaFrancaReactor  => a.getStateSizesInBits.asScala.map(_.toLong).sum
          case r: LinguaFrancaReaction => r.getSizeInBits
          case _                       => 0L
        }).asInstanceOf[Long]
      )
      .toMap
  }

  def computePriorityRelation(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor],
      reactions: Set[LinguaFrancaReaction]
  ): Set[(LinguaFrancaReaction, LinguaFrancaReaction)] = {
    Set.empty
  }

  def computePeriodFunction(model: ForSyDeModel)(using
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Map[LinguaFrancaReaction, Fraction] =
    timers
      .flatMap(t =>
        reactions
          .filter(r => model.hasConnection(t, r))
          .map(r => (r, t))
      )
      .map((r, t) =>
        r -> Fraction(
          t.getPeriodNumeratorPerSec,
          t.getPeriodDenominatorPerSec
        )
      )
      .toMap

  def channelsAsReactionConnections(model: ForSyDeModel)(using
      reactions: Set[LinguaFrancaReaction],
      channels: Set[LinguaFrancaSignal]
  ): Map[(LinguaFrancaReaction, LinguaFrancaReaction), LinguaFrancaSignal] =
    channels
      .map(c =>
        {
          val src = reactions.find(model.hasConnection(_, c)).get
          val dst = reactions.find(model.hasConnection(c, _)).get
          (src, dst)
        } -> c
      )
      .toMap

  def deriveContainmentFunction(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor],
      reactions: Set[LinguaFrancaReaction]
  ): Map[LinguaFrancaReaction, LinguaFrancaReactor] =
    reactions.map(r => r -> 
      reactors.find(model.hasConnection(_, r)).get
    ).toMap
}
