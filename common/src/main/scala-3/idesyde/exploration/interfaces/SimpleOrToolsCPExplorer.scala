package idesyde.exploration.interfaces

import idesyde.identification.DecisionModel
import idesyde.identification.interfaces.OrToolsCPDecisionModel
//import com.google.ortools.sat.CpModel
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeSystemGraph
//import com.google.ortools.sat.CpSolver
import idesyde.exploration.Explorer

trait SimpleOrToolsCPExplorer extends Explorer: 

    def explore(decisionModel: OrToolsCPDecisionModel, orignalDesignModel: ForSyDeSystemGraph)(using ExecutionContext): Future[Option[ForSyDeSystemGraph]] = 
        /* val cpModel = decisionModel.cpModel
        val solver = CpSolver() */
        Future({
            /* val status = solver.solve(cpModel)
            Option(decisionModel.rebuildDesignModel(cpModel, orignalDesignModel)) */
            Option.empty
        })
        
