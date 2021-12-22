package idesyde.identification.interfaces

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph

trait MiniZincDecisionModel extends DecisionModel:

    def mznModel: String

    def mznInputs: Map[String, MiniZincData]

    def rebuildFromMznOutputs(output: Map[String, MiniZincData], originalModel: ForSyDeSystemGraph): ForSyDeSystemGraph
