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
    val jobOrdering: (Int) => IntVar,
    val mapping: (Int) => IntVar,
    val transmissionDuration: (Int) => (Int) => IntVar,
    val succeeds: (Int) => (Int) => Boolean
) extends Propagator[IntVar](
      jobs.map(_.getStart()) ++ jobs.zipWithIndex.map((_, i) => jobOrdering(i)) ++ jobs.zipWithIndex.map((_, i) => mapping(i)),
      PropagatorPriority.QUADRATIC,
      false
    ) {

  private val numJobs = jobs.size

  def propagate(evtmask: Int): Unit = wfor(0, _ < numJobs, _ + 1) { i =>
    var lb = jobs(i).getStart().getLB()
    var ub = jobs(i).getEnd().getUB()
    wfor(0, _ < numJobs, _ + 1) { j =>
      if (i != j && mapping(i).isInstantiated() && mapping(j).isInstantiated() && mapping(i).getValue() == mapping(j).getValue()) {
        if (jobOrdering(j).getUB() + 1 <= jobOrdering(i).getLB()) {
          lb = Math.max(lb, jobs(j).getEnd().getLB())
        }
        if (jobOrdering(i).getUB() + 1 <= jobOrdering(j).getLB()) {
          ub = Math.min(ub, jobs(j).getStart().getLB())
        }
        // else if (succeeds(j)(i)) {
        //   lb = Math.max(jobs(j).getEnd().getLB() + transmissionDuration(j)(i).getLB(), lb)
        //   ub = Math.max(jobs(j).getEnd().getUB() + transmissionDuration(j)(i).getUB(), ub)
        // }
      }
    }
    jobs(i).getStart().updateLowerBound(lb, this)
    jobs(i).getEnd().updateUpperBound(ub, this)
  }

  def isEntailed(): ESat =
    if (jobs.forall(_.getStart().isInstantiated())) ESat.TRUE else ESat.UNDEFINED
}
