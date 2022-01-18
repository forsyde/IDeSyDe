import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import org.chocosolver.solver.Model
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.Variable

trait ChocoCPDecisionModel extends DecisionModel:

    def chocoModel: Model

    def rebuildFromChocoOutput(output: Model, originalModel: ForSyDeSystemGraph): ForSyDeSystemGraph

end ChocoCPDecisionModel
