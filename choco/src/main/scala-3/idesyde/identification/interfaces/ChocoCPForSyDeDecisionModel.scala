package idesyde.identification.interfaces

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.ForSyDeDecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.Variable
import org.chocosolver.solver.Solution
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy

trait ChocoCPForSyDeDecisionModel extends ForSyDeDecisionModel with ChocoModelMixin:

  def chocoModel: Model

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph

end ChocoCPForSyDeDecisionModel
