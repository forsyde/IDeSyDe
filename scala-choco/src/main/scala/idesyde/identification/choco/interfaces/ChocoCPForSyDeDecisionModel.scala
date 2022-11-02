package idesyde.identification.choco.interfaces

import forsyde.io.java.core.ForSyDeSystemGraph
import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.Variable
import org.chocosolver.solver.Solution
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import idesyde.identification.forsyde.ForSyDeDecisionModel
import idesyde.identification.DecisionModel

trait ChocoCPForSyDeDecisionModel extends DecisionModel:

  def chocoModel: Model

  def shouldRestartOnSolution: Boolean = true

  def shouldLearnSignedClauses: Boolean = true

  def rebuildFromChocoOutput(output: Solution): DecisionModel

  /** Due to how the choco solver treats multi objective optimization, the objective _have_ to be
    * all maximization goals! Consider using the minus of a variable in case the original objective
    * is a minimization goal.
    *
    * Like, val maxObjVar = chocoModel.intMinusView(minObjVar)
    */
  def modelMinimizationObjectives: Array[IntVar] = Array.empty

  def strategies: Array[AbstractStrategy[? <: Variable]] = Array.empty

end ChocoCPForSyDeDecisionModel
