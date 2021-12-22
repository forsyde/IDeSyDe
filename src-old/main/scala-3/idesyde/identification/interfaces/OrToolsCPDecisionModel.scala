package idesyde.identification.interfaces

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import com.google.ortools.sat.CpModel

trait OrToolsCPDecisionModel() extends DecisionModel:

    def cpModel: CpModel

    def rebuildDesignModel(output: CpModel, originalModel: ForSyDeSystemGraph): ForSyDeSystemGraph