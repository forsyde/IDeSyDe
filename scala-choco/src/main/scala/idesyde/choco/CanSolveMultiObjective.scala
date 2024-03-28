package idesyde.choco

import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import idesyde.exploration.choco.explorers.ParetoMinimizationBrancher
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.objective.ParetoMaximizer

trait CanSolveMultiObjective {

  def createAndApplyMOOPropagator(
      m: Model,
      objs: Array[IntVar],
      objectivesUpperLimits: Set[Map[String, Int]]
  ): Unit = {
    if (objs.size == 1) {
      m.setObjective(
        false,
        objs.head
      )
    }
    // keep only domninant solutions
    val dominantUpperBounds = objectivesUpperLimits.filterNot(o1 =>
    objectivesUpperLimits
        .filter(o2 => o2 != o1)
        .exists(o2 => o2.forall((k2, v2) => !o1.contains(k2) || v2 <= o1(k2)))
    )
    // pareto constraint
    if (dominantUpperBounds.size > 0) {
        for (objMap <- dominantUpperBounds) {
            m.or(
            objMap
                .flatMap((name, v) =>
                objs.find(_.getName().equals(name)).map(objVar => m.arithm(objVar, "<", v))
                )
                .toSeq*
            ).post()
        }
    }
  }
}
