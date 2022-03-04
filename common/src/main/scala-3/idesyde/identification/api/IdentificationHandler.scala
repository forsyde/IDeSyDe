package idesyde.identification.api

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import forsyde.io.java.core.VertexTrait

import java.util.stream.Collectors
import forsyde.io.java.core.Trait

import scala.collection.mutable.HashSet
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import idesyde.identification.rules.sdf.SDFAppIdentificationRule
import idesyde.identification.rules.reactor.ReactorMinusIdentificationRule
import idesyde.identification.rules.mixed.ReactorMinusAppDSEIdentRule
import idesyde.identification.rules.mixed.ReactorMinusAppDSEMznIdentRule
import idesyde.identification.rules.platform.NetworkedDigitalHWIdentRule
import idesyde.identification.rules.platform.SchedulableNetDigHWIdentRule

import collection.JavaConverters.*
import java.util.concurrent.Executors
import java.util.concurrent.ThreadPoolExecutor
import idesyde.identification.rules.workload.PeriodicTaskIdentificationRule
import org.apache.commons.math3.fraction.BigFraction
import idesyde.utils.BigFractionIsNumeric

class IdentificationHandler(
    var registeredModules: Set[IdentificationModule] = Set(CommonIdentificationModule())
) {

  def registerIdentificationRule(identModule: IdentificationModule): IdentificationHandler =
    registeredModules += identModule
    this

  def identifyDecisionModels(
      model: ForSyDeSystemGraph
  ): Set[DecisionModel] =
    var identified: Set[DecisionModel] = Set()
    var activeRules                    = registeredModules.flatMap(_.identificationRules)
    val maxIters                       = activeRules.size * countTraits(model)
    var iters                          = 0
    val dominanceGraph = SimpleDirectedGraph[DecisionModel, DefaultEdge](classOf[DefaultEdge])
    scribe.info(
      s"Performing identification with ${activeRules.size} rules up to $maxIters iterations."
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
