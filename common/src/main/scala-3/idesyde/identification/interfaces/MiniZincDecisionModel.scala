package idesyde.identification.interfaces

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel

trait MiniZincDecisionModel extends DecisionModel:

    def mznModel: String

    def mznInputs: Map[String, MiniZincData]

    def rebuildFromMznOutputs(output: Map[String, MiniZincData], originalModel: ForSyDeModel): ForSyDeModel
