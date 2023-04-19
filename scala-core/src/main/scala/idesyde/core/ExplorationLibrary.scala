package idesyde.core

import idesyde.core.Explorer

/** The trait/interface for an exploration library that provides the explorers rules required to
  * explored identified design spaces [1].
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait ExplorationLibrary {

  /** The set of explorers registred in this library
    *
    * @return
    *   the registered explorers.
    *
    * @see
    *   [[idesyde.core.Explorer]]
    */
  def explorers: Set[Explorer]
}
