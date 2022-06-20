package idesyde.identification.api

import idesyde.identification.DecisionModel

import idesyde.identification.IdentificationRule

class MinizincIdentificationModule extends idesyde.identification.api.IdentificationModule {

    def identificationRules: Set[IdentificationRule[? <: DecisionModel]] = Set()
  
}
