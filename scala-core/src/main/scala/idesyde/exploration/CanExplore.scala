package idesyde.exploration

import scala.concurrent.Future
import idesyde.exploration.ExplorationCriteria
import java.util.stream.Collectors

import collection.JavaConverters.*
import idesyde.exploration.Explorer
import idesyde.identification.DecisionModel
import idesyde.utils.HasUtils
import idesyde.utils.Logger
import scala.annotation.targetName

trait CanExplore(using logger: Logger) extends HasUtils {

  @targetName("chooseExplorersAndModelsWithModules")
  def chooseExplorersAndModels(
      decisionModels: Set[? <: DecisionModel],
      explorationModules: Set[ExplorationModule],
      explorationCriteria: Set[ExplorationCriteria] = Set(ExplorationCriteria.TimeUntilOptimality)
  ): Set[(Explorer, DecisionModel)] = chooseExplorersAndModels(
    decisionModels,
    explorationModules.flatMap(_.explorers),
    explorationCriteria
  )

  @targetName("chooseExplorersAndModelsWithExplorers")
  def chooseExplorersAndModels(
      decisionModels: Set[? <: DecisionModel],
      explorers: Set[Explorer],
      explorationCriteria: Set[ExplorationCriteria]
  ): Set[(Explorer, DecisionModel)] =
    val explorableModels = decisionModels.filter(m => explorers.exists(e => e.canExplore(m)))
    logger.debug(s"total of ${explorableModels.size} exp. models to find combos.")
    // for each of the explorable models build up a dominance graph of the available explorers
    // based on the criteria supplied
    val modelToExplorerSet =
      for (m <- explorableModels) yield
        val possibleExplorers = explorers.filter(_.canExplore(m)).toVector
        val dominanceMatrix = possibleExplorers.map(e =>
          possibleExplorers.map(ee => e.dominates(ee, m, explorationCriteria))
        )
        // keep only the SCC which are leaves
        val dominant = computeSSCFromReachibility(reachibilityClosure(dominanceMatrix))
          .map(idx => possibleExplorers(idx))
        m -> dominant
      end for
    // flat map the model to set of explorers to map of model to explorers
    val modelToExplorers = modelToExplorerSet.flatMap((m, es) => es.map(_ -> m))
    modelToExplorers

}
