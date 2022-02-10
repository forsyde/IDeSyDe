package idesyde.exploration.api

import idesyde.identification.DecisionModel
import scala.concurrent.Future
import idesyde.exploration.api.ExplorationCriteria
import idesyde.exploration.explorers.GecodeMiniZincExplorer
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import java.util.stream.Collectors

import collection.JavaConverters.*
import idesyde.exploration.explorers.ChuffedMiniZincExplorer
import idesyde.exploration.interfaces.Explorer

object Exploration:

  val defaultExplorers: Set[Explorer] = Set(
    GecodeMiniZincExplorer(),
    ChuffedMiniZincExplorer()
  )

  def chooseExplorersAndModels(
      decisionModels: Set[DecisionModel],
      extraExplorers: Set[Explorer] = Set(),
      explorationCriteria: Set[ExplorationCriteria] = Set(ExplorationCriteria.TimeUntilOptimality)
  ): Set[(Explorer, DecisionModel)] =
    val explorers        = (defaultExplorers ++ extraExplorers) //.map(_.asInstanceOf[Explorer])
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

// def exploreDecisionModel(decisionModel: DecisionModel, extraExplorers: Set[Explorer[? <: DecisionModel]] = Set()): Future[DecisionModel] = {
//     val explorers = defaultExplorers ++ extraExplorers

// }

end Exploration
