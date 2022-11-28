package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import idesyde.utils.CoreUtils.wfor
import cats.kernel.instances.order

class OrderedJobUpperBoundPropagator(
    val mapping: Array[IntVar],
    val ordering: Array[IntVar],
    val startTimes: Array[IntVar],
    val durations: Array[IntVar]
) extends Propagator[IntVar](
      mapping ++ startTimes ++ durations,
      PropagatorPriority.QUADRATIC,
      false
    ) {

  private val numJobs = startTimes.size

  def propagate(evtmask: Int): Unit = wfor(0, _ < numJobs, _ + 1) { i =>
    if (mapping(i).isInstantiated()) {
      var maxStart = startTimes(i).getLB()
      wfor(0, _ < numJobs, _ + 1) { j =>
        if (i != j && mapping(j).contains(mapping(i).getValue())) {
          val jMaxTime = startTimes(j).getUB() + durations(j).getUB()
          if (ordering(j).getLB() <= ordering(i).getUB() && maxStart < jMaxTime) {
            maxStart = jMaxTime
          }
        }
        if (i != j && mapping(j).isInstantiatedTo(mapping(i).getValue())) {
          if (ordering(j).getUB() < ordering(i).getLB()) {
            startTimes(i).updateLowerBound(startTimes(j).getLB() + durations(j).getLB(), this)
          }
        }
      }
      if (maxStart < startTimes(i).getLB()) {
        maxStart = startTimes(i).getLB()
      }
      startTimes(i).updateUpperBound(maxStart, this)
    }
  }

  def isEntailed(): ESat =
    if (startTimes.forall(_.isInstantiated())) ESat.TRUE else ESat.UNDEFINED
}
