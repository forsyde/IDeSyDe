package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.variables.IntVar
import breeze.linalg.DenseVector
import breeze.linalg.DenseMatrix
import breeze.linalg.SparseVector
import breeze.linalg.CSCMatrix

trait SDFChocoRecomputeMethodsMixin {
  def maxFiringsPerActor: Array[Int]
  def balanceMatrix: Array[Array[Int]]
  def initialTokens: Array[Int]
  def actorDuration: Array[Array[Int]]
  def channelsTravelTime: Array[Array[Array[IntVar]]]
  def firingsInSlots: Array[Array[Array[IntVar]]]
  // def initialLatencies: Array[IntVar]
  // def slotMaxDurations: Array[IntVar]
  // def slotPeriods: Array[IntVar]
  // def globalInvThroughput: IntVar

  def channels: Range
  def actors: Range
  def schedulers: Range
  def slots: Range

  // these two are created here so that they are note recreated every time
  // wasting time with memory allocations etc
  protected lazy val tokens0: SparseVector[Int] = SparseVector(initialTokens)
  // these two are created here so that they are note recreated every time
  // wasting time with memory allocations etc
  protected lazy val tokensBefore = SparseVector(initialTokens) +: slots
    .drop(1)
    .map(_ => SparseVector.zeros[Int](channels.size))
    .toArray
  // def tokens(slot: Int) = if (slot < 0) then tokens0 else tokensBefore(slot)
  protected lazy val tokensAfter =
    slots.map(_ => SparseVector.zeros[Int](channels.size)).toArray

  /** whether channel c sends data at slot i, from tile src to tile dst at slot i+1. order is
    * (i)(c)(p)(pp)
    */
  // protected lazy val tokensCommunicatedPerChannel: Array[Array[CSCMatrix[Int]]] = slots.map(i => channels.map(c => CSCMatrix.zeros[Int](schedulers.size, schedulers.size)).toArray).toArray
  // protected lazy val channelsTravelTimeMatrix: Array[CSCMatrix[Int]] = channels.map(c => CSCMatrix.zeros[Int](schedulers.size, schedulers.size)).toArray
  protected lazy val startTimes: Array[Array[Int]] =
    schedulers.map(_ => slots.map(_ => 0).toArray).toArray
  protected lazy val finishTimes: Array[Array[Int]] =
    schedulers.map(_ => slots.map(_ => 0).toArray).toArray
  def mat: CSCMatrix[Int]
  protected lazy val prodMat: CSCMatrix[Int] = mat.map(f => if (f >= 0) then f else 0)
  protected lazy val consMat: CSCMatrix[Int] = mat.map(f => if (f <= 0) then f else 0)

  def mkSingleActorFire(a: Int)(q: Int): SparseVector[Int] = {
    val v = SparseVector.zeros[Int](actors.size)
    v(a) = q
    v
  }

  def slotAtSchedulerIsTaken(p: Int)(slot: Int): Boolean = {
    for (a <- actors) {
      if (firingsInSlots(a)(p)(slot).getLB() > 0)
         return true
    }
    return false
  }
    // actors.exists(a => )

  def slotIsClosed(slot: Int): Boolean = {
    for (p <- schedulers; a <- actors) {
      if (!firingsInSlots(a)(p)(slot).isInstantiated()) {
        return false
      }
    }
    return true
  }
    // schedulers.forall(p => actors.forall(a => ))

  def slotHasFiring(slot: Int): Boolean = {
    for (p <- schedulers; a <- actors) {
      if (firingsInSlots(a)(p)(slot).getLB() > 0) {
        return true
      }
    }
    return false
  }
    // actors.exists(a => schedulers.exists(p => firingsInSlots(a)(p)(slot).getLB() > 0))

  protected lazy val firingVector: Array[SparseVector[Int]] =
    slots.map(slot => SparseVector.zeros[Int](actors.size)).toArray

  def diffTokenVec(slot: Int)(srcSched: Int)(dstSched: Int): SparseVector[Int] = if (slot > 0) {
    consMat * firingVectorPerCore(slot)(dstSched) + prodMat * firingVectorPerCore(slot - 1)(
      srcSched
    ) // + tokensBefore(slot - 1)
  } else {
    SparseVector.zeros[Int](channels.size)
  }

  protected lazy val firingVectorPerCore: Array[Array[SparseVector[Int]]] =
    slots.map(i => schedulers.map(p => SparseVector.zeros[Int](actors.size)).toArray).toArray

  protected var maxStartTime = 0
  protected var commRecvTime = 0
  // protected var sendVecSaved = SparseVector.zeros[Int](0)

  def singleActorFire: Array[Array[SparseVector[Int]]]

  // def recomputeSendTimesMatrix(): Unit = {
  //   for (c <- channels; p <- schedulers) {
  //     for (pp <- schedulers; if pp != p) {
  //       channelsTravelTimeMatrix(c)(p, pp) = channelsTravelTime(c)(p)(pp).getUB()
  //     }
  //   }
  // }

  def recomputeFiringVectors(): Unit = {
    for (
      slot <- slots;
      a    <- actors
    ) {
      if (firingVector(slot)(a) != 0) firingVector(slot)(a) = 0
      for (p <- schedulers; if firingVectorPerCore(slot)(p)(a) != 0) {
        firingVectorPerCore(slot)(p)(a) = 0
      }
      for (p <- schedulers) {
        firingVector(slot)(a) += firingsInSlots(a)(p)(slot).getLB()
        firingVectorPerCore(slot)(p)(a) += firingsInSlots(a)(p)(slot).getLB()
      }
    }
  }

  def recomputeTokensAndTime(): Unit = {
    recomputeFiringVectors()
    // for the very first initial conditions
    tokensAfter(0) = mat * firingVector(0) + tokensBefore(0)
    for (p <- schedulers) {
      startTimes(p)(0) = 0
      finishTimes(p)(0) = startTimes(p)(0)
      for (a <- actors) finishTimes(p)(0) += firingVector(0)(a) * actorDuration(a)(p)
    }
    // work based first on the slots and update the timing whenever possible
    for (
      i <- slots.drop(1)
      // p <- schedulers
    ) {
      // println(s"at ${i} : ${firingVector(i)}")
      tokensBefore(i) = tokensAfter(i - 1)
      tokensAfter(i) = mat * firingVector(i) + tokensBefore(i)
      for (p <- schedulers) {
        maxStartTime = finishTimes(p)(i - 1)
        for (
          pp <- schedulers;
          if pp != p && slotAtSchedulerIsTaken(p)(i) && slotAtSchedulerIsTaken(pp)(i - 1)
        ) {
          commRecvTime = 0
          val diffAtP  = diffTokenVec(i)(p)(p)
          val diffAtPP = diffTokenVec(i)(pp)(pp)
          for (c <- channels; if diffAtP(c) < 0 && diffAtPP(c) >= -diffAtP(c)) {
            commRecvTime =
              Math.max(commRecvTime, -channelsTravelTime(c)(pp)(p).getUB() * diffAtP(c))
          }
          maxStartTime = Math.max(maxStartTime, finishTimes(pp)(i - 1) + commRecvTime)
        }
        startTimes(p)(i) = maxStartTime
        finishTimes(p)(i) = maxStartTime
        for (a <- actors) finishTimes(p)(i) += firingVector(i)(a) * actorDuration(a)(p)
      }
    }
  }
}
