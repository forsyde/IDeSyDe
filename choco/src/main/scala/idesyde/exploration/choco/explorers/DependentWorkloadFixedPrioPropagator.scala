package idesyde.exploration.explorers.choco

import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.solver.variables.events.IntEventType
import org.chocosolver.solver.exception.ContradictionException
import org.chocosolver.util.ESat

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*

class DependentWorkloadFixedPrioPropagator(
    val taskPrios: Array[Int],
    val schedulingPoints: Array[Int],
    val taskPeriods: Array[Int],
    var taskExecutions: Array[IntVar],
    var taskBlocking: Array[IntVar],
    var taskResponses: Array[IntVar],
    var taskDurations: Array[IntVar]
                                          ) {
//) {extends Propagator[IntVar](
//  (taskExecutions ++ taskResponses ++ taskBlocking ++ taskDurations),
//  PropagatorPriority.QUADRATIC,
//  false) {
//
//   override def getPropagationConditions(vIdx: Int): Int = {
//     return IntEventType.combine(IntEventType.INSTANTIATE, IntEventType.BOUND);
//   }
//
//   @throws(classOf[ContradictionException])
//   override def propagate(evtmask: Int) = {
//     for ((pi, i) <- taskExecutions.zipWithIndex) {
//       val minR = schedulingPoints
//         .map(t => {
//           taskBlocking(i).getUB + taskDurations(i).getUB + taskExecutions.zipWithIndex
//             .filter((pj, j) => pj.getUB <= pi.getLB || pi.getUB <= pj.getLB)
//             .map((pj, j) => taskDurations(j).getUB * (Math.floorDiv(t, taskPeriods(j)) + 1))
//             .sum
//         })
//         .min
//       if (minR < taskResponses(i).getUB) then taskResponses(i).updateUpperBound(minR, this)
//     }
//   }
//
//   override def isEntailed(): ESat = {
//     if (taskResponses.forall(r => r.getLB == r.getUB)) then return ESat.TRUE
//     else ESat.UNDEFINED
//   }

}
