package idesyde.choco

import idesyde.core.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import org.chocosolver.solver.variables.IntVar

trait ChocoExplorable[T <: DecisionModel] {

  def buildChocoModel(
      m: T,
      previousSolutions: Set[(T, Map[String, Double])] = Set(),
      timeResolution: Long = -1L,
      memoryResolution: Long = -1L
  ): (Model, Map[String, IntVar])

  def rebuildDecisionModel(
      m: T,
      solution: Solution,
      timeResolution: Long = -1L,
      memoryResolution: Long = -1L
  ): (T, Map[String, Double])
}

object ChocoExplorableOps {
  extension [T <: DecisionModel](m: T)
    def chocoModel(
        previousSolutions: Set[(T, Map[String, Double])] = Set(),
        timeResolution: Long = -1L,
        memoryResolution: Long = -1L
    )(using
        exp: ChocoExplorable[T]
    ) = exp.buildChocoModel(m, previousSolutions, timeResolution, memoryResolution)
  extension [T <: DecisionModel](m: T)
    def mergeSolution(sol: Solution, timeResolution: Long = -1L, memoryResolution: Long = -1L)(using
        exp: ChocoExplorable[T]
    ) =
      exp.rebuildDecisionModel(m, sol, timeResolution, memoryResolution)
}
