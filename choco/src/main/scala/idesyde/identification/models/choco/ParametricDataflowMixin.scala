import org.chocosolver.solver.variables.IntVar
import org.jgrapht.Graph
import idesyde.identification.interfaces.ChocoModelMixin


trait ParametricDataflowMixin extends ChocoModelMixin {
  
    def executionTimes: Array[IntVar]
    def mappings: Array[IntVar]
    def stateSpace: Graph[Int, Int]

}
