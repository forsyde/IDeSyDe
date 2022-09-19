package idesyde.identification.choco.interfaces

import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable

trait ChocoModelMixin {
  
  def chocoModel: Model

  /**
   * Due to how the choco solver treats multi objective optimization, the objective
   * _have_ to be all maximization goals! Consider using the minus of a variable in
   * case the original objective is a minimization goal.
   * 
   * Like,
   *  val maxObjVar = chocoModel.intMinusView(minObjVar)
   */
  def modelMinimizationObjectives: Array[IntVar] = Array.empty

  def strategies: Array[AbstractStrategy[? <: Variable]] = Array.empty

}
