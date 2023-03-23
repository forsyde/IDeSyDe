package idesyde.identification.choco

import idesyde.identification.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.Solution
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable

/**
  * As the name implies, this is the basic trait which extends the core [[idesyde.identification.DecisionMmodel]]
  * for a minimal interface with the choco solver. From this trait, CP models can be built so that the choco solver
  * can solve it.
  *   
  * @param shouldRestartOnSolution
  *   Instructs that the solving process should start on solutions
  * @param shouldLearnSignedClauses
  *   Instructs that the solving proces should use learned clauses
  */
trait ChocoDecisionModel(
  val shouldRestartOnSolution: Boolean = true,
  val shouldLearnSignedClauses: Boolean = true
) extends DecisionModel {
  
  def chocoModel: Model

  def rebuildFromChocoOutput(output: Solution): Set[DecisionModel]

  /** Due to how the choco solver treats multi objective optimization, the objective _have_ to be
    * all maximization goals! Consider using the minus of a variable in case the original objective
    * is a minimization goal.
    *
    * Like, val maxObjVar = chocoModel.intMinusView(minObjVar)
    */
  def modelMinimizationObjectives: Array[IntVar] = Array.empty

  def strategies: Array[AbstractStrategy[? <: Variable]] = Array.empty
}
