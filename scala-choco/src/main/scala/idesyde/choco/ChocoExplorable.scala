package idesyde.choco

import idesyde.core.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import org.chocosolver.solver.variables.IntVar
import idesyde.core.ExplorerConfiguration

trait ChocoExplorable[T <: DecisionModel] {

  def buildChocoModel(
      m: T,
      previousSolutions: Set[(T, Map[String, Double])],
      configuration: ExplorerConfiguration
  ): (Model, Map[String, IntVar])

  def rebuildDecisionModel(
      m: T,
      solution: Solution,
      configuration: ExplorerConfiguration
  ): (T, Map[String, Double])
}

object ChocoExplorableOps {
  extension [T <: DecisionModel](m: T)
    def chocoModel(
        previousSolutions: Set[(T, Map[String, Double])],
        configuration: ExplorerConfiguration
    )(using
        exp: ChocoExplorable[T]
    ) = exp.buildChocoModel(m, previousSolutions, configuration)
  extension [T <: DecisionModel](m: T)
    def mergeSolution(sol: Solution, configuration: ExplorerConfiguration)(using
        exp: ChocoExplorable[T]
    ) =
      exp.rebuildDecisionModel(m, sol, configuration)
}
