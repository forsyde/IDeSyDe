package idesyde.exploration.interfaces

import idesyde.identification.DecisionModel
import idesyde.identification.interfaces.OrToolsCPDecisionModel
import com.google.ortools.sat.CpModel
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeModel
import com.google.ortools.sat.CpSolver
import idesyde.exploration.Explorer

trait SimpleOrToolsCPExplorer extends Explorer: 

    def explore(decisionModel: OrToolsCPDecisionModel, orignalDesignModel: ForSyDeModel)(using ExecutionContext): Future[Option[ForSyDeModel]] = 
        val cpModel = decisionModel.cpModel
        val solver = CpSolver()
        Future({
            val status = solver.solve(cpModel)
            Option(decisionModel.rebuildDesignModel(cpModel, orignalDesignModel))
        })
        
