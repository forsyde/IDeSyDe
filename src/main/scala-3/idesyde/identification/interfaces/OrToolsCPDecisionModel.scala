package idesyde.identification.interfaces

import com.google.ortools.sat.CpModel
import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel

trait OrToolsCPDecisionModel() extends DecisionModel:

    def cpModel: CpModel

    def rebuildModelFunction: (CpModel) => ForSyDeModel