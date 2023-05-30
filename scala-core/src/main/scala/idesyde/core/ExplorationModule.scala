package idesyde.core

import idesyde.core.Explorer

/** The trait/interface for an exploration library that provides the explorers rules required to
  * explored identified design spaces [1].
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait ExplorationModule extends Explorer {

  /** The set of explorers registred in this library
    *
    * @return
    *   the registered explorers.
    *
    * @see
    *   [[idesyde.core.Explorer]]
    */
  def explorers: Set[Explorer]

  def canExplore(decisionModel: DecisionModel): Boolean =
    explorers.exists(_.combination(decisionModel).can_explore)

  def explore(
      decisionModel: DecisionModel,
      totalExplorationTimeOutInSecs: Long = 0L,
      maximumSolutions: Long = 0L,
      timeDiscretizationFactor: Long = -1L,
      memoryDiscretizationFactor: Long = -1L
  ): LazyList[DecisionModel] = {
    val valid = explorers
      .filter(_.combination(decisionModel).can_explore)
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
          totalExplorationTimeOutInSecs,
          maximumSolutions,
          timeDiscretizationFactor,
          memoryDiscretizationFactor
        )
      case None => LazyList.empty
    }
  }
}
