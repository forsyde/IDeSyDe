package idesyde.identification.interfaces

import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph

trait MiniZincForSyDeDecisionModel extends ForSyDeDecisionModel:

    def mznModel: String

    def mznInputs: Map[String, MiniZincData]

    def rebuildFromMznOutputs(output: Map[String, MiniZincData], originalModel: ForSyDeSystemGraph): ForSyDeSystemGraph
