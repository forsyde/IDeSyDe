package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait

import java.util.stream.Collectors
import scala.jdk.StreamConverters.*
import collection.JavaConverters.*
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import forsyde.io.java.core.Vertex
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.ArithmeticUtils
import org.apache.commons.math3.analysis.function.Sin
import forsyde.io.java.typed.viewers.LinguaFrancaTimer
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaElement
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.ModelOfComputation
import forsyde.io.java.core.OpaqueTrait
import idesyde.identification.models.reactor.ReactorMinusApplication
import scala.annotation.meta.companionObject

final case class ReactorMinusIdentificationRule() extends IdentificationRule {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) = {
    if (ReactorMinusIdentificationRule.canIdentify(model, identified)) {
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
      val decisionModel = ReactorMinusApplication(
        pureReactions = ReactorMinusIdentificationRule.filterOnlyPure(model, timers, reactions),
        periodicReactions =
          ReactorMinusIdentificationRule.filterOnlyPeriodic(model, timers, reactions),
        reactors = onlyNonHierarchicalReactors(model, reactors),
        channels = channelsAsReactionConnections(model, reactors, reactions, channels),
        containmentFunction = deriveContainmentFunction(model, reactors, reactions),
        priorityRelation = computePriorityRelation(model, reactors),
        periodFunction = computePeriodFunction(model, timers, reactions),
        sizeFunction = computeSizesFunction(model, reactors, channels, reactions)
      )
      scribe.debug(
        "Conforming Reactor- model found with:" +
          s"${decisionModel.pureReactions.size} pure reaction(s), " +
          s"${decisionModel.periodicReactions.size} periodic reaction(s), " +
          s"${decisionModel.reactors.size} reactor(s), " +
          s"${decisionModel.channels.size} channel(s)," +
          s"${decisionModel.unambigousEndToEndReactions.size} trivial chain(s) and " +
          s"hyperperiod of ${decisionModel.hyperPeriod}"
      )
      (
        true,
        Option(decisionModel)
      )
    } else {
      scribe.debug("No conforming Reactor- model found.")
      (true, Option.empty)
    }
  }

  def noReactionIsLoose(
      model: ForSyDeModel,
      reactors: Set[LinguaFrancaReactor],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean = reactions.forall(r => {
    reactors.exists(a => a.getReactionsPort(model).contains(r))
  })

  def computeSizesFunction(
      model: ForSyDeModel,
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

  def computePriorityRelation(
      model: ForSyDeModel,
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

  def computePeriodFunction(
      model: ForSyDeModel,
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Map[LinguaFrancaReaction, BigFraction] =
    timers
      .flatMap(t =>
        reactions
          .filter(r => model.hasConnection(t, r))
          .map(r => (r, t))
      )
      .map((r, t) =>
        r -> BigFraction(
          t.getPeriodNumeratorPerSec,
          t.getPeriodDenominatorPerSec
        )
      )
      .toMap

  /** this function build up the channels for the reactor- model, but there's a catch. The function
    * assumes (as currently does the adapter in ForSyDeIO) that the organization from reaction to
    * reaction follows the pattern:
    *
    * reaction -> [same port] reactor -> signal -> reactor -> [same port] -> reaction
    *
    * If the model to be identified does not follow this pattern, this function _has undefined
    * behavior_.
    *
    * @param model
    *   The input design model.
    * @param reactors
    *   The implicitly identified reactors
    * @param reactions
    *   The implicitly identified reactions
    * @param channels
    *   The implicitly identified channels
    */
  def channelsAsReactionConnections(
      model: ForSyDeModel,
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

  def deriveContainmentFunction(
      model: ForSyDeModel,
      reactors: Set[LinguaFrancaReactor],
      reactions: Set[LinguaFrancaReaction]
  ): Map[LinguaFrancaReaction, LinguaFrancaReactor] =
    reactions
      .map(r =>
        r ->
          reactors.find(model.hasConnection(_, r)).get
      )
      .toMap

  def onlyNonHierarchicalReactors(
      model: ForSyDeModel,
      reactors: Set[LinguaFrancaReactor]
  ): Set[LinguaFrancaReactor] =
    reactors.filter(a => a.getChildrenReactorsPort(model).isEmpty
    // ||  (a.getReactionsPort(model)
    //     .isEmpty && a.getStateNames.isEmpty && a.getStateSizesInBits.isEmpty)
    )

}

object ReactorMinusIdentificationRule:

  def canIdentify(model: ForSyDeModel, identified: Set[DecisionModel]): Boolean = {
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
    hasOnlyAcceptableTraits(model, elements) &&
    isTriviallyHierarchical(model, reactors) &&
    // noReactionIsLoose(model)
    onlyPeriodicOrPure(model, timers, reactions) &&
    allReactionsPeriodicable(model, timers, reactions)
  }

  def hasOnlyAcceptableTraits(model: ForSyDeModel, elements: Set[LinguaFrancaElement]): Boolean =
    elements.forall(v =>
      LinguaFrancaReactor.conforms(v) ||
        LinguaFrancaTimer.conforms(v) ||
        LinguaFrancaReaction.conforms(v) ||
        LinguaFrancaSignal.conforms(v)
    )

  def filterOnlyPeriodic(
      model: ForSyDeModel,
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Set[LinguaFrancaReaction] =
    reactions.filter(r =>
      timers.count(t => model.hasConnection(t, r)) == 1 && !reactions
        .exists(r2 => model.hasConnection(r2, r))
    )

  def filterOnlyPure(
      model: ForSyDeModel,
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Set[LinguaFrancaReaction] =
    reactions.filter(r => (timers.count(t => model.hasConnection(t, r)) == 0))

  /** Checks if all reactions are either periodic or pure.
    *
    * @param model
    *   The input model to check
    * @return
    *   whether all reactions are either periodic or pure.
    */
  def onlyPeriodicOrPure(
      model: ForSyDeModel,
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean =
    filterOnlyPeriodic(model, timers, reactions)
      .intersect(filterOnlyPure(model, timers, reactions))
      .isEmpty

  /** Checks if every timer can reach every reaction in the model. I.e. there is at least one simple
    * path between them.
    */
  def allReactionsPeriodicable(
      model: ForSyDeModel,
      timers: Set[LinguaFrancaTimer],
      reactions: Set[LinguaFrancaReaction]
  ): Boolean =
    val allPaths = AllDirectedPaths(model)
    reactions.forall(r =>
      timers.exists(t =>
        !allPaths
          .getAllPaths(t.getViewedVertex, r.getViewedVertex, true, null)
          .isEmpty
      )
    )

  def isTriviallyHierarchical(model: ForSyDeModel, reactors: Set[LinguaFrancaReactor]): Boolean =
    reactors.forall(a =>
      a.getChildrenReactorsPort(model).isEmpty ||
        (a.getReactionsPort(model)
          .isEmpty && a.getStateNames.isEmpty && a.getStateSizesInBits.isEmpty)
    )
end ReactorMinusIdentificationRule
