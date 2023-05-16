package idesyde.choco

import idesyde.core.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import org.chocosolver.solver.variables.IntVar

trait ChocoExplorable[T <: DecisionModel] {

  def buildChocoModel(
      m: T,
      timeResolution: Long = -1L,
      memoryResolution: Long = -1L,
      objsUpperBounds: Vector[Vector[Int]] = Vector.empty
  ): (Model, Vector[IntVar])

  def rebuildDecisionModel(
      m: T,
      solution: Solution,
      timeResolution: Long = -1L,
      memoryResolution: Long = -1L
  ): T
}

object ChocoExplorableOps {
  extension [T <: DecisionModel](m: T)
    def chocoModel(
        timeResolution: Long = -1L,
        memoryResolution: Long = -1L,
        objsUpperBounds: Vector[Vector[Int]] = Vector.empty
    )(using
        exp: ChocoExplorable[T]
    ) = exp.buildChocoModel(m, timeResolution, memoryResolution, objsUpperBounds)
  extension [T <: DecisionModel](m: T)
    def mergeSolution(sol: Solution, timeResolution: Long = -1L, memoryResolution: Long = -1L)(using
        exp: ChocoExplorable[T]
    ) =
      exp.rebuildDecisionModel(m, sol, timeResolution, memoryResolution)
}
