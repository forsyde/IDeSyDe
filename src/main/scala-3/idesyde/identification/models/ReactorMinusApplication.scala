package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.util.ArithmeticUtils
import forsyde.io.java.typed.viewers.LinguaFrancaTimer
import forsyde.io.java.typed.viewers.LinguaFrancaReaction
import forsyde.io.java.typed.viewers.LinguaFrancaReactor
import forsyde.io.java.typed.viewers.LinguaFrancaSignal
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import collection.JavaConverters.*

final case class ReactorMinusApplication(
    val pureReactions: Set[LinguaFrancaReaction],
    val periodicReactions: Set[LinguaFrancaReaction],
    val reactors: Set[LinguaFrancaReactor],
    val channels: Map[(LinguaFrancaReaction, LinguaFrancaReaction), LinguaFrancaSignal],
    val containmentFunction: Map[LinguaFrancaReaction, LinguaFrancaReactor],
    val priorityRelation: Set[(LinguaFrancaReaction, LinguaFrancaReaction)],
    val periodFunction: Map[LinguaFrancaReaction, Fraction],
    val sizeFunction: Map[LinguaFrancaReaction | LinguaFrancaReactor | LinguaFrancaSignal, Long]
) extends SimpleDirectedGraph[LinguaFrancaReaction, LinguaFrancaSignal](classOf[LinguaFrancaSignal])
    with DecisionModel {
    
  for (r <- pureReactions) addVertex(r)
  for (r <- periodicReactions) addVertex(r)
  for (((r1, r2) -> c) <- channels) addEdge(r1, r2, c)

  def reactions(): Set[LinguaFrancaReaction] = vertexSet.asScala.toSet

  def hyperPeriod(): Fraction = periodFunction.values.reduce((frac1, frac2) =>
    // the LCM of a nunch of fractions n1/d1, n2/d2... is lcm(n1, n2,...)/gcd(d1, d2,...). You can check.
    Fraction(
      ArithmeticUtils.lcm(frac1.getNumerator, frac2.getNumerator),
      ArithmeticUtils.gcd(frac1.getDenominator, frac2.getDenominator)
    )
  )

  def coveredVertexes() = {
    for (v <- reactions()) yield v.getViewedVertex
    for (v <- reactors) yield v.getViewedVertex
    // for (a <- reactor; t <- a.get)
    for ((_, c) <- channels) yield c.getViewedVertex
  }

  def coveredEdges() = Seq()

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

}