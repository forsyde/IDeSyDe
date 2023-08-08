package idesyde.core

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.Duration
import idesyde.core.ExplorationCriteria
import idesyde.core.DecisionModel
import idesyde.core.headers.ExplorerHeader

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

  def explore(
      decisionModel: DecisionModel,
      objectivesUpperLimits: Set[Map[String, Double]] = Set(),
      totalExplorationTimeOutInSecs: Long = 0L,
      maximumSolutions: Long = 0L,
      timeDiscretizationFactor: Long = -1L,
      memoryDiscretizationFactor: Long = -1L
  ): LazyList[(DecisionModel, Map[String, Double])]

  def availableCriterias(decisionModel: DecisionModel): Set[ExplorationCriteria] = Set()

  def criteriaValue(decisionModel: DecisionModel, criteria: ExplorationCriteria): Double = 0.0

  def combination(decisionModel: DecisionModel): ExplorationCombinationDescription =
    ExplorationCombinationDescription.impossible(uniqueIdentifier, decisionModel.category)

  def canExplore(decisionModel: DecisionModel): Boolean =
    combination(decisionModel).can_explore

  def dominates(
      o: Explorer,
      m: DecisionModel
  ): Boolean =
    val otherCriterias = o.availableCriterias(m)
    val comparisonResult =
      for (thisC <- availableCriterias(m))
        yield
          if (otherCriterias.contains(thisC)) {
            if (thisC.moreIsBetter) then criteriaValue(m, thisC) > o.criteriaValue(m, thisC)
            else criteriaValue(m, thisC) < o.criteriaValue(m, thisC)
          } else {
            false
          }
    !comparisonResult.contains(false)
  //&& combination(m) && o.combination(m)

  def uniqueIdentifier: String

  def header: ExplorerHeader = ExplorerHeader(uniqueIdentifier, ":embedded:")

  override def equals(x: Any): Boolean = x match {
    case o: Explorer =>
      uniqueIdentifier == o.uniqueIdentifier
    case _ =>
      false
  }

}
