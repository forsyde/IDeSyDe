package idesyde.exploration

import scala.concurrent.Future
import idesyde.core.ExplorationCriteria
import java.util.stream.Collectors

import collection.JavaConverters.*
import idesyde.core.Explorer
import idesyde.core.DecisionModel
import idesyde.utils.HasUtils
import idesyde.utils.Logger
import scala.annotation.targetName
import idesyde.core.ExplorationCombinationDescription
import idesyde.core.ExplorationLibrary

trait CanExplore(using logger: Logger) extends HasUtils {

  @targetName("chooseExplorersAndModelsWithModules")
  def chooseExplorersAndModels(
      decisionModels: Set[? <: DecisionModel],
      explorationModules: Set[ExplorationLibrary],
      explorationCriteria: Set[ExplorationCriteria] = Set(ExplorationCriteria.TimeUntilOptimality)
  ): Set[(? <: Explorer, ? <: DecisionModel)] = chooseExplorersAndModels(
    decisionModels,
    explorationModules.flatMap(_.explorers),
    explorationCriteria
  )

  @targetName("chooseExplorersAndModelsWithExplorers")
  def chooseExplorersAndModels(
      decisionModels: Set[? <: DecisionModel],
      explorers: Set[Explorer],
      explorationCriteria: Set[ExplorationCriteria]
  ): Set[(? <: Explorer, ? <: DecisionModel)] =
    val explorableModels =
      decisionModels.filter(m => explorers.exists(e => e.combination(m).can_explore))
    logger.debug(s"total of ${explorableModels.size} exp. models to find combos.")
    // for each of the explorable models build up a dominance graph of the available explorers
    // based on the criteria supplied
    val modelToExplorerSet =
      for (m <- explorableModels) yield
        val possibleExplorers = explorers.filter(_.combination(m).can_explore).toVector
        val dominanceMatrix =
          possibleExplorers.map(e => possibleExplorers.map(ee => e.dominates(ee, m)))
        // keep only the SCC which are leaves
        val dominant = computeSSCFromReachibility(reachibilityClosure(dominanceMatrix))
          .map(idx => possibleExplorers(idx))
        m -> dominant
      end for
    // flat map the model to set of explorers to map of model to explorers
    val modelToExplorers = modelToExplorerSet.flatMap((m, es) => es.map(exp => (exp, m)))
    modelToExplorers

}
