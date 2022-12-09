package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import idesyde.utils.CoreUtils.wfor
import cats.kernel.instances.order
import org.chocosolver.solver.variables.Task

class SDFJobPropagator(
    val jobs: Array[Task],
    val mapping: (Int) => IntVar,
    val transmissionDuration: (Int) => (Int) => IntVar,
    val succeeds: (Int) => (Int) => Boolean
) extends Propagator[IntVar](
      jobs.map(_.getStart()),
      PropagatorPriority.QUADRATIC,
      false
    ) {

  private val numJobs = jobs.size

  def propagate(evtmask: Int): Unit = wfor(0, _ < numJobs, _ + 1) { i =>
    // var ub = 0
    var trivial = true
    wfor(0, _ < numJobs, _ + 1) { j =>
      val coMappable = mapping(i).stream().anyMatch(mapping(j).contains(_))
      if (i != j && coMappable) {
        // can never clash
        trivial = trivial && jobs(i).getEnd().getLB() <= jobs(j).getStart().getLB()
      }
    // else if (succeeds(i)(j)) {
    //   ub = Math.max(jobs(j).getEnd().getLB() + transmissionDuration(j)(i).getLB(), ub)
    // }
    }
    if (trivial) {
      jobs(i).getStart().updateUpperBound(jobs(i).getStart().getLB(), this)
    }
  // println(i + " -> " + ub)
  // jobs(i).getStart().updateUpperBound(ub, this)
  }

  def isEntailed(): ESat =
    if (jobs.forall(_.getStart().isInstantiated())) ESat.TRUE else ESat.UNDEFINED
}
