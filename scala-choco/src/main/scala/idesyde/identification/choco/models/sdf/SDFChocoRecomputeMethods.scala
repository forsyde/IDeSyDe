package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.variables.IntVar
import idesyde.utils.CoreUtils.wfor
import breeze.linalg._
import breeze.math.Field
import breeze.linalg.support.CanZipMapValues
import org.chocosolver.solver.variables.BoolVar

class SDFChocoRecomputeMethods(
    val firingsInSlots: Array[Array[Array[BoolVar]]]
) {

  private val numActors     = firingsInSlots.size
  private val numSchedulers = firingsInSlots.head.size
  private val numSlots      = firingsInSlots.head.head.size
  // def maxFiringsPerActor: Array[Int]
  // def balanceMatrix: Array[Array[Int]]
  // def initialTokens: Array[Int]
  // def actorDuration: Array[Array[Int]]
  // def channelsTravelTime: Array[Array[Array[IntVar]]]
  // def firingsInSlots: Array[Array[Array[IntVar]]]
  // def initialLatencies: Array[IntVar]
  // def slotMaxDurations: Array[IntVar]
  // def slotPeriods: Array[IntVar]
  // def globalInvThroughput: IntVar

  // def channels: Array[Int]
  // def actors: Array[Int]
  // def schedulers: Array[Int]
  // def slots: Array[Int]

  // these two are created here so that they are note recreated every time
  // wasting time with memory allocations etc

  // these two are created here so that they are note recreated every time
  // wasting time with memory allocations etc

  /** whether channel c sends data at slot i, from tile src to tile dst at slot i+1. order is
    * (i)(c)(p)(pp)
    */
  // protected lazy val tokensCommunicatedPerChannel: Array[Array[CSCMatrix[Int]]] = slots.map(i => channels.map(c => CSCMatrix.zeros[Int](schedulers.size, schedulers.size)).toArray).toArray
  // protected lazy val channelsTravelTimeMatrix: Array[CSCMatrix[Int]] = channels.map(c => CSCMatrix.zeros[Int](schedulers.size, schedulers.size)).toArray

  // def mat = CSCMatrix(balanceMatrix:_*)
  // protected lazy val prodMat: CSCMatrix[Int] = mat.map(f => if (f >= 0) then f else 0)
  // protected lazy val consMat: CSCMatrix[Int] = mat.map(f => if (f <= 0) then f else 0)
  // protected lazy val invThroughputPerSchedulers = Array.fill(schedulers.size)(0)

  def mkSingleActorFire(a: Int)(q: Int): DenseVector[Int] = {
    val v = DenseVector.zeros[Int](numActors)
    v(a) = q
    v
  }

  def slotAtSchedulerIsTaken(p: Int)(slot: Int): Boolean = {
    val numActors = firingsInSlots.size
    wfor(0, _ < numActors, _ + 1) { a =>
      if (firingsInSlots(a)(p)(slot).isInstantiated() && firingsInSlots(a)(p)(slot).getLB() > 0)
        return true
    }
    return false
  }
  // actors.exists(a => )

  def slotIsClosed(slot: Int): Boolean = {
    val numActors     = firingsInSlots.size
    val numSchedulers = firingsInSlots.head.size
    wfor(0, _ < numSchedulers, _ + 1) { p =>
      wfor(0, _ < numActors, _ + 1) { a =>
        if (!firingsInSlots(a)(p)(slot).isInstantiated()) {
          return false
        }
      }
    }
    return true
  }
  // schedulers.forall(p => actors.forall(a => ))

  def slotHasFiring(slot: Int): Boolean = {
    val numActors     = firingsInSlots.size
    val numSchedulers = firingsInSlots.head.size
    wfor(0, _ < numSchedulers, _ + 1) { p =>
      wfor(0, _ < numActors, _ + 1) { a =>
        if (firingsInSlots(a)(p)(slot).getLB() > 0) {
          return true
        }
      }
    }
    return false
  }
  // actors.exists(a => schedulers.exists(p => firingsInSlots(a)(p)(slot).getLB() > 0))

  def diffTokenVec(consMat: DenseMatrix[Int])(prodMat: DenseMatrix[Int])(
      srcFiring: DenseVector[Int]
  )(dstFiring: DenseVector[Int]): DenseVector[Int] =
    consMat * dstFiring + prodMat * srcFiring

  // protected var sendVecSaved = SparseVector.zeros[Int](0)

  // def recomputeSendTimesMatrix(): Unit = {
  //   for (c <- channels; p <- schedulers) {
  //     for (pp <- schedulers; if pp != p) {
  //       channelsTravelTimeMatrix(c)(p, pp) = channelsTravelTime(c)(p)(pp).getUB()
  //     }
  //   }
  // }

  /** @return
    *   the last closed slot in the schedule so that all slots before it are decided. An int of -1
    *   means the very first slot is still open.
    */
  def recomputeLowerestDecidedSlot(): Int = {
    val numActors        = firingsInSlots.size
    val numSchedulers    = firingsInSlots.head.size
    val numSlots         = firingsInSlots.head.head.size
    var latestClosedSlot = -1
    wfor(0, _ < numSlots, _ + 1) { s =>
      // instantiedCount = 0
      if (slotIsClosed(s) && latestClosedSlot == s - 1) {
        latestClosedSlot = s
      }
    }
    latestClosedSlot
  }

  def recomputeFiringVectors[V <: Vector[Int]](
      firingVector: Array[V],
      firingVectorPerCore: Array[Array[V]]
  ): Unit = {
    val numActors     = firingsInSlots.size
    val numSchedulers = firingsInSlots.head.size
    val numSlots      = firingsInSlots.head.head.size
    wfor(0, _ < numSlots, _ + 1) { slot =>
      wfor(0, _ < numActors, _ + 1) { a =>
        if (firingVector(slot)(a) != 0) firingVector(slot)(a) = 0
        wfor(0, _ < numSchedulers, _ + 1) { p =>
          if (firingVectorPerCore(slot)(p)(a) != 0) {
            firingVectorPerCore(slot)(p)(a) = 0
          }
          firingVector(slot)(a) += firingsInSlots(a)(p)(slot).getLB()
          firingVectorPerCore(slot)(p)(a) += firingsInSlots(a)(p)(slot).getLB()
        }
      }
    }
  }

  /** Must be called after [[recomputeFiringVectors]]! behaviour is undefined otherwise.
    */
  def recomputeTokens(mat: DenseMatrix[Int])(
      firingVector: Array[DenseVector[Int]],
      firingVectorPerCore: Array[Array[DenseVector[Int]]]
  )(tokensBefore: Array[DenseVector[Int]], tokensAfter: Array[DenseVector[Int]]): Unit = {
    val numSlots = firingVector.size
    // recomputeFiringVectors()
    // for the very first initial conditions
    tokensAfter(0) = (mat * firingVector(0)) + tokensBefore(0)
    // for (p <- schedulers) {
    //   startTimes(p)(0) = 0
    //   finishTimes(p)(0) = startTimes(p)(0)
    //   for (a <- actors) {
    //     finishTimes(p)(0) += firingVectorPerCore(0)(p)(a) * actorDuration(a)(p)
    //   }
    // }
    // work based first on the slots and update the timing whenever possible
    wfor(1, _ < numSlots, _ + 1) { i =>
      // println(s"at ${i} : ${firingVector(i)}")
      tokensBefore(i) = tokensAfter(i - 1)
      tokensAfter(i) = mat * firingVector(i) + tokensBefore(i)
    // now for timing
    // for (p <- schedulers) {
    //   maxStartTime = finishTimes(p)(i - 1)
    //   for (
    //     pp <- schedulers;
    //     if pp != p && slotAtSchedulerIsTaken(p)(i) && slotAtSchedulerIsTaken(pp)(i - 1)
    //   ) {
    //     commRecvTime = 0
    //     val diffAtP  = diffTokenVec(i)(p)(p)
    //     val diffAtPP = diffTokenVec(i)(pp)(pp)
    //     for (c <- channels; if diffAtP(c) < 0 && diffAtPP(c) >= -diffAtP(c)) {
    //       // println(s"commRecvTime from $p to $pp by $c is ${-channelsTravelTime(c)(pp)(p).getLB() * diffAtP(c)}")
    //       commRecvTime =
    //         Math.max(commRecvTime, -channelsTravelTime(c)(pp)(p).getLB() * diffAtP(c))
    //     }
    //     maxStartTime = Math.max(maxStartTime, finishTimes(pp)(i - 1) + commRecvTime)
    //   }
    //   startTimes(p)(i) = maxStartTime
    //   finishTimes(p)(i) = maxStartTime
    //   for (a <- actors) finishTimes(p)(i) += firingVectorPerCore(i)(p)(a) * actorDuration(a)(p)
    // }
    }
  }

  /** Must be called after [[recomputeTokensAndTime]]! Behaviour is undefined otherwise.
    */
  // def recomputeInvThroughput(): Unit = {
  //   wfor(0, _ < schedulers.size, _ + 1) { p =>
  //     val latestFinish = finishTimes(p).last
  //     var earliestStart = 0
  //     var searching = true
  //     wfor(0, searching && _ < slots.size, _ + 1) {i =>
  //       if (slotAtSchedulerIsTaken(p)(i)) {
  //         earliestStart = startTimes(p)(i)
  //         searching = false
  //       }
  //     }
  //     invThroughputPerSchedulers(p) = latestFinish - earliestStart
  //   }
  // }

  // def computeGlobalInvThroughput(): Int = {
  //   var maxTh = 0
  //   wfor(0, _ < schedulers.size, _ + 1) { p =>
  //     if (invThroughputPerSchedulers(p) > maxTh) then maxTh = invThroughputPerSchedulers(p)
  //   }
  //   maxTh
  // }

  def slotsPrettyPrint(): Unit = {
    val actors     = 0 until firingsInSlots.size
    val schedulers = 0 until firingsInSlots.head.size
    val slots      = 0 until firingsInSlots.head.head.size
    println(
      schedulers
        .map(s => {
          slots
            .map(slot => {
              actors
                .map(a =>
                  firingsInSlots(a)(s)(slot).getLB() + "|" + firingsInSlots(a)(s)(slot).getUB()
                )
                .mkString("(", ", ", ")")
            })
            .mkString("[", ", ", "]")
        })
        .mkString("[\n ", "\n ", "\n]")
    )
  }

  def schedulePrettyPrint(): Unit = {
    val actors     = 0 until firingsInSlots.size
    val schedulers = 0 until firingsInSlots.head.size
    println(
      schedulers
        .map(s => {
          (0 until actors.size)
            .map(slot => {
              actors.zipWithIndex
                .find((a, ai) =>
                  firingsInSlots(ai)(s)(slot)
                    .isInstantiated() && firingsInSlots(ai)(s)(slot).getLB() > 0
                )
                .map((a, ai) =>
                  slot + "/" + a + ": " + firingsInSlots(ai)(s)(slot)
                    .getLB()
                )
                .getOrElse("_")
            })
            .mkString("[", ", ", "]")
        })
        .mkString("[\n ", "\n ", "\n]")
    )
  }

  // def timingPrettyPrint(): Unit = {
  //   println(
  //     schedulers
  //       .map(s => {
  //         (0 until actors.size)
  //           .map(slot => {
  //             startTimes(s)(slot) + " - " + finishTimes(s)(slot)
  //           })
  //           .mkString("[", ", ", "]")
  //       })
  //       .mkString("[\n ", "\n ", "\n]")
  //   )
  // }

}
