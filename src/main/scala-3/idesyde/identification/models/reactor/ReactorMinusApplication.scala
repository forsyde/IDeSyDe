package idesyde.identification.models.reactor

import forsyde.io.java.typed.viewers.{LinguaFrancaReaction, LinguaFrancaReactor, LinguaFrancaSignal}
import idesyde.identification.DecisionModel
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.ArithmeticUtils
import org.jgrapht.graph.SimpleDirectedGraph

import collection.JavaConverters.*
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import org.jgrapht.alg.shortestpath.AllDirectedPaths
import org.jgrapht.graph.DirectedPseudograph
import org.jgrapht.graph.DefaultDirectedGraph
import org.jgrapht.alg.shortestpath.DijkstraManyToManyShortestPaths
import java.util.stream.Collectors

/** */
final case class ReactorMinusApplication(
    val pureReactions: Set[LinguaFrancaReaction],
    val periodicReactions: Set[LinguaFrancaReaction],
    val reactors: Set[LinguaFrancaReactor],
    val channels: Map[(LinguaFrancaReaction, LinguaFrancaReaction), LinguaFrancaSignal],
    val containmentFunction: Map[LinguaFrancaReaction, LinguaFrancaReactor],
    val priorityRelation: Set[(LinguaFrancaReaction, LinguaFrancaReaction)],
    val periodFunction: Map[LinguaFrancaReaction, BigFraction],
    val sizeFunction: Map[LinguaFrancaReaction | LinguaFrancaReactor | LinguaFrancaSignal, Long]
) extends SimpleDirectedGraph[LinguaFrancaReaction, LinguaFrancaSignal](classOf[LinguaFrancaSignal])
    with DecisionModel:

  for (r               <- pureReactions) addVertex(r)
  for (r               <- periodicReactions) addVertex(r)
  for (((r1, r2) -> c) <- channels) addEdge(r1, r2, c)

  val reactions: Set[LinguaFrancaReaction] = vertexSet.asScala.toSet

  val hyperPeriod: BigFraction = periodFunction.values.reduce((frac1, frac2) =>
    // the LCM of a nunch of BigFractions n1/d1, n2/d2... is lcm(n1, n2,...)/gcd(d1, d2,...). You can check.
    BigFraction(
      ArithmeticUtils.lcm(frac1.getNumerator.longValue, frac2.getNumerator.longValue),
      ArithmeticUtils.gcd(frac1.getDenominator.longValue, frac2.getDenominator.longValue)
    )
  )

  val coveredVertexes = {
    for (v <- reactions) yield v.getViewedVertex
    for (v <- reactors) yield v.getViewedVertex
    // for (a <- reactor; t <- a.get)
    for ((_, c) <- channels) yield c.getViewedVertex
  }

  override def dominates(o: DecisionModel) =
    super.dominates(o) && (o match {
      case o: ReactorMinusApplication => dominatesReactorMinus(o)
      case _                          => true
    })

  def dominatesReactorMinus(o: ReactorMinusApplication): Boolean = {
    // has more information about sizes
    o.sizeFunction.keySet.subsetOf(sizeFunction.keySet) && (
      sizeFunction.count((k, v) => v > 0) > o.sizeFunction.count((k, v) => v > 0)
    )
  }

  /** Get all trivial source and destination of trigger chains.
    * @return
    *   all paths between unambigous sinks and sources in the model. that is, those which have
    *   absolutely no incoming edges or outgoing edges.
    */
  lazy val unambigousEndToEndReactions: Set[(LinguaFrancaReaction, LinguaFrancaReaction)] =
    val reactionOnlyGraph = SimpleDirectedGraph[LinguaFrancaReaction, DefaultEdge](classOf[DefaultEdge])
    for (r <- vertexSet.asScala) reactionOnlyGraph.addVertex(r)
    for (
      r <- vertexSet.asScala; rr <- vertexSet.asScala; 
      if r != rr;
      if containsEdge(
        r,
        rr
      ) || (containmentFunction(r) == containmentFunction(rr))
          ) reactionOnlyGraph.addEdge(r, rr)
    val consensedReactionGraph = GabowStrongConnectivityInspector(reactionOnlyGraph).getCondensation
    val sources = consensedReactionGraph.vertexSet.stream
      .filter(g => consensedReactionGraph.incomingEdgesOf(g).isEmpty)
      .flatMap(g => g.vertexSet.stream)
      .collect(Collectors.toSet)
    val sinks = consensedReactionGraph.vertexSet.stream
      .filter(g => consensedReactionGraph.outgoingEdgesOf(g).isEmpty)
      .flatMap(g => g.vertexSet.stream)
      .collect(Collectors.toSet)
    val paths = DijkstraManyToManyShortestPaths(reactionOnlyGraph)
      .getManyToManyPaths(sources, sinks)
    val mutableSet = for (
      src <- sources.asScala;
      dst <- sinks.asScala;
      if paths.getPath(src, dst) != null
    ) yield (src, dst)
    mutableSet.toSet
    // paths.map(p => p.getVertexList.asScala.toSeq)
    //   .distinctBy(l => (l.head, l.last))
    //   .toSet

  override val uniqueIdentifier = "ReactorMinusApplication"

end ReactorMinusApplication
