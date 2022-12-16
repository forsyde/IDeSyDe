package idesyde.identification.choco.models.workload

import org.chocosolver.util.ESat
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.PropagatorPriority
import idesyde.utils.CoreUtils.wfor

class FixedPriorityPreemptivePropagator[T](
    val schedulerIdx: Int,
    val priorities: Array[Int],
    val periods: Array[T],
    val deadlines: Array[T],
    val wcets: Array[Array[T]],
    val taskExecution: Array[IntVar],
    val responseTimes: Array[IntVar],
    val blockingTimes: Array[IntVar],
    val durations: Array[IntVar]
)(using Conversion[T, Int])
    extends Propagator[IntVar](
      taskExecution ++ responseTimes ++ blockingTimes ++ durations,
      PropagatorPriority.BINARY,
      false
    ) {

  private val numTasks = taskExecution.size

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

  def dur(taskIdx: Int) = durations(taskIdx).getLB()

  def propagate(x$0: Int): Unit = {
    wfor(0, _ < numTasks, _ + 1) { i =>
      if (taskExecution(i).contains(schedulerIdx)) {
        var rtNext = blockingTimes(i).getLB() + dur(i)
        wfor(0, _ < numTasks, _ + 1) { j =>
          if (
            i != j && priorities(j) >= priorities(i) && taskExecution(j).isInstantiatedTo(
              schedulerIdx
            )
          ) {
            rtNext += dur(j) * ceil(
              responseTimes(i).getLB() + responseTimes(j).getLB() - dur(j),
              periods(j)
            )
          }
        }
        if (taskExecution(i).isInstantiatedTo(schedulerIdx)) {
          responseTimes(i).updateLowerBound(rtNext, this)
        } else if (rtNext > deadlines(i)) {
          taskExecution(i).removeValue(schedulerIdx, this)
        }
      }
    }
  }
  // taskExecution.zipWithIndex
  //   .filter((pi, i) => pi.contains(schedulerIdx))
  //   .foreach((pi, i) => {
  //     var rtL     = -1
  //     var rtLNext = blockingTimes(i).getLB() + dur(i)
  //     while (rtL != rtLNext && rtL < responseTimes(i).getUB()) {
  //       rtL = rtLNext
  //       rtLNext = blockingTimes(i).getLB() + dur(i) +
  //         taskExecution.zipWithIndex
  //           .filter((_, j) => i != j && priorities(j) >= priorities(i))
  //           .filter((pj, j) => pj.isInstantiatedTo(schedulerIdx))
  //           .map((_, j) => dur(j) * ceil(rtL, periods(j)))
  //           .sum
  //     }
  //     // println(s"RT of ${i} in ${schedulerIdx} prop: ${rtL} | ${deadlines(i)}")
  //     if (pi.isInstantiatedTo(schedulerIdx)) {
  //       responseTimes(schedulerIdx).updateLowerBound(rtL, this)
  //     } else if (rtL > deadlines(i)) {
  //       pi.removeValue(schedulerIdx, this)
  //     }
  //     //if (rtU < responseTimes(i).getUB()) responseTimes(i).updateUpperBound(rtU, this)
  //   })

}
