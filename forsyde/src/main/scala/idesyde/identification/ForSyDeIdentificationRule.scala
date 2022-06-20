package idesyde.identification

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph

trait ForSyDeIdentificationRule[+M <: ForSyDeDecisionModel] extends IdentificationRule[M] {

    type G = ForSyDeSystemGraph
}
