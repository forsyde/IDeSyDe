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
import org.jgrapht.traverse.BreadthFirstIterator
import java.util.concurrent.ThreadPoolExecutor

// sealed class ReactionsPartialOrder(
//     val containmentFunction: Map[LinguaFrancaReaction, LinguaFrancaReactor],
//     val reactionIndex: Set[(LinguaFrancaReaction, LinguaFrancaReaction)]
// ) extends PartialOrdering[LinguaFrancaReaction] {

//   inline def lteq(x: LinguaFrancaReaction, y: LinguaFrancaReaction): Boolean =
//     containmentFunction(x) == containmentFunction(y) && reactionIndex.contains((x, y))

//   inline def tryCompare(x: LinguaFrancaReaction, y: LinguaFrancaReaction): Option[Int] =
//     if containmentFunction(x) == containmentFunction(y) then
//       if reactionIndex.contains((x, y)) then Option(-1)
//       else if reactionIndex.contains((y, x)) then Option(1)
//       else Option(0)
//     else Option.empty
// }

/**
 * This is a subset of the Reactor MoC in order to unambiguously calculate a finite event time-horizon,
 * the hyperPeriod,
 * and subsequently, analyze and schedule over this time-horizon.
 * This subset is refereed as Reactor-, and it is a restriction of
 * the constructs allowed in Reactor. 
 * 
 * Specifically, we assume that reactor hierarchies are considered flattened,
 * either before analysis or by design. Then, reactions are either pure or periodic.
 * 
 * @param reactors the containers for the reactions.
 * @param periodicReactions triggered by only one timer.
 * @param pureReactions triggered by any number of reactions or input ports.
 * @param containmentFunction relates every reaction to its containing reactor.
 * @param reactionIndex the index in which a reaction appears in its reactor. Used for priority analysis.
 * @param periodFunction gives the period for every periodic reaction in the model.
 * @param channels mapping of edges between reactions and the channels that their information.
 */
final case class ReactorMinusApplication(
    val pureReactions: Set[LinguaFrancaReaction],
    val periodicReactions: Set[LinguaFrancaReaction],
    val reactors: Set[LinguaFrancaReactor],
    val channels: Map[(LinguaFrancaReaction, LinguaFrancaReaction), LinguaFrancaSignal],
    val containmentFunction: Map[LinguaFrancaReaction, LinguaFrancaReactor],
    val reactionIndex: Map[LinguaFrancaReaction, Int],
    val periodFunction: Map[LinguaFrancaReaction, BigFraction],
    executor: ThreadPoolExecutor
    // val sizeFunction: Map[LinguaFrancaReaction | LinguaFrancaReactor | LinguaFrancaSignal, Long]
) extends SimpleDirectedGraph[LinguaFrancaReaction, LinguaFrancaSignal](classOf[LinguaFrancaSignal])
    with DecisionModel:

  for (r               <- pureReactions) addVertex(r)
  for (r               <- periodicReactions) addVertex(r)
  for (((r1, r2) -> c) <- channels) addEdge(r1, r2, c)


  /** This ordering orders the reactions in the model according to reactor containment and if x > y,
    * then x has _higher_ priority than y, and should always have execution precedence/priority.
    */
  lazy val reactionsPriorityOrdering = new Ordering[LinguaFrancaReaction] {

    def compare(x: LinguaFrancaReaction, y: LinguaFrancaReaction): Int =
      if containmentFunction(x) == containmentFunction(y) then
        reactionIndex(y).compareTo(reactionIndex(x))
      else if reactionsPropagates.contains((x, y)) then 1
      else if reactionsPropagates.contains((y, x)) then -1
      // TODO: fix this approximation to a strict total order
      else periodFunction.getOrElse(y, BigFraction.ZERO).compareTo(periodFunction.getOrElse(x, BigFraction.ZERO))
    // it is reversed because the smaller period takes precedence
    // periodFunction(y).compareTo(periodFunction(x))
  }
  given Ordering[LinguaFrancaReaction] = reactionsPriorityOrdering

  val reactions: Set[LinguaFrancaReaction] = vertexSet.asScala.toSet

  lazy val reactionsOrdered = reactions.toList.sorted

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

  val sizeFunction: Map[LinguaFrancaReactor | LinguaFrancaSignal | LinguaFrancaReaction, Long] = {
    val elemSet: Set[LinguaFrancaReactor | LinguaFrancaSignal | LinguaFrancaReaction] =
      (reactors ++ channels.values ++ reactions)
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

  lazy val reactionsOnlyExtendedConnectionsGraph =
    val g = SimpleDirectedGraph[LinguaFrancaReaction, DefaultEdge](classOf[DefaultEdge])
    for (r <- vertexSet.asScala) g.addVertex(r)
    for (
      r <- vertexSet.asScala; rr <- vertexSet.asScala;
      if r != rr;
      if containsEdge(r, rr) || containmentFunction(r) == containmentFunction(rr)
    ) g.addEdge(r, rr)
    g

  lazy val reactionsOnlyWithPropagationsGraph =
    val g = SimpleDirectedGraph[LinguaFrancaReaction, DefaultEdge](classOf[DefaultEdge])
    for (r <- vertexSet.asScala) g.addVertex(r)
    for (
      r <- vertexSet.asScala; rr <- vertexSet.asScala;
      if r != rr;
      if containsEdge(
        r,
        rr
      ) || (containmentFunction(r) == containmentFunction(rr) && reactionIndex(r) < reactionIndex(
        rr
      ))
    ) g.addEdge(r, rr)
    g

  lazy val reactionsPropagates: Set[(LinguaFrancaReaction, LinguaFrancaReaction)] =
    reactionsOnlyWithPropagationsGraph.vertexSet.asScala
      .filter(v => reactionsOnlyWithPropagationsGraph.incomingEdgesOf(v).isEmpty)
      .flatMap(src => {
        BreadthFirstIterator(reactionsOnlyWithPropagationsGraph, src).asScala
          .filter(v => v != src)
          .map(dst => (src, dst))
      })
      .toSet
  
  lazy val reactionsExtendedReachability: Set[(LinguaFrancaReaction, LinguaFrancaReaction)] =
    reactionsOnlyExtendedConnectionsGraph.vertexSet.asScala
      .filter(v => !reactionsOnlyExtendedConnectionsGraph.outgoingEdgesOf(v).isEmpty)
      .flatMap(src => {
        BreadthFirstIterator(reactionsOnlyExtendedConnectionsGraph, src).asScala
          .filter(v => v != src)
          .map(dst => (src, dst))
      })
      .toSet


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
  lazy val unambigousEndToEndReactions
      : Map[(LinguaFrancaReaction, LinguaFrancaReaction), Seq[LinguaFrancaReaction]] =
    val consensedReactionGraph = GabowStrongConnectivityInspector(
      reactionsOnlyExtendedConnectionsGraph
    ).getCondensation
    val sourcesJava = consensedReactionGraph.vertexSet.stream
      .filter(g => consensedReactionGraph.incomingEdgesOf(g).isEmpty)
      .flatMap(g => g.vertexSet.stream)
      .filter(periodicReactions.contains(_))
      .collect(Collectors.toSet)
    val sinksJava = consensedReactionGraph.vertexSet.stream
      .filter(g => consensedReactionGraph.outgoingEdgesOf(g).isEmpty)
      .flatMap(g => g.vertexSet.stream)
      .filter(periodicReactions.contains(_))
      .collect(Collectors.toSet)
    val paths = DijkstraManyToManyShortestPaths(reactionsOnlyExtendedConnectionsGraph)
      .getManyToManyPaths(sourcesJava, sinksJava)
    val sources = sourcesJava.asScala
    val sinks   = sinksJava.asScala
    val mutableSet = for (
      src <- sources;
      dst <- sinks;
      path = paths.getPath(src, dst);
      if path != null
    ) yield (src, dst) -> path.getVertexList.asScala.toSeq
    mutableSet.toMap

  /** @return
    *   the jobs graph computed out of this Reaction- model, in a lazy fashion since the computation
    *   can be slightly demanding for bigger graphs.
    */
  lazy val jobGraph = ReactorMinusAppJobGraph(this, executor)

  lazy val unambigousEndToEndFixedLatencies
      : Map[(LinguaFrancaReaction, LinguaFrancaReaction), BigFraction] =
    jobGraph.jobLevelFixedLatencies
    .groupBy((srcdst, w) => (srcdst._1.srcReaction, srcdst._2.srcReaction))
    .map((srcdst, w) => srcdst -> w.values.max)

  /** The left-to-right maximal interference analysis results
    * @return
    *   returns a Map of reactions (src, dst) with a sequence of points, which give the maximum and
    *   thoughest set of points in which Reaction 'src' interferes with 'dst', if any.
    */
  lazy val maximalInterferencePoints
      : Map[(LinguaFrancaReaction, LinguaFrancaReaction), Seq[BigFraction]] =
    (for (r <- reactions; rr <- reactions - r; if reactionsPriorityOrdering.compare(r, rr) >= 0) yield
      val (j, jSeq) = jobGraph.reactionToJobs(r).map(j =>
        j -> jobGraph.reactionToJobs(rr).filter(jj => j.interferes(jj)).map(jj => jj.trigger).toList
      ).maxBy((j, jjStartSeq) => 
        jjStartSeq.map(jjTrigger => j.deadline.subtract(jjTrigger).doubleValue).sum
      )
      (r, rr) -> jSeq.map(jjTrigger => jjTrigger.subtract(j.trigger))
    ).toMap
        

  override val uniqueIdentifier = "ReactorMinusApplication"

end ReactorMinusApplication
