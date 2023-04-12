package idesyde.choco

import idesyde.core.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution

trait ChocoExplorable[T <: DecisionModel] {

  def buildChocoModel(m: T, timeResolution: Long = -1L, memoryResolution: Long = -1L): Model

  def rebuildDecisionModel(
      m: T,
      solution: Solution,
      timeResolution: Long = -1L,
      memoryResolution: Long = -1L
  ): T
}

object ChocoExplorableOps {
  extension [T <: DecisionModel](m: T)
    def chocoModel(timeResolution: Long = -1L, memoryResolution: Long = -1L)(using
        exp: ChocoExplorable[T]
    ) = exp.buildChocoModel(m, timeResolution, memoryResolution)
  extension [T <: DecisionModel](m: T)
    def mergeSolution(sol: Solution, timeResolution: Long = -1L, memoryResolution: Long = -1L)(using
        exp: ChocoExplorable[T]
    ) =
      exp.rebuildDecisionModel(m, sol, timeResolution, memoryResolution)
}
