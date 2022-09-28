package idesyde.identification.choco.models.workload

import org.chocosolver.util.ESat
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.PropagatorPriority

class FixedPriorityPreemptivePropagator[T](
    val schedulerIdx: Int,
    val priorities: Array[Int],
    val periods: Array[T],
    val deadlines: Array[T],
    val wcets: Array[Array[T]],
    val taskExecution: Array[IntVar],
    val responseTimes: Array[IntVar],
    val blockingTimes: Array[IntVar],
    val durations: Array[Array[IntVar]]
)(using Conversion[T, Int])
    extends Propagator[IntVar](
      taskExecution ++ responseTimes ++ blockingTimes ++ durations.flatten,
      PropagatorPriority.VERY_SLOW,
      false
    ) {

  def isEntailed(): org.chocosolver.util.ESat = {
    if (
      responseTimes.zipWithIndex
        .forall((r, i) => r.isInstantiated() && r.getUB() <= deadlines(i))
    ) return ESat.TRUE;
    else if (
      responseTimes.zipWithIndex
        .exists((r, i) => r.getLB() > deadlines(i))
    )
      return ESat.FALSE
    else return ESat.UNDEFINED;
  }

  def ceil(numerand: Int, dividend: Int): Int = {
    if (numerand % dividend > 0) then (numerand / dividend) + 1
    else numerand / dividend
  }

  def dur(taskIdx: Int) = Math.max(
    durations(taskIdx)(schedulerIdx).getLB(),
    wcets(taskIdx)(schedulerIdx)
  )

  def propagate(x$0: Int): Unit =
    taskExecution.zipWithIndex
      .filter((pi, i) => pi.contains(schedulerIdx))
      .foreach((pi, i) => {
        var rtL     = -1
        var rtLNext = blockingTimes(i).getLB() + dur(i)
        while (rtL != rtLNext && rtL < responseTimes(i).getUB()) {
          rtL = rtLNext
          rtLNext = blockingTimes(i).getLB() + dur(i) +
            taskExecution.zipWithIndex
              .filter((_, j) => i != j && priorities(j) >= priorities(i))
              .filter((pj, j) => pj.isInstantiatedTo(schedulerIdx))
              .map((_, j) => dur(j) * ceil(rtL, periods(j)))
              .sum
        }
        // println(s"RT of ${i} in ${schedulerIdx} prop: ${rtL} | ${deadlines(i)}")
        if (pi.isInstantiatedTo(schedulerIdx)) {
          responseTimes(schedulerIdx).updateLowerBound(rtL, this)
        } else if (rtL > deadlines(i)) {
          pi.removeValue(schedulerIdx, this)
        }
        //if (rtU < responseTimes(i).getUB()) responseTimes(i).updateUpperBound(rtU, this)
      })

}
