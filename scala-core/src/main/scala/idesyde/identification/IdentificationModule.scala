package idesyde.identification

import idesyde.identification.IdentificationRule
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

/** The trait/interface for an identification module gice the identification and integration rules
  * required to power the design space identification process [1].
  *
  * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
  * Composable Design Space Identification," 2021 Design, Automation & Test in Europe Conference &
  * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
  */
trait IdentificationModule {

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
  def integrationRules: Set[
    (DesignModel, DecisionModel) => Option[? <: DesignModel]
  ]
}
