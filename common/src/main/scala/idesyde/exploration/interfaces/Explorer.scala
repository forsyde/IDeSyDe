package idesyde.exploration.interfaces

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.Duration
import idesyde.exploration.api.ExplorationCriteria
import idesyde.identification.DecisionModel


/**
 * This trait is the root for all possible explorers within IDeSyDe. 
 * A real explorer should implement this trait by dispatching the
 * real exploration from 'explore'.
 * 
 * The Design model is left a type parameter because the explorer might
 * be used in a context where explorers for different decision models and
 * design models are used together. A correct implemention of the explorer
 * should then:
 *
 * 1. if the DesignModel is part of the possible design models covered, it
 *    should return a lazylist accodingly.
 * 2. If the DesignModel si not part of the possible design models, then
 *    the explorer should return an empty lazy list.
 * 3. If the decision model is unexplorable regardless, an empty list
 *    should be returned.
 * 
 * See [1] for some extra information on how the explorer fits the design
 * space identifcation approach, as well as [[idesyde.exploration.api.ExplorationHandler]] to
 * see how explorers used together in a generic context.
 * 
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by Composable Design Space Identification," 
 *     2021 Design, Automation & Test in Europe Conference & Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 */
trait Explorer {

  def explore[DesignModel](decisionModel: DecisionModel)(using
      ExecutionContext
  ): LazyList[DesignModel]

  def canExplore(decisionModel: DecisionModel): Boolean

  def estimateTimeUntilFeasibility(decisionModel: DecisionModel): Duration

  def estimateTimeUntilOptimality(decisionModel: DecisionModel): Duration

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long

  def dominates(
      o: Explorer,
      m: DecisionModel,
      criteria: Set[ExplorationCriteria] = Set()
  ): Boolean =
    val c =
      (if (criteria.contains(ExplorationCriteria.TimeUntilFeasibility))
         estimateTimeUntilFeasibility(m).compareTo(o.estimateTimeUntilFeasibility(m)) < 0
       else
         true) &&
        (if (criteria.contains(ExplorationCriteria.TimeUntilOptimality))
           estimateTimeUntilOptimality(m).compareTo(o.estimateTimeUntilOptimality(m)) < 0
         else
           true) &&
        (if (criteria.contains(ExplorationCriteria.MemoryUntilFeasibility))
           estimateMemoryUntilFeasibility(m) < o.estimateMemoryUntilFeasibility(m)
         else
           true) &&
        (if (criteria.contains(ExplorationCriteria.MemoryUntilOptimality))
           estimateMemoryUntilOptimality(m) < o.estimateMemoryUntilOptimality(m)
         else
           true)
    c && canExplore(m) && o.canExplore(m)

}
