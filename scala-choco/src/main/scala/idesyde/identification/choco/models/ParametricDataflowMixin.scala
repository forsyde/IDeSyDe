import org.chocosolver.solver.variables.IntVar
import org.jgrapht.Graph
import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.util.ESat
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.solver.constraints.Constraint
import org.jgrapht.traverse.BreadthFirstIterator
import org.jgrapht.traverse.DepthFirstIterator


trait ParametricDataflowMixin extends ChocoModelMixin {
  
    def stateSpace: Graph[Int, Int]
    def allowsSelfConcurrency(task: Int): Boolean

    def executionTimes: Array[Array[IntVar]]
    def mappings: Array[Array[BoolVar]]
    def minimumLatency: Array[IntVar]
    def maximumLatency: Array[IntVar]

    def postParametricDataflowSelfTimedLatencyConstraints() = {
        val propagator = ParametricDataflowSelfTimedLatencyPropagator(
            stateSpace,
            (0 until executionTimes.length).map(allowsSelfConcurrency(_)).toArray,
            executionTimes,
            mappings,
            minimumLatency,
            maximumLatency)
        chocoModel.post(Constraint("globalParametricDataflowLatencyConstraint", propagator))
    }

    
    class ParametricDataflowSelfTimedLatencyPropagator(
        val stateSpace: Graph[Int, Int],
        val allowsSelfConcurrency: Array[Boolean],
        val executionTimes: Array[Array[IntVar]],
        val mappings: Array[Array[BoolVar]],
        val minimumLatency: Array[IntVar],
        val maximumLatency: Array[IntVar]
    ) extends Propagator[IntVar](executionTimes.flatten ++ mappings.flatten ++ minimumLatency ++ maximumLatency, 
        if (stateSpace.vertexSet.size <= (mappings.size ^ 2)) then PropagatorPriority.QUADRATIC
        else if (stateSpace.vertexSet.size <= (mappings.size ^ 3)) then PropagatorPriority.CUBIC
        else PropagatorPriority.VERY_SLOW
    , false) {

        val numProcessors = mappings.head.length

        val pessimiticTimelinePerState = (0 until stateSpace.vertexSet.size).map(s => Array.fill(numProcessors)(0)).toArray
        val optimisticTimelinePerState = (0 until stateSpace.vertexSet.size).map(s => Array.fill(numProcessors)(0)).toArray

        val jobMapping = (0 until stateSpace.vertexSet.size).map(s => Array.fill(numProcessors)(0)).toArray

        def isEntailed(): ESat = if (minimumLatency.forall(_.isInstantiated) && maximumLatency.forall(_.isInstantiated)) ESat.TRUE else ESat.UNDEFINED
        def propagate(eventMask: Int): Unit = {
            val stateIter = DepthFirstIterator(stateSpace, 0)
            while (stateIter.hasNext) {
                val state = stateIter.next
                // val task = stateSpace.getEdge(stateIter.getParent(state), state)
                // val bestOption = optimisticTimelinePerState(state).zipWithIndex.minBy((t, pe) => t)
            }
        }

    }

}
