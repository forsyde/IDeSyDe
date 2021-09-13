package idesyde.exploration.api

import idesyde.exploration.Explorer
import idesyde.identification.DecisionModel
import scala.concurrent.Future
import idesyde.exploration.ExplorationCriteria
import idesyde.exploration.explorers.ReactorMinusGecodeMiniZincExplorer
import idesyde.exploration.ReactorMinusToNetHWOrToolsExplorer
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.connectivity.GabowStrongConnectivityInspector
import java.util.stream.Collectors

import collection.JavaConverters.*

object Exploration:

  val defaultExplorers: Set[Explorer] = Set(
    ReactorMinusGecodeMiniZincExplorer(),
    ReactorMinusToNetHWOrToolsExplorer()
  )

  def chooseExplorersAndModels(
      decisionModels: Set[DecisionModel],
      extraExplorers: Set[Explorer] = Set(),
      explorationCriteria: Set[ExplorationCriteria] = Set(ExplorationCriteria.TimeUntilOptimality)
  ): Set[(Explorer, DecisionModel)] =
    val explorers = (defaultExplorers ++ extraExplorers)//.map(_.asInstanceOf[Explorer])
    val explorableModels = decisionModels.filter(m => explorers.exists(e => e.canExplore(m)))
    scribe.debug(s"total of ${explorableModels.size} exp. models to find combos.")
    val modelToExplorers = 
      for (m <- explorableModels) yield
        val dominanceGraph = SimpleDirectedGraph[Explorer, DefaultEdge](classOf[DefaultEdge])
        for (e <- explorers) if (e.canExplore(m)) dominanceGraph.addVertex(e)
        dominanceGraph.vertexSet.stream().forEach(e => {
          dominanceGraph.vertexSet.stream().forEach(ee => {
            if (e.dominates(ee, m, explorationCriteria)) dominanceGraph.addEdge(e, ee)
          })
        })
        val dominanceCondensation = GabowStrongConnectivityInspector(dominanceGraph).getCondensation()
        val dominant = dominanceCondensation.vertexSet.stream
          .filter(g => dominanceCondensation.incomingEdgesOf(g).isEmpty)
          .flatMap(g => g.vertexSet.stream)
          .collect(Collectors.toSet)
          .asScala
        m -> dominant
      end for
    val res = modelToExplorers.flatMap((m, es) => es.map(_ -> m))
    scribe.debug(s"chosen models: ${res.map(_.getClass.toString)}")
    res

    


// def exploreDecisionModel(decisionModel: DecisionModel, extraExplorers: Set[Explorer[? <: DecisionModel]] = Set()): Future[DecisionModel] = {
//     val explorers = defaultExplorers ++ extraExplorers

// }

end Exploration
