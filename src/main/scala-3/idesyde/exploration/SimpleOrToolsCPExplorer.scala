package idesyde.exploration

import idesyde.identification.DecisionModel
import idesyde.identification.interfaces.OrToolsCPDecisionModel
import com.google.ortools.sat.CpModel
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeModel
import com.google.ortools.sat.CpSolver

trait SimpleOrToolsCPExplorer[M <: OrToolsCPDecisionModel] extends Explorer[M]: 

    def explore(decisionModel: M)(using ExecutionContext): Future[ForSyDeModel] = 
        val cpModel = decisionModel.cpModel
        val rebuilder = decisionModel.rebuildModelFunction
        val solver = CpSolver()
        Future({
            val status = solver.solve(cpModel)
            rebuilder(cpModel)
        })
        
