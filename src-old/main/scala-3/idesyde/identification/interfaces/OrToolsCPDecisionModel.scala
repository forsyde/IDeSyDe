package idesyde.identification.interfaces

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel
import com.google.ortools.sat.CpModel

trait OrToolsCPDecisionModel() extends DecisionModel:

    def cpModel: CpModel

    def rebuildDesignModel(output: CpModel, originalModel: ForSyDeModel): ForSyDeModel