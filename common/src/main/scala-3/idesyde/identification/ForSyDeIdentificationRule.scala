package idesyde.identification

import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph

trait ForSyDeIdentificationRule[M <: DecisionModel]
    extends IdentificationRule[ForSyDeSystemGraph, M] {}
