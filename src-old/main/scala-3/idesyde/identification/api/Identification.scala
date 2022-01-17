package idesyde.identification.api

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait

import java.util.stream.Collectors
import forsyde.io.java.core.Trait

import scala.collection.mutable.HashSet
import idesyde.identification.rules.{
  NetworkedDigitalHWIdentRule,
  ReactorMinusIdentificationRule,
  ReactorMinusToJobsRule,
  SDFAppIdentificationRule
}
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector

import collection.JavaConverters.*
import idesyde.identification.rules.SchedulableNetDigHWIdentRule
import idesyde.identification.rules.ReactorMinusAppDSEIdentRule
import idesyde.identification.rules.ReactorMinusAppDSEMznIdentRule
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor

object Identification {

  val standardRules: Set[IdentificationRule] =
    Set[IdentificationRule](
      SDFAppIdentificationRule(),
      ReactorMinusIdentificationRule(
        Executors.newFixedThreadPool(1).asInstanceOf[ThreadPoolExecutor]
      ),
      // ReactorMinusToJobsRule(),
      NetworkedDigitalHWIdentRule(),
      SchedulableNetDigHWIdentRule(),
      ReactorMinusAppDSEIdentRule(),
      ReactorMinusAppDSEMznIdentRule()
    )

  def identifyDecisionModels(
      model: ForSyDeSystemGraph,
      rules: Set[IdentificationRule] = Set.empty
  ): Set[DecisionModel] =
    var identified: Set[DecisionModel] = Set()
    var activeRules                    = rules ++ standardRules
    val maxIters                       = activeRules.size * countTraits(model)
    var iters                          = 0
    val dominanceGraph = SimpleDirectedGraph[DecisionModel, DefaultEdge](() => DefaultEdge())
    scribe.info(
      s"Performing identification with ${activeRules.size} rules for $maxIters iterations."
    )
    while (activeRules.size > 0 && iters < maxIters) {
      val ruleResults = activeRules.map(r => (r, r.identify(model, identified)))
      val newIdentified =
        ruleResults.filter((r, res) => !res._2.isEmpty).map((r, res) => res._2.get).toSet
      // scribe.debug("building dominance graph")
      for (m <- newIdentified) dominanceGraph.addVertex(m)
      for (m <- newIdentified; mm <- identified; if m.dominates(mm)) dominanceGraph.addEdge(m, mm)
      for (m <- identified; mm <- newIdentified; if m.dominates(mm)) dominanceGraph.addEdge(m, mm)
      identified = identified ++ newIdentified
      // identified =
      //   identified.filter(m => !identified.exists(other => other != m && other.dominates(m)))
      activeRules = ruleResults.filter((r, res) => !res._1).map(_._1)
      scribe.debug(
        s"identification step $iters: ${identified.size} identified and ${activeRules.size} rules"
      )
      iters += 1
    }
    // condense it for comparison
    val dominanceCondensation = GabowStrongConnectivityInspector(dominanceGraph).getCondensation()
    // keep only the SCC which are leaves
    val dominant = dominanceCondensation.vertexSet.asScala
      .filter(g => dominanceCondensation.incomingEdgesOf(g).isEmpty)
      .flatMap(g => g.vertexSet.asScala)
      .toSet
    scribe.info(s"droppped ${identified.size - dominant.size} dominated decision model(s).")
    scribe.debug(s"domitant: ${dominant.map(m => m.uniqueIdentifier)}")
    dominant
  end identifyDecisionModels

  protected def countTraits(model: ForSyDeSystemGraph): Integer =
    model.vertexSet.stream.flatMap(_.getTraits.stream).distinct.count.toInt

}
