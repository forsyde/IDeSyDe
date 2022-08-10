package idesyde.exploration

import scala.concurrent.Future
import idesyde.exploration.ExplorationCriteria
import java.util.stream.Collectors

import collection.JavaConverters.*
import idesyde.exploration.Explorer
import idesyde.identification.DecisionModel
import idesyde.utils.CoreUtils

final class ExplorationHandler(
    var explorationModules: Set[ExplorationModule] = Set.empty,
    val infoLogger: (String) => Unit = (s => println(s)),
    val debugLogger: (String) => Unit = (s => println(s))
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
    debugLogger(s"total of ${explorableModels.size} exp. models to find combos.")
    // for each of the explorable models build up a dominance graph of the available explorers
    // based on the criteria supplied
    val modelToExplorerSet =
      for (m <- explorableModels) yield
        val possibleExplorers = explorers.filter(_.canExplore(m)).toArray
        val dominanceMatrix = possibleExplorers.map(e => possibleExplorers.map(ee => e.dominates(ee, m, explorationCriteria)))
        // keep only the SCC which are leaves
        val dominant = CoreUtils.computeDominantFromReachability(dominanceMatrix).map(idx => possibleExplorers(idx))
        // val dominant = dominanceComponents.filter(component => {
        //   // keep dominant components in which no other components dominate any decision model
        //   // therein
        //   dominanceComponents.filter(_ != component).forall(other => {
        //     // decision models in component
        //     def componentModels = component.map(possibleExplorers(_))
        //     def otherModels = other.map(possibleExplorers(_))
        //     !componentModels.exists(e => otherModels.exists(ee => ee.dominates(e, m, explorationCriteria)))
        //   })
        // }).flatMap(component => component.map(idx => possibleExplorers(idx))).toSet
        // therefore, for this decision model, these explorers are the dominant alternative
        m -> dominant
      end for
    // flat map the model to set of explorers to map of model to explorers
    val modelToExplorers = modelToExplorerSet.flatMap((m, es) => es.map(_ -> m))
    modelToExplorers

// def exploreForSyDeDecisionModel(ForSyDeDecisionModel: ForSyDeDecisionModel, extraExplorers: Set[Explorer[? <: ForSyDeDecisionModel]] = Set()): Future[ForSyDeDecisionModel] = {
//     val explorers = defaultExplorers ++ extraExplorers

// }

end ExplorationHandler
