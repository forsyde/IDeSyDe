package idesyde.core

import idesyde.core.DecisionModel
import idesyde.core.DesignModel

/** The trait/interface for an identification library that provides the identification and
  * integration rules required to power the design space identification process [1].
  *
  * [[identificationRules]] are functions that abstracts [[DesignModel]] s systematically to
  * [[DecisionModel]] s until it becomes a set of "solvable" parameters and functions.
  *
  * [[integrationRules]] are basically the inverse of [[identificationRules]]: They take solved
  * [[DecisionModels]] and "integrate" the solutions in the abstracted domain back to the design
  * domain, in the [[DesignModel]] s.
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait IdentificationLibrary {

  /** Each identification rule takes a set of design models and a set of decision models to produce
    * a new decision model. The new decision model must cover at least more of the design models
    * than the original given decision model combined
    *
    * @return
    *   The set of identification rules registered in this module
    */
  def identificationRules: Set[
    (Set[DesignModel], Set[DecisionModel]) => Set[? <: DecisionModel]
  ]

  /** Each integration rule takes a design model and (solved/explored) decision model to produce a
    * new design model that has the decision model merged into it. The integration rule might return
    * nothing if the input parameters are unknown.
    *
    * @return
    *   The set of integration rules registered in this module
    */
  def reverseIdentificationRules: Set[
    (DesignModel, DecisionModel) => Option[? <: DesignModel]
  ]
}
