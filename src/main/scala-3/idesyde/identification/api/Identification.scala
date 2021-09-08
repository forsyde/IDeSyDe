package idesyde.identification.api

import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.VertexTrait

import java.util.stream.Collectors
import forsyde.io.java.core.Trait

import scala.collection.mutable.HashSet
import idesyde.identification.rules.{NetworkedDigitalHWIdentRule, ReactorMinusIdentificationRule, ReactorMinusToJobsRule, SDFAppIdentificationRule}
import org.slf4j.event.Level
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector

import collection.JavaConverters.*

object Identification {

  def getStandardRules(): Set[IdentificationRule[? <: DecisionModel]] =
    Set[IdentificationRule[? <: DecisionModel]](
      SDFAppIdentificationRule(),
      ReactorMinusIdentificationRule(),
      ReactorMinusToJobsRule(),
      NetworkedDigitalHWIdentRule()
    )

  def identifyDecisionModels(
      model: ForSyDeModel,
      rules: Set[IdentificationRule[? <: DecisionModel]] = Set.empty,
      loggingLevel: Level = Level.INFO
  ): Set[? <: DecisionModel] = {
    var identified: Set[DecisionModel] = Set()
    var activeRules                    = rules ++ getStandardRules()
    val maxIters                       = activeRules.size * countTraits(model)
    var iters                          = 0
    scribe.info(
      s"Performing identification with ${activeRules.size} rules for $maxIters iterations."
    )
    while (activeRules.size > 0 && iters < maxIters) {
      val ruleResults = activeRules.map(r => (r, r.identify(model, identified)))
      identified = identified.union(
        ruleResults.filter((r, res) => !res._2.isEmpty).map((r, res) => res._2.get).toSet
      )
      // identified =
      //   identified.filter(m => !identified.exists(other => other != m && other.dominates(m)))
      activeRules = ruleResults.filter((r, res) => !res._1).map(_._1)
      scribe.debug(
        s"identification step $iters: ${identified.size} identified and ${activeRules.size} rules"
      )
      iters += 1
    }
    // build up the dominance graph
    val dominanceGraph = SimpleDirectedGraph[DecisionModel, DefaultEdge](classOf[DefaultEdge])
    for (m <- identified) dominanceGraph.addVertex(m)
    for (m <- identified; mm <- identified; if m != mm && m.dominates(mm))
      dominanceGraph.addEdge(m, mm)
    // condense it for comparison
    val dominanceCondensation = GabowStrongConnectivityInspector(dominanceGraph).getCondensation()
    // keep only the SCC which are leaves
    dominanceCondensation.vertexSet.asScala
          .filter(g => dominanceCondensation.outgoingEdgesOf(g).isEmpty)
          .flatMap(g => g.vertexSet.asScala)
          .toSet
  }

  protected def countTraits(model: ForSyDeModel): Integer =
    model.vertexSet.stream.flatMap(_.getTraits.stream).distinct.count.toInt

}
