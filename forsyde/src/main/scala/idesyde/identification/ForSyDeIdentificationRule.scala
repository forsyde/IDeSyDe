package idesyde.identification

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph

trait ForSyDeIdentificationRule[+M <: ForSyDeDecisionModel] extends IdentificationRule[M] {

    def identify[DesignModel](model: DesignModel, identified: Set[DecisionModel]): (Boolean, Option[M]) = 
        model match {
            case fio: ForSyDeSystemGraph => identifyFromForSyDe(fio, identified)
            case _ => (false, Option.empty)
        }

    def identifyFromForSyDe(model: ForSyDeSystemGraph, identified: Set[DecisionModel]): (Boolean, Option[M]) = ???
}
