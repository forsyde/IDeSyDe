package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import scala.collection.mutable.HashMap
import breeze.linalg._
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.exception.ContradictionException

trait SDFTimingAnalysisSASMixin extends ChocoModelMixin {

  def actors: Array[Int]
  def schedulers: Array[Int]
  def balanceMatrix: Array[Array[Int]]
  def initialTokens: Array[Int]
  def actorDuration: Array[Array[Int]]

  def channelsTravelTime: Array[Array[Array[IntVar]]]
  def firingsInSlots: Array[Array[Array[IntVar]]]
  def initialLatencies: Array[IntVar]
  def slotMaxDurations: Array[IntVar]
  def slotPeriods: Array[IntVar]
  def globalInvThroughput: IntVar

  def maxRepetitionsPerActors(actorId: Int): Int

  def actorMapping(actorId: Int)(schedulerId: Int): BoolVar
  def startLatency(schedulerId: Int): IntVar
  def numActorsScheduledSlotsInStaticCyclic(actorId: Int)(tileId: Int)(slotId: Int): IntVar

  def allSlotsForActor(a: Int) =
    (0 until firingsInSlots.head.head.size)
      .map(slot =>
        (0 until schedulers.size)
          .map(s => numActorsScheduledSlotsInStaticCyclic(a)(s)(slot))
          .toArray
      )
      .toArray

  def slots(ai: Int)(sj: Int) =
    (0 until firingsInSlots.head.head.size)
      .map(numActorsScheduledSlotsInStaticCyclic(ai)(sj)(_))
      .toArray

  def allInSlot(s: Int)(slot: Int) =
    (0 until actors.size).map(numActorsScheduledSlotsInStaticCyclic(_)(s)(slot)).toArray

  def postOnlySAS(): Unit = {
    actors.zipWithIndex.foreach((a, ai) => {
      // set total number of firings
      chocoModel.sum(allSlotsForActor(ai).flatten, "=", maxRepetitionsPerActors(a)).post()
      schedulers.zipWithIndex
        .foreach((s, sj) => {
          // if (actors.size > 1) {
          //   chocoModel
          //     .min(s"0_in_${ai}_${sj}", (0 until actors.size).map(slots(ai)(sj)(_)): _*)
          //     .eq(0)
          //     .post()
          // }
          chocoModel.ifThenElse(
            actorMapping(ai)(sj),
            chocoModel.sum(slots(ai)(sj), ">=", 1),
            chocoModel.sum(slots(ai)(sj), "=", 0)
          )
          chocoModel.sum(slots(ai)(sj), "<=", maxRepetitionsPerActors(a)).post()
        })
    })
    for (
      (s, sj) <- schedulers.zipWithIndex;
      slot    <- 0 until firingsInSlots.head.head.size
    ) {
      for (a <- actors) {
        chocoModel.ifThen(
          firingsInSlots(a)(sj)(slot).gt(0).decompose(),
          chocoModel.and(actors.filter(_ != a).map(firingsInSlots(_)(sj)(slot).eq(0).decompose()):_*)
        )
      }
      chocoModel.atMostNValues(allInSlot(sj)(slot), chocoModel.intVar(2), true).post()
    }
    // val firingsSlotSums = (0 until firingsInSlots.head.head.size).map(slot => chocoModel.sum(s"sum_${slot}", actors.flatMap(a => schedulers.map(p => firingsInSlots(a)(p)(slot))):_*)).toArray
    // for (
    //   slot <- 1 until firingsInSlots.head.head.size
    // ) {
    //   chocoModel.ifThen(firingsSlotSums(slot - 1).gt(0), firingsSlotSums(slot).gt(0))
    // }
  }

  def postSDFTimingAnalysisSAS(): Unit = {
    postOnlySAS()
    chocoModel.post(
      new Constraint(
        "global_sas_sdf_prop",
        SASTimingAndTokensPropagator(
          actors.zipWithIndex.map((a, i) => maxRepetitionsPerActors(i)),
          balanceMatrix,
          initialTokens,
          actorDuration,
          channelsTravelTime,
          firingsInSlots,
          initialLatencies,
          slotMaxDurations,
          slotPeriods,
          globalInvThroughput
        )
      )
    )
  }

  class SASTimingAndTokensPropagator(
      val maxFiringsPerActor: Array[Int],
      val balanceMatrix: Array[Array[Int]],
      val initialTokens: Array[Int],
      val actorDuration: Array[Array[Int]],
      val channelsTravelTime: Array[Array[Array[IntVar]]],
      val firingsInSlots: Array[Array[Array[IntVar]]],
      val initialLatencies: Array[IntVar],
      val slotMaxDurations: Array[IntVar],
      val slotPeriods: Array[IntVar],
      val globalInvThroughput: IntVar
  ) extends Propagator[IntVar](
        firingsInSlots.flatten.flatten ++ initialLatencies ++ slotMaxDurations ++ slotPeriods :+ globalInvThroughput,
        PropagatorPriority.TERNARY,
        false
      ) with SDFChocoRecomputeMethodsMixin {

    val channels   = 0 until initialTokens.size
    val actors     = 0 until maxFiringsPerActor.size
    val schedulers = 0 until slotPeriods.size
    val slots      = 0 until firingsInSlots.head.head.size

    // these two are created here so that they are note recreated every time
    // wasting time with memory allocations etc
    val tokens0 = SparseVector(initialTokens)
    val tokensBefore = SparseVector(initialTokens) +: slots
      .drop(1)
      .map(_ => SparseVector.zeros[Int](channels.size))
      .toArray
    // def tokens(slot: Int) = if (slot < 0) then tokens0 else tokensBefore(slot)
    val tokensAfter =
      slots.map(_ => SparseVector.zeros[Int](channels.size)).toArray
    val startTimes  = schedulers.map(_ => slots.map(_ => 0).toArray).toArray
    val finishTimes = schedulers.map(_ => slots.map(_ => 0).toArray).toArray
    val mat         = CSCMatrix(balanceMatrix: _*)
    val prodMat     = mat.map(f => if (f >= 0) then f else 0)
    val consMat     = mat.map(f => if (f <= 0) then f else 0)

    val firingVector: Array[SparseVector[Int]] = slots.map(slot => SparseVector.zeros[Int](actors.size)).toArray

    val singleActorFire = actors.map(a => (0 to maxFiringsPerActor(a)).map(q => mkSingleActorFire(a)(q)).toArray).toArray

    var latestClosedSlot = 0
    var tailZeros = 0

    def propagate(evtmask: Int): Unit = {
      // println("propagate")
      recomputeTokensAndTime()
      // latestClosedSlot = 0
      latestClosedSlot = -1
      // println(
      //   schedulers
      //     .map(s => {
      //       slots
      //         .map(slot => {
      //           actors
      //             .map(a =>
      //               firingsInSlots(a)(s)(slot).getLB() + "|" + firingsInSlots(a)(s)(slot).getUB()
      //             )
      //             .mkString("(", ", ", ")")
      //         })
      //         .mkString("[", ", ", "]")
      //     })
      //     .mkString("[\n ", "\n ", "\n]")
      // )
      for (s <- slots) {
        // instantiedCount = 0
        if (slotIsClosed(s)) {
          if (latestClosedSlot >= 0 && latestClosedSlot < s - 1) {
            fails() // there is an open hole in the schedule. We avoid that.
          } else if (latestClosedSlot >= s - 1) {
            latestClosedSlot = s
          }
        }
      }
      // println(s"latestClosedSlot ${latestClosedSlot}")
      // now, we also avoid instantiations that are farther than the decision slot
      // tailZeros = 0
      // if (latestClosedSlot > -1) {
      for (s <- slots.drop(1).dropRight(1)) {
        if(slotIsClosed(s) && max(firingVector(s-1)) > 0 && max(firingVector(s)) == 0 && max(firingVector(s+1)) > 0) {
        // println(
        //   schedulers
        //     .map(s => {
        //       slots
        //         .map(slot => {
        //           actors
        //             .map(a =>
        //               firingsInSlots(a)(s)(slot).getLB() + "|" + firingsInSlots(a)(s)(slot).getUB()
        //             )
        //             .mkString("(", ", ", ")")
        //         })
        //         .mkString("[", ", ", "]")
        //     })
        //     .mkString("[\n ", "\n ", "\n]")
        //   )
        //   println(s"zero ${s}")
          // tailZeros += 1
          fails()
          // throw ContradictionException().set(this, firingsInSlots(a)(p)(s), s"${a}_${p}_${s} invalidated order")
        }
      }
        // println("tailZeros " + tailZeros)
        // if (tailZeros > 0) {
        //   println("invalidated by before order!")
        //   fails()
        // }
      // }
      // first, we check if the model is still sane
      for (
        s <- slots.take(latestClosedSlot + 1)
        // p <- schedulers
      ) {
        // make sure that the tokens are not negative.
        for (
          p <- schedulers;
          a <- actors;
          if firingsInSlots(a)(p)(s).isInstantiated();
          q = firingsInSlots(a)(p)(s).getValue()
          if min(consMat * singleActorFire(a)(q) + tokensBefore(s)) < 0 // there is a negative fire
        ) {
          // println("invalidated by token!")
          fails()
        }
      }

      // make sure that the latest slot only has admissible firings
      if (latestClosedSlot < slots.size - 1) {
        val nextSlot = latestClosedSlot + 1
        for (
          p <- schedulers;
          a <- actors;
          q <- firingsInSlots(a)(p)(nextSlot).getUB() to 1 by -1;
          if min(consMat * singleActorFire(a)(q) + tokensBefore(nextSlot)) < 0
        ) {
          firingsInSlots(a)(p)(nextSlot).updateUpperBound(q - 1, this) //
        }
      }
      
      // println("----------------------------------------------------")
      // println(
      //   schedulers
      //     .map(s => {
      //       slots
      //         .map(slot => {
      //           actors
      //             .map(a =>
      //               firingsInSlots(a)(s)(slot).getLB() + "|" + firingsInSlots(a)(s)(slot).getUB()
      //             )
      //             .mkString("(", ", ", ")")
      //         })
      //         .mkString("[", ", ", "]")
      //     })
      //     .mkString("[\n ", "\n ", "\n]")
      // )

      // now try to bound the timing,
      var maxInvTh = 0
      var latestFinish = 0
      var earliestStart = 0
      for (p <- schedulers) {
        latestFinish = finishTimes(p)(slots.size - 1)
        earliestStart = 0
        for (i <- slots) {
          // this criteria makes it stop updating in the first found
          if (earliestStart == 0 && slotAtSchedulerIsTaken(p)(i)) {
            earliestStart = startTimes(p)(i)
          }
        }
        maxInvTh = latestFinish - earliestStart
      }
      globalInvThroughput.updateLowerBound(maxInvTh, this)

    }

    def isEntailed(): ESat = {
      // println("checking entailment")
      recomputeTokensAndTime()
      recomputeFiringVectors()
      // work based first on the slots and update the timing whenever possible
      for (
        i <- slots.drop(1)
        // p <- schedulers
      ) {
        // make sure there are no holes in the schedule
        // if (any(firingVector(i)) && !any(firingVector(i - 1))) then return ESat.FALSE
        // actors.exists(a => firingVector(i).)
        if (min(consMat * firingVector(i) + tokensBefore(i)) < 0) then return ESat.FALSE
      }
      val allFired = firingsInSlots.zipWithIndex.forall((vs, a) =>
        vs.flatten.filter(v => v.isInstantiated()).map(v => v.getValue()).sum >= maxFiringsPerActor(
          a
        )
      )
      if (allFired) then if (tokensAfter.last == tokens0) then ESat.TRUE else ESat.FALSE
      else ESat.UNDEFINED
    }

    // def computeStateTime(): Unit = {
    //   stateTime.clear()
    //   stateTime += 0 -> initialTokens
    //   // we can do a simple for loop since we assume
    //   // that the schedules are SAS
    //   for (
    //     q <- slots;
    //     p <- schedulers;
    //     a <- actors;
    //     if firingsInSlots(a)(p)(q).isInstantiated();
    //     firings = firingsInSlots(a)(p)(q).getValue()
    //   ) {
    //     stateTime.foreach((t, b) => {
    //       // if ()
    //     })
    //   }
    // }
  }

}
