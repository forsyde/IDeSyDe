package idesyde.exploration

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.Duration
import idesyde.exploration.ExplorationCriteria
import idesyde.core.DecisionModel

/** This trait is the root for all possible explorers within IDeSyDe. A real explorer should
  * implement this trait by dispatching the real exploration from 'explore'.
  *
  * The Design model is left a type parameter because the explorer might be used in a context where
  * explorers for different decision models and design models are used together. A correct
  * implemention of the explorer should then:
  *
  *   1. if the DesignModel is part of the possible design models covered, it should return a
  *      lazylist accodingly. 2. If the DesignModel si not part of the possible design models, then
  *      the explorer should return an empty lazy list. 3. If the decision model is unexplorable
  *      regardless, an empty list should be returned.
  *
  * See [1] for some extra information on how the explorer fits the design space identifcation
  * approach, as well as [[idesyde.exploration.api.ExplorationHandler]] to see how explorers used
  * together in a generic context.
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait Explorer {

  type DesignModel

  def explore(
      decisionModel: DecisionModel,
      explorationTimeOutInSecs: Long = 0L
  ): LazyList[DecisionModel]

  def availableCriterias(decisionModel: DecisionModel): Set[ExplorationCriteria] = Set()

  def criteriaValue(decisionModel: DecisionModel, criteria: ExplorationCriteria): Double = 0.0

  def canExplore(decisionModel: DecisionModel): Boolean

  @deprecated("this was substituded by the criteriaValue function")
  def estimateTimeUntilFeasibility(decisionModel: DecisionModel) =
    criteriaValue(decisionModel, ExplorationCriteria.TimeUntilFeasibility)

  @deprecated("this was substituded by the criteriaValue function")
  def estimateTimeUntilOptimality(decisionModel: DecisionModel) =
    criteriaValue(decisionModel, ExplorationCriteria.TimeUntilOptimality)

  @deprecated("this was substituded by the criteriaValue function")
  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel) =
    criteriaValue(decisionModel, ExplorationCriteria.MemoryUntilFeasibility)

  @deprecated("this was substituded by the criteriaValue function")
  def estimateMemoryUntilOptimality(decisionModel: DecisionModel) =
    criteriaValue(decisionModel, ExplorationCriteria.MemoryUntilOptimality)

  // def estimateCriteria[V: PartiallyOrdered](decisionModel: DecisionModel, criteria: ExplorationCriteria): V

  def dominates(
      o: Explorer,
      m: DecisionModel,
      desiredCriterias: Set[ExplorationCriteria]
  ): Boolean =
    val otherCriterias = o.availableCriterias(m)
    val comparisonResult = for (
      thisC <- availableCriterias(m)
      if desiredCriterias.contains(thisC)
    )
      yield
        if (otherCriterias.contains(thisC)) {
          if (thisC.moreIsBetter) then criteriaValue(m, thisC) > o.criteriaValue(m, thisC)
          else criteriaValue(m, thisC) < o.criteriaValue(m, thisC)
        } else {
          false
        }
    !comparisonResult.contains(false) && canExplore(m) && o.canExplore(m)

}
