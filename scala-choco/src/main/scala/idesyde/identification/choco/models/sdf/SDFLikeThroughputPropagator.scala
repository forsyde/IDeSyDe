package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.util.ESat
import idesyde.utils.CoreUtils.wfor
import org.chocosolver.solver.constraints.PropagatorPriority

class SDFLikeThroughputPropagator(
    val firingsInSlots: Array[Array[Array[IntVar]]],
    val startTimes: Array[Array[IntVar]],
    val finishTimes: Array[Array[IntVar]],
    val invThroughputs: Array[IntVar],
    val globalInvThroughput: IntVar
) extends Propagator[IntVar](
      firingsInSlots.flatten.flatten ++ startTimes.flatten ++ finishTimes.flatten ++ invThroughputs :+ globalInvThroughput,
      PropagatorPriority.CUBIC,
      false
    ) {

  private val numSchedulers = invThroughputs.size
  private val numActors     = firingsInSlots.size
  private val numSlots      = startTimes.head.size
  private val slots         = 0 until numSlots

  def propagate(evtmask: Int): Unit = {
    var maxTh = 0
    wfor(0, _ < numSchedulers, _ + 1) { p =>
      val latestFinish  = finishTimes(p).last.getLB()
      var earliestStart = 0
      var searching     = true
      wfor(0, searching && _ < numSlots, _ + 1) { s =>
        wfor(0, searching && _ < numActors, _ + 1) { a =>
          if (firingsInSlots(a)(p)(s).isInstantiated() && firingsInSlots(a)(p)(s).getLB() > 0) {
            earliestStart = startTimes(p)(s).getLB()
            searching = false
          }
        }
      }
      // println(s"$p: $earliestStart -> $latestFinish")
      invThroughputs(p).updateLowerBound(latestFinish - earliestStart, this)
      if (invThroughputs(p).getLB() > maxTh) then maxTh = invThroughputs(p).getLB()
    }
    globalInvThroughput.updateLowerBound(maxTh, this)
  }

  def isEntailed(): ESat = {
    var decided = true
    wfor(0, _ < numSchedulers && decided, _ + 1) { p =>
      decided = decided && invThroughputs(p).isInstantiated()
    }
    decided = decided && globalInvThroughput.isInstantiated()
    if (decided) then ESat.TRUE else ESat.UNDEFINED
  }

}
