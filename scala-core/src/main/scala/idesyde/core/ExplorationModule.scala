package idesyde.core

import idesyde.core.Explorer

/** The trait/interface for an exploration library that provides the explorers rules required to
  * explored identified design spaces [1].
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait ExplorationModule {

  type ExplorationSolution = (DecisionModel, Map[String, Double])

  /** The set of explorers registred in this library
    *
    * @return
    *   the registered explorers.
    *
    * @see
    *   [[idesyde.core.Explorer]]
    */
  def explorers: List[Explorer] = List()

  def canExplore(decisionModel: DecisionModel): Boolean =
    explorers.exists(e => e.bid(decisionModel).can_explore)

  def combination(decisionModel: DecisionModel): List[ExplorationCombinationDescription] =
    explorers.map(e => e.bid(decisionModel))

  def explore(
      decisionModel: DecisionModel,
      explorerId: String,
      previousSolutions: Set[ExplorationSolution] = Set(),
      configuration: ExplorerConfiguration
      // totalExplorationTimeOutInSecs: Long = 0L,
      // maximumSolutions: Long = 0L,
      // timeDiscretizationFactor: Long = -1L,
      // memoryDiscretizationFactor: Long = -1L
  ): LazyList[ExplorationSolution] =
    explorers
      .find(_.uniqueIdentifier == explorerId)
      .map(
        _.explore(
          decisionModel,
          previousSolutions,
          configuration
        )
      )
      .getOrElse(LazyList.empty)

  def exploreBest(
      decisionModel: DecisionModel,
      previousSolutions: Set[ExplorationSolution] = Set(),
      configuration: ExplorerConfiguration
      // totalExplorationTimeOutInSecs: Long = 0L,
      // maximumSolutions: Long = 0L,
      // timeDiscretizationFactor: Long = -1L,
      // memoryDiscretizationFactor: Long = -1L
  ): LazyList[ExplorationSolution] = {
    val valid = explorers
      .filter(e => canExplore(decisionModel))
    val nonDominated =
      valid
        .filter(e =>
          !valid
            .filter(_ != e)
            .exists(ee => ee.dominates(e, decisionModel))
        )
        .headOption
    nonDominated match {
      case Some(e) =>
        e.explore(
          decisionModel,
          previousSolutions,
          configuration
          // totalExplorationTimeOutInSecs,
          // maximumSolutions,
          // timeDiscretizationFactor,
          // memoryDiscretizationFactor
        )
      case None => LazyList.empty
    }
  }
}
