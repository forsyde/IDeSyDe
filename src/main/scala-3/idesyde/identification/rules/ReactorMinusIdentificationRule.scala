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
import forsyde.io.java.typed.viewers.ModelOfComputation
import forsyde.io.java.core.OpaqueTrait

final case class ReactorMinusIdentificationRule()
    extends IdentificationRule[ReactorMinusApplication]() {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) = {
    val vertexes = model.vertexSet.asScala
    val elements = vertexes
      .filter(LinguaFrancaElement.conforms(_))
      .map(LinguaFrancaElement.safeCast(_).get)
      .toSet
    val reactors = elements
      .filter(LinguaFrancaReactor.conforms(_))
      .map(LinguaFrancaReactor.safeCast(_).get)
      .toSet
    val reactions = elements
      .filter(LinguaFrancaReaction.conforms(_))
      .map(LinguaFrancaReaction.safeCast(_).get)
      .toSet
    val channels = elements
      .filter(LinguaFrancaSignal.conforms(_))
      .map(LinguaFrancaSignal.safeCast(_).get)
      .toSet
    val timers = elements
      .filter(LinguaFrancaTimer.conforms(_))
      .map(LinguaFrancaTimer.safeCast(_).get)
      .toSet
    given Set[LinguaFrancaElement]  = elements
    given Set[LinguaFrancaReactor]  = reactors
    given Set[LinguaFrancaReaction] = reactions
    given Set[LinguaFrancaSignal]   = channels
    given Set[LinguaFrancaTimer]    = timers
    if (conformsToReactorMinus(model)) {
      scribe.debug("Conforming Reactor- model found.")
      (
        true,
        Option(
          ReactorMinusApplication(
            pureReactions = filterOnlyPure(model),
            periodicReactions = filterOnlyPeriodic(model),
            reactors = onlyNonHierarchicalReactors(model),
            channels = channelsAsReactionConnections(model),
            containmentFunction = deriveContainmentFunction(model),
            priorityRelation = computePriorityRelation(model),
            periodFunction = computePeriodFunction(model),
            sizeFunction = computeSizesFunction(model)
          )
        )
      )
    } else {
      scribe.debug("No conforming Reactor- model found.")
      (true, Option.empty)
    }
  }

  def conformsToReactorMinus(model: ForSyDeModel)(using
      elements: Set[LinguaFrancaElement],
      reactors: Set[LinguaFrancaReactor],
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean = {
    val traits    = hasOnlyAcceptableTraits(model)
    val hierarchy = isTriviallyHierarchical(model)
    // val wellformed  = noReactionIsLoose(model)
    val isSimple    = onlyPeriodicOrPure(model)
    val allPeriodic = allReactionsPeriodicable(model)
    scribe.debug(s"Model has only acceptable traits: $traits")
    scribe.debug(s"Model has no hierarchy-: $hierarchy")
    // scribe.debug(s"Model has no loose reactions: $wellformed")
    scribe.debug(s"Model has only periodic or pure reactions: $isSimple")
    scribe.debug(s"Model has only periodicable reactions: $allPeriodic")
    traits &&
    hierarchy &&
    // wellformed &&
    isSimple &&
    allPeriodic
  }

  def hasOnlyAcceptableTraits(model: ForSyDeModel)(using
      elements: Set[LinguaFrancaElement]
  ): Boolean =
    elements.forall(v =>
      LinguaFrancaReactor.conforms(v) ||
        LinguaFrancaTimer.conforms(v) ||
        LinguaFrancaReaction.conforms(v) ||
        LinguaFrancaSignal.conforms(v)
    )

  def isTriviallyHierarchical(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor]
  ): Boolean =
    reactors.forall(a =>
      a.getChildrenReactorsPort(model).isEmpty ||
        (a.getReactionsPort(model)
          .isEmpty && a.getStateNames.isEmpty && a.getStateSizesInBits.isEmpty)
    )

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
    reactions.filter(r =>
      timers.count(t => model.hasConnection(t, r)) == 1 && !reactions
        .exists(r2 => model.hasConnection(r2, r))
    )

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
  ): Boolean = reactions.forall(r => {
    reactors.exists(a => a.getReactionsPort(model).contains(r))
  })

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
        }).asInstanceOf[Long]
      )
      .toMap
  }

  def computePriorityRelation(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor]
  ): Set[(LinguaFrancaReaction, LinguaFrancaReaction)] =
    reactors
      .flatMap(a => {
        val orderedReactions = a.getReactionsPort(model).asScala.toArray
        for (
          i <- Seq.range(0, orderedReactions.size - 1);
          j <- Seq.range(i + 1, orderedReactions.size)
        ) yield (orderedReactions(i), orderedReactions(j))
      })
      .toSet

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

  /**
   * this function build up the channels for the reactor- model, but there's
   * a catch. The function assumes (as currently does the adapter in ForSyDeIO)
   * that the organization from reaction to reaction follows the pattern:
   * 
   * reaction -> [same port] reactor -> signal -> reactor -> [same port] -> reaction
   * 
   * If the model to be identified does not follow this pattern, this function
   * _has undefined behavior_.
   * 
   * @param model The input design model.
   * @param reactors The implicitly identified reactors
   * @param reactions The implicitly identified reactions
   * @param channels The implicitly identified channels
   */
  def channelsAsReactionConnections(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor],
      reactions: Set[LinguaFrancaReaction],
      channels: Set[LinguaFrancaSignal]
  ): Map[(LinguaFrancaReaction, LinguaFrancaReaction), LinguaFrancaSignal] =
    channels
      .flatMap(c =>
        for (
          srcReactor  <- reactors; dstReactor <- reactors;
          srcEdge     <- model.getAllEdges(srcReactor.getViewedVertex, c.getViewedVertex).asScala;
          dstEdge     <- model.getAllEdges(c.getViewedVertex, dstReactor.getViewedVertex).asScala;
          srcReaction <- srcReactor.getReactionsPort(model).asScala;
          dstReaction <- dstReactor.getReactionsPort(model).asScala;
          if model
            .hasConnection(srcReaction, srcReactor, srcEdge.sourcePort.get, srcEdge.sourcePort.get);
          if model
            .hasConnection(dstReactor, dstReaction, dstEdge.targetPort.get, dstEdge.targetPort.get)
        ) yield (srcReaction, dstReaction) -> c
      )
      .toMap

  def deriveContainmentFunction(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor],
      reactions: Set[LinguaFrancaReaction]
  ): Map[LinguaFrancaReaction, LinguaFrancaReactor] =
    reactions
      .map(r =>
        r ->
          reactors.find(model.hasConnection(_, r)).get
      )
      .toMap

  def onlyNonHierarchicalReactors(model: ForSyDeModel)(using
      reactors: Set[LinguaFrancaReactor]
  ): Set[LinguaFrancaReactor] =
    reactors.filterNot(a =>
      a.getChildrenReactorsPort(model).isEmpty ||
        (a.getReactionsPort(model)
          .isEmpty && a.getStateNames.isEmpty && a.getStateSizesInBits.isEmpty)
    )

}