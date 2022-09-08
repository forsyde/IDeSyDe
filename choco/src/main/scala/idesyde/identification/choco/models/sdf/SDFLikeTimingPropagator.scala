package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import idesyde.utils.CoreUtils.wfor
import org.chocosolver.solver.constraints.PropagatorPriority
import breeze.linalg._
import org.chocosolver.util.ESat

class SDFLikeTimingPropagator(
    val actorDuration: Array[Array[Int]],
    val balanceMatrix: Array[Array[Int]],
    val initialTokens: Array[Int],
    val firingsInSlots: Array[Array[Array[IntVar]]],
    val channelsTravelTime: Array[Array[Array[IntVar]]],
    val channelsCommunicate: Array[Array[Array[BoolVar]]],
    val startTimes: Array[Array[IntVar]],
    val finishTimes: Array[Array[IntVar]]
) extends Propagator[IntVar](
      firingsInSlots.flatten.flatten ++ channelsCommunicate.flatten.flatten ++ startTimes.flatten ++ finishTimes.flatten,
      PropagatorPriority.TERNARY,
      false
    ) {

  private val recomputeMethods = SDFChocoRecomputeMethods(firingsInSlots)

  private val numSchedulers = startTimes.size
  private val numActors     = firingsInSlots.size
  private val numSlots      = startTimes.head.size
  private val numChannels   = channelsCommunicate.size

  private val tokensBefore =
    DenseVector(initialTokens) +: Array.fill(numSlots - 1)(DenseVector.zeros[Int](numChannels))
  // def tokens(slot: Int) = if (slot < 0) then tokens0 else tokensBefore(slot)
  private val tokensAfter  = Array.fill(numSlots)(DenseVector.zeros[Int](numChannels))
  private val mat          = DenseMatrix(balanceMatrix: _*)
  private val consMat      = mat.map(v => if (v < 0) then v else 0)
  private val prodMat      = mat.map(v => if (v > 0) then v else 0)
  private val firingVector = Array.fill(numSlots)(DenseVector.zeros[Int](numActors))
  private val firingVectorPerCore =
    Array.fill(numSchedulers)(Array.fill(numSlots)(DenseVector.zeros[Int](numActors)))

  def slotOccupied(p: Int)(s: Int): Boolean = {
    var occupied = false
    wfor(0, _ < numActors && occupied, _ + 1) { a =>
      occupied = firingsInSlots(a)(p)(s).isInstantiated() && firingsInSlots(a)(p)(s).getLB() > 0
    }
    occupied
  }

  // def communicationDelay(slot: Int)(srcSched: Int)(dstSched: Int): Int = if (slot > 0) {
  //   wfor(0, _ < numChannels, _ + 1) {c =>
  //     transmitted(c) = 0
  //   }
  //   wfor(0, _ < numActors, _ + 1) {a =>
  //     wfor(0, _ < numActors, _ + 1) {aa =>
  //       if (firingsInSlots(a)(srcSched)(slot - 1).isInstantiated() && firingsInSlots(a)(srcSched)(slot - 1).getLB() > 0) {
  //         if (firingsInSlots(aa)(dstSched)(slot).isInstantiated() && firingsInSlots(aa)(dstSched)(slot).getLB() > 0) {
  //           wfor(0, _ < numChannels, _ + 1) {c =>
  //             if (balanceMatrix(c)(aa) * firingsInSlots()
  //           }
  //         }
  //       }
  //     }

  //       wfor(0, _ < numChannels, _ + 1) {c =>

  //       }
  //     }

  //       wfor(0, _ < numChannels, _ + 1) {c =>
  //       }

  //     }
  //   }
  //   summed
  // } else {
  //   0
  // }

  def propagate(evtmask: Int): Unit = {
    recomputeMethods.recomputeFiringVectors(
      firingVector,
      firingVectorPerCore
    )
    recomputeMethods.recomputeTokens(mat)(firingVector, firingVectorPerCore)(
      tokensBefore,
      tokensAfter
    )
    var duration = 0
    wfor(0, _ < numSchedulers, _ + 1) { p =>
      startTimes(p)(0).updateLowerBound(0, this)
      var duration = 0
      wfor(0, _ < numActors, _ + 1) { a =>
        duration += firingsInSlots(a)(p)(0).getLB() * actorDuration(a)(p)
      }
      finishTimes(p)(0).updateLowerBound(startTimes(p)(0).getLB() + duration, this)
    }
    // work based first on the slots and update the timing whenever possible
    wfor(1, _ < numSlots, _ + 1) { i =>
      // println(s"at ${i} : ${firingVector(i)}")
      // now for timing
      wfor(0, _ < numSchedulers, _ + 1) { p =>
        var maxStartTime = finishTimes(p)(i - 1).getLB()
        wfor(0, _ < numSchedulers, _ + 1) { pp =>
          if (pp != p && slotOccupied(p)(i) && slotOccupied(pp)(i - 1)) {
            var commRecvTime = 0
            val diffAtP = recomputeMethods.diffTokenVec(consMat)(prodMat)(
              firingVectorPerCore(i)(p)
            )(firingVectorPerCore(i - 1)(p))
            val diffAtPP = recomputeMethods.diffTokenVec(consMat)(prodMat)(
              firingVectorPerCore(i)(pp)
            )(firingVectorPerCore(i - 1)(pp))
            wfor(0, _ < numChannels, _ + 1) { c =>
              if (diffAtP(c) < 0 && diffAtPP(c) >= -diffAtP(c)) {
                commRecvTime =
                  Math.max(commRecvTime, -channelsTravelTime(c)(pp)(p).getLB() * diffAtP(c))
              }
            }
            maxStartTime = Math.max(maxStartTime, finishTimes(pp)(i - 1).getLB() + commRecvTime)
          }
        }
        startTimes(p)(i).updateLowerBound(maxStartTime, this)
        var duration = 0
        wfor(0, _ < numActors, _ + 1) { a =>
          duration += firingVectorPerCore(i)(p)(a) * actorDuration(a)(p)
        }
        finishTimes(p)(i).updateLowerBound(startTimes(p)(i).getLB() + duration, this)
      }
    }
  }

  def isEntailed(): ESat = {
    wfor(0, _ < numSchedulers, _ + 1) { p =>
      wfor(0, _ < numSlots, _ + 1) { s =>
        if (!startTimes(p)(s).isInstantiated() || !finishTimes(p)(s).isInstantiated()) {
          return ESat.UNDEFINED
        }
      }
    }
    ESat.TRUE
  }

}
