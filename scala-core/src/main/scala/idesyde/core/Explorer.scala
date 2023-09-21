package idesyde.core

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import java.time.Duration
import idesyde.core.ExplorationCriteria
import idesyde.core.DecisionModel
import idesyde.core.headers.ExplorerHeader

import upickle.default._

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

  type ExplorationSolution = (DecisionModel, Map[String, Double])

  def explore(
      decisionModel: DecisionModel,
      previousSolutions: Set[ExplorationSolution],
      totalExplorationTimeOutInSecs: Long,
      maximumSolutions: Long,
      timeDiscretizationFactor: Long,
      memoryDiscretizationFactor: Long
  ): LazyList[ExplorationSolution]

  def explore(
      decisionModel: DecisionModel,
      previousSolutions: Set[ExplorationSolution] = Set(),
      configuration: Explorer.Configuration = Explorer.Configuration(0, 0, 0, 0)
  ): LazyList[ExplorationSolution] = explore(
    decisionModel,
    previousSolutions,
    configuration.total_timeout,
    configuration.max_sols,
    configuration.time_resolution,
    configuration.memory_resolution
  )

  def availableCriterias(decisionModel: DecisionModel): Set[ExplorationCriteria] = Set()

  def criteriaValue(decisionModel: DecisionModel, criteria: ExplorationCriteria): Double = 0.0

  def bid(decisionModel: DecisionModel): ExplorationCombinationDescription =
    ExplorationCombinationDescription.impossible(uniqueIdentifier)

  def canExplore(decisionModel: DecisionModel): Boolean =
    bid(decisionModel).can_explore

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

object Explorer {
  final case class Configuration(
      max_sols: Long,
      total_timeout: Long,
      time_resolution: Long,
      memory_resolution: Long
  ) derives ReadWriter {}

}
