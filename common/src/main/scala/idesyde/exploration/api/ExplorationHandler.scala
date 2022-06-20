package idesyde.exploration.api

import scala.concurrent.Future
import idesyde.exploration.api.ExplorationCriteria
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import java.util.stream.Collectors

import collection.JavaConverters.*
import idesyde.exploration.interfaces.Explorer
import idesyde.identification.DecisionModel

final class ExplorationHandler(
    var explorationModules: Set[ExplorationModule] = Set.empty
):

  def registerModule(explorationModule: ExplorationModule): ExplorationHandler =
    explorationModules += explorationModule
    this

  def chooseExplorersAndModels(
      decisionModels: Set[? <: DecisionModel],
      explorationCriteria: Set[ExplorationCriteria] = Set(ExplorationCriteria.TimeUntilOptimality)
  ): Set[(Explorer, ? <: DecisionModel)] =
    val explorers        = explorationModules.flatMap(_.explorers) //.map(_.asInstanceOf[Explorer])
    val explorableModels = decisionModels.filter(m => explorers.exists(e => e.canExplore(m)))
    scribe.debug(s"total of ${explorableModels.size} exp. models to find combos.")
    // for each of the explorable models build up a dominance graph of the available explorers
    // based on the criteria supplied
    val modelToExplorerSet =
      for (m <- explorableModels) yield
        val dominanceGraph = SimpleDirectedGraph[Explorer, DefaultEdge](classOf[DefaultEdge])
        for (e <- explorers) if (e.canExplore(m)) dominanceGraph.addVertex(e)
        dominanceGraph.vertexSet
          .stream()
          .forEach(e => {
            dominanceGraph.vertexSet
              .stream()
              .forEach(ee => {
                if (e.dominates(ee, m, explorationCriteria)) dominanceGraph.addEdge(e, ee)
              })
          })
        // find the SCC and leave out the dominated ones
        val dominanceCondensation =
          GabowStrongConnectivityInspector(dominanceGraph).getCondensation()
        val dominant = dominanceCondensation.vertexSet.stream
          .filter(g => dominanceCondensation.incomingEdgesOf(g).isEmpty)
          .flatMap(g => g.vertexSet.stream)
          .collect(Collectors.toSet)
          .asScala
        m -> dominant
      end for
    // flat map the model to set of explorers to map of model to explorers
    val modelToExplorers = modelToExplorerSet.flatMap((m, es) => es.map(_ -> m))
    modelToExplorers

// def exploreForSyDeDecisionModel(ForSyDeDecisionModel: ForSyDeDecisionModel, extraExplorers: Set[Explorer[? <: ForSyDeDecisionModel]] = Set()): Future[ForSyDeDecisionModel] = {
//     val explorers = defaultExplorers ++ extraExplorers

// }

end ExplorationHandler
