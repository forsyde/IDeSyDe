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

    def actorArrayOfSlot(p: Int)(i: Int) = firingsInSlots.map(as => as(p)(i))

    // these two are created here so that they are note recreated every time
    // wasting time with memory allocations etc
    def tokens0: SparseVector[Int]
    def tokensBefore: Array[SparseVector[Int]]
    // def tokens(slot: Int) = if (slot < 0) then tokens0 else tokensBefore(slot)
    def tokensAfter: Array[SparseVector[Int]]
    def startTimes:  Array[Array[Int]]
    def finishTimes: Array[Array[Int]]
    def mat: CSCMatrix[Int]
    def prodMat: CSCMatrix[Int]
    def consMat: CSCMatrix[Int]

    def mkSingleActorFire(a: Int)(q: Int): SparseVector[Int] = {
      val v = SparseVector.zeros[Int](actors.size)
      v(a) = q
      v
    }

    def slotAtSchedulerIsTaken(p: Int)(slot: Int): Boolean =
      actors.exists(a => firingsInSlots(a)(p)(slot).getLB() > 0)

    def slotIsClosed(slot: Int): Boolean = 
      schedulers.forall(p => actors.forall(a => firingsInSlots(a)(p)(slot).isInstantiated()))


    def slotHasFiring(slot: Int): Boolean =
      actors.exists(a => schedulers.exists(p => firingsInSlots(a)(p)(slot).getLB() > 0))

    def firingVector: Array[SparseVector[Int]] 

    def singleActorFire: Array[Array[SparseVector[Int]]]

    def recomputeFiringVectors(): Unit = {
      for (
        slot <- slots;
        a <- actors
      ) {
        firingVector(slot)(a) = 0
        for (p <- schedulers) firingVector(slot)(a) += firingsInSlots(a)(p)(slot).getLB()
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
          startTimes(p)(i) = finishTimes(p)(i-1)
              // schedulers
              //   .filter(_ != p)
              //   .map(pp => (pp, tokensBefore(i) - tokensAfter(i - 1)))
              //   .map((pp, b) =>
              //     // this is the actual essential equation
              //     finishTimes(pp)(i - 1) + channels
              //       .map(c => b(c) * channelsTravelTime(c)(pp)(p).getUB())
              //       .sum
              //   )
              //   .max
          finishTimes(p)(i) = startTimes(p)(i)
          for (a <- actors) finishTimes(p)(i) += firingVector(i)(a) * actorDuration(a)(p)
        }
      }
    }
}
