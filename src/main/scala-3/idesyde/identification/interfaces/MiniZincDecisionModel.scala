package idesyde.identification.interfaces

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel

trait MiniZincDecisionModel extends DecisionModel:

    def mznModel: String

    def mznInputs: Map[String, MinizincData]

    def rebuildFromMznOutputs(output: Map[String, MinizincData], originalModel: ForSyDeModel): ForSyDeModel
