package idesyde.choco

import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import idesyde.exploration.choco.explorers.ParetoMinimizationBrancher
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.objective.ParetoMaximizer

trait CanSolveMultiObjective {

  def createAndApplyMOOPropagator(m: Model, objs: Array[IntVar]): ParetoMinimizationBrancher = {
    if (objs.size == 1) {
      m.setObjective(
        false,
        objs.head
      )
    }
    // val paretoMaximizer = ParetoMaximizer(objs.map(m.intMinusView(_)))
    val paretoBrancher = ParetoMinimizationBrancher(objs)
    m.getSolver().plugMonitor(paretoBrancher)
    m.post(new Constraint("paretoOptConstraint", paretoBrancher))
    paretoBrancher
  }
}
