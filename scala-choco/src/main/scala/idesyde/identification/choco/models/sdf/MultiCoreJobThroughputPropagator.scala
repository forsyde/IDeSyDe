package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import idesyde.utils.CoreUtils.wfor

class MultiCoreJobThroughputPropagator(
    val mapping: Array[IntVar],
    val startTimes: Array[IntVar],
    val durations: Array[IntVar],
    val coreInvThroughputs: Array[IntVar]
) extends Propagator[IntVar](
      mapping ++ startTimes ++ coreInvThroughputs,
      PropagatorPriority.CUBIC,
      false
    ) {

  private val numJobs  = mapping.size
  private val numCores = coreInvThroughputs.size

  def propagate(evtmask: Int): Unit = {
    wfor(0, _ < numCores, _ + 1) { p =>
      var lb = coreInvThroughputs(p).getLB()
      //   var ub = 0
      wfor(0, _ < numJobs, _ + 1) { i =>
        wfor(0, _ < numJobs, _ + 1) { j =>
          if (
            mapping(j).isInstantiatedTo(p) && mapping(i)
              .isInstantiatedTo(p) && lb < startTimes(j).getLB() + durations(j)
              .getLB() - startTimes(i).getUB()
          ) {
            // println(s"${i} to ${j}: ${startTimes(j).getLB()} + ${durations(j).getLB()} - ${startTimes(i).getUB()}")
            lb = startTimes(j).getLB() + durations(j).getLB() - startTimes(i).getUB()
          }
        //   if (
        //     mapping(j).contains(p) && mapping(i)
        //       .contains(p) && ub < startTimes(j).getUB() + durations(j)
        //       .getUB() - startTimes(i).getLB()
        //   ) {
        //     ub = startTimes(j).getUB() + durations(j)
        //       .getUB() - startTimes(i).getLB()
        //   }
        }
      }
      // println(lb)
      coreInvThroughputs(p).updateLowerBound(lb, this)
    //   if (ub < coreInvThroughputs(p).getUB()) {
    //     coreInvThroughputs(p).updateUpperBound(ub, this)
    //   }
    }
  }

  def isEntailed(): ESat =
    if (coreInvThroughputs.forall(_.isInstantiated())) ESat.TRUE else ESat.UNDEFINED

}
