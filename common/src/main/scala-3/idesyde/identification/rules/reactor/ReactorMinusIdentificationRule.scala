package idesyde.identification.rules.reactor

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
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
import forsyde.io.java.core.OpaqueTrait
import forsyde.io.java.typed.viewers.moc.linguafranca.{
  LinguaFrancaElem,
  LinguaFrancaReaction,
  LinguaFrancaReactor,
  LinguaFrancaSignal,
  LinguaFrancaTimer
}
import idesyde.identification.models.reactor.ReactorMinusApplication

import scala.annotation.meta.companionObject
import java.util.concurrent.ThreadPoolExecutor

final case class ReactorMinusIdentificationRule(executor: ThreadPoolExecutor)
    extends IdentificationRule {

  def identify(model: ForSyDeSystemGraph, identified: Set[DecisionModel]) = {
    val elements = model.vertexSet.asScala
      .filter(LinguaFrancaElem.conforms(_))
      .map(LinguaFrancaElem.safeCast(_).get)
      .toArray
    if (elements.length > 0 && ReactorMinusIdentificationRule.canIdentify(model, identified)) {
      val vertexes = model.vertexSet.asScala
      val reactors = elements
        .filter(LinguaFrancaReactor.conforms(_))
        .map(LinguaFrancaReactor.safeCast(_).get)
        .toArray
      val reactions = elements
        .filter(LinguaFrancaReaction.conforms(_))
        .map(LinguaFrancaReaction.safeCast(_).get)
        .toArray
      val channels = elements
        .filter(LinguaFrancaSignal.conforms(_))
        .map(LinguaFrancaSignal.safeCast(_).get)
        .toArray
      val timers = elements
        .filter(LinguaFrancaTimer.conforms(_))
        .map(LinguaFrancaTimer.safeCast(_).get)
        .toArray
      val decisionModel = ReactorMinusApplication(
        pureReactions = ReactorMinusIdentificationRule.filterOnlyPure(model, timers, reactions),
        periodicReactions =
          ReactorMinusIdentificationRule.filterOnlyPeriodic(model, timers, reactions),
        reactors = onlyNonHierarchicalReactors(model, reactors),
        channels = channelsAsReactionConnections(model, reactors, reactions, channels),
        containmentFunction = deriveContainmentFunction(model, reactors, reactions),
        reactionIndex = computeReactionIndex(model, reactors),
        periodFunction = computePeriodFunction(model, timers, reactions),
        executor = executor
        // sizeFunction = computeSizesFunction(model, reactors, channels, reactions)
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
      model: ForSyDeSystemGraph,
      reactors: Array[LinguaFrancaReactor],
      reactions: Array[LinguaFrancaReaction]
  ): Boolean = reactions.forall(r => {
    reactors.exists(a => a.getReactionsPort(model).contains(r))
  })

  // def computeSizesFunction(
  //     model: ForSyDeSystemGraph,
  //     reactors: Array[LinguaFrancaReactor],
  //     channels: Array[LinguaFrancaSignal],
  //     reactions: Array[LinguaFrancaReaction]
  // ): Map[LinguaFrancaReactor | LinguaFrancaSignal | LinguaFrancaReaction, Long] = {
  //   val elemSet: Array[LinguaFrancaReactor | LinguaFrancaSignal | LinguaFrancaReaction] =
  //     (reactors ++ channels ++ reactions)
  //   elemSet
  //     .map(e =>
  //       e -> (e match {
  //         case s: LinguaFrancaSignal   => s.getSizeInBits
  //         case a: LinguaFrancaReactor  => a.getStateSizesInBits.asScala.map(_.toLong).sum
  //         case r: LinguaFrancaReaction => r.getSizeInBits
  //       }).asInstanceOf[Long]
  //     )
  //     .toMap
  // }

  def computeReactionIndex(
      model: ForSyDeSystemGraph,
      reactors: Array[LinguaFrancaReactor]
  ): Map[LinguaFrancaReaction, Int] =
    reactors
      .flatMap(a => {
        a.getReactionsPort(model).asScala.toIndexedSeq.zipWithIndex.map((r, i) => r -> i)
      })
      .toMap

  def computePeriodFunction(
      model: ForSyDeSystemGraph,
      timers: Array[LinguaFrancaTimer],
      reactions: Array[LinguaFrancaReaction]
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
      model: ForSyDeSystemGraph,
      reactors: Array[LinguaFrancaReactor],
      reactions: Array[LinguaFrancaReaction],
      channels: Array[LinguaFrancaSignal]
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
      model: ForSyDeSystemGraph,
      reactors: Array[LinguaFrancaReactor],
      reactions: Array[LinguaFrancaReaction]
  ): Map[LinguaFrancaReaction, LinguaFrancaReactor] =
    reactions
      .map(r =>
        r ->
          reactors.find(model.hasConnection(_, r)).get
      )
      .toMap

  def onlyNonHierarchicalReactors(
      model: ForSyDeSystemGraph,
      reactors: Array[LinguaFrancaReactor]
  ): Array[LinguaFrancaReactor] =
    reactors.filter(a => a.getChildrenReactorsPort(model).isEmpty
    // ||  (a.getReactionsPort(model)
    //     .isEmpty && a.getStateNames.isEmpty && a.getStateSizesInBits.isEmpty)
    )

}

object ReactorMinusIdentificationRule:

  def canIdentify(model: ForSyDeSystemGraph, identified: Set[DecisionModel]): Boolean = {
    val vertexes = model.vertexSet.asScala
    val elements = vertexes
      .filter(LinguaFrancaElem.conforms(_))
      .map(LinguaFrancaElem.safeCast(_).get)
      .toArray
    val reactors = elements
      .filter(LinguaFrancaReactor.conforms(_))
      .map(LinguaFrancaReactor.safeCast(_).get)
      .toArray
    val reactions = elements
      .filter(LinguaFrancaReaction.conforms(_))
      .map(LinguaFrancaReaction.safeCast(_).get)
      .toArray
    val channels = elements
      .filter(LinguaFrancaSignal.conforms(_))
      .map(LinguaFrancaSignal.safeCast(_).get)
      .toArray
    val timers = elements
      .filter(LinguaFrancaTimer.conforms(_))
      .map(LinguaFrancaTimer.safeCast(_).get)
      .toArray
    hasOnlyAcceptableTraits(model, elements) &&
    isTriviallyHierarchical(model, reactors) &&
    // noReactionIsLoose(model)
    onlyPeriodicOrPure(model, timers, reactions) &&
    allReactionsPeriodicable(model, timers, reactions)
  }

  def hasOnlyAcceptableTraits(
      model: ForSyDeSystemGraph,
      elements: Array[LinguaFrancaElem]
  ): Boolean =
    elements.forall(v =>
      LinguaFrancaReactor.conforms(v) ||
        LinguaFrancaTimer.conforms(v) ||
        LinguaFrancaReaction.conforms(v) ||
        LinguaFrancaSignal.conforms(v)
    )

  def filterOnlyPeriodic(
      model: ForSyDeSystemGraph,
      timers: Array[LinguaFrancaTimer],
      reactions: Array[LinguaFrancaReaction]
  ): Array[LinguaFrancaReaction] =
    reactions.filter(r =>
      timers.count(t => model.hasConnection(t, r)) == 1 && !reactions
        .exists(r2 => model.hasConnection(r2, r))
    )

  def filterOnlyPure(
      model: ForSyDeSystemGraph,
      timers: Array[LinguaFrancaTimer],
      reactions: Array[LinguaFrancaReaction]
  ): Array[LinguaFrancaReaction] =
    reactions.filter(r => (timers.count(t => model.hasConnection(t, r)) == 0))

  /** Checks if all reactions are either periodic or pure.
    *
    * @param model
    *   The input model to check
    * @return
    *   whether all reactions are either periodic or pure.
    */
  def onlyPeriodicOrPure(
      model: ForSyDeSystemGraph,
      timers: Array[LinguaFrancaTimer],
      reactions: Array[LinguaFrancaReaction]
  ): Boolean =
    filterOnlyPeriodic(model, timers, reactions)
      .intersect(filterOnlyPure(model, timers, reactions))
      .isEmpty

  /** Checks if every timer can reach every reaction in the model. I.e. there is at least one simple
    * path between them.
    */
  def allReactionsPeriodicable(
      model: ForSyDeSystemGraph,
      timers: Array[LinguaFrancaTimer],
      reactions: Array[LinguaFrancaReaction]
  ): Boolean =
    val allPaths = AllDirectedPaths(model)
    reactions.forall(r =>
      timers.exists(t =>
        !allPaths
          .getAllPaths(t.getViewedVertex, r.getViewedVertex, true, null)
          .isEmpty
      )
    )

  def isTriviallyHierarchical(
      model: ForSyDeSystemGraph,
      reactors: Array[LinguaFrancaReactor]
  ): Boolean =
    reactors.forall(a =>
      a.getChildrenReactorsPort(model).isEmpty ||
        (a.getReactionsPort(model)
          .isEmpty && a.getStateNames.isEmpty && a.getStateSizesInBits.isEmpty)
    )
end ReactorMinusIdentificationRule
