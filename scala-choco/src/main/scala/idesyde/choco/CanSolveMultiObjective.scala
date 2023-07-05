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
      objsUpperBounds: Vector[Vector[Int]]
  ): Unit = {
    if (objs.size == 1) {
      m.setObjective(
        false,
        objs.head
      )
    }
    // val paretoBrancher = ParetoMaximizer(objs.map(m.intMinusView(_)))
    // val paretoBrancher = ParetoMinimizationBrancher(objs)
    // m.getSolver().plugMonitor(paretoBrancher)
    // val constraint = new Constraint("paretoOptConstraint", paretoBrancher)
    // m.post(constraint)
    // keep only domninant solutions
    // println("got " + objsUpperBounds.map(_.mkString(", ")).mkString("\n"))
    val dominantUpperBounds = objsUpperBounds.filterNot(o1 =>
      objsUpperBounds
        .filter(o2 => o2 != o1)
        .exists(o2 => o2.zipWithIndex.forall((ov2, i) => ov2 <= o1(i)))
    )
    // println("using " + dominantUpperBounds.map(_.mkString(", ")).mkString("\n"))
    // pareto constraint
    if (dominantUpperBounds.length > 0) {
      m
        .and(
          dominantUpperBounds.map(ov =>
            m.or(ov.zipWithIndex.map((o, i) => m.arithm(objs(i), "<", o)): _*)
          ): _*
        )
        .post()
    }
    // constraint
  }
}
