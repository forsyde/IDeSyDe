package idesyde.choco

import idesyde.core.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution

trait ChocoExplorable[T <: DecisionModel] {

  def buildChocoModel(m: T): Model

  def rebuildDecisionModel(m: T, solution: Solution): T
}

object ChocoExplorableOps {
  extension [T <: DecisionModel](m: T)
    def chocoModel(using exp: ChocoExplorable[T]) = exp.buildChocoModel(m)
  extension [T <: DecisionModel](m: T)
    def mergeSolution(sol: Solution)(using exp: ChocoExplorable[T]) =
      exp.rebuildDecisionModel(m, sol)
}
