package idesyde.choco

import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import idesyde.exploration.choco.explorers.ParetoMinimizationBrancher
import org.chocosolver.solver.constraints.Constraint

trait CanSolveMultiObjective {

  def createAndApplyPropagator(m: Model, objs: Array[IntVar]): ParetoMinimizationBrancher = {
    if (objs.size == 1) {
      m.setObjective(
        false,
        objs.head
      )
    }
    val paretoMinimizer = ParetoMinimizationBrancher(objs)
    m.getSolver().plugMonitor(paretoMinimizer)
    m.post(new Constraint("paretoOptConstraint", paretoMinimizer))
    paretoMinimizer
  }
}
