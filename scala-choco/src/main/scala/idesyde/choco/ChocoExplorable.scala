package idesyde.choco

import idesyde.core.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import org.chocosolver.solver.variables.IntVar
import idesyde.core.Explorer
import idesyde.core.ExplorationSolution

trait ChocoExplorable[T <: DecisionModel] {

  def buildChocoModel(
      m: T,
      previousSolutions: Set[ExplorationSolution],
      configuration: Explorer.Configuration
  ): (Model, Map[String, IntVar])

  def rebuildDecisionModel(
      m: T,
      solution: Solution,
      configuration: Explorer.Configuration
  ): ExplorationSolution
}

object ChocoExplorableOps {
  extension [T <: DecisionModel](m: T)
    def chocoModel(
        previousSolutions: Set[ExplorationSolution],
        configuration: Explorer.Configuration
    )(using
        exp: ChocoExplorable[T]
    ) = exp.buildChocoModel(m, previousSolutions, configuration)
  extension [T <: DecisionModel](m: T)
    def mergeSolution(sol: Solution, configuration: Explorer.Configuration)(using
        exp: ChocoExplorable[T]
    ) =
      exp.rebuildDecisionModel(m, sol, configuration)
}
