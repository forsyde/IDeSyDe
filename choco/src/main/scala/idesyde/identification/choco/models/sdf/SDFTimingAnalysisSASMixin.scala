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
import org.chocosolver.solver.constraints.`extension`.Tuples
import idesyde.identification.choco.models.SingleProcessSingleMessageMemoryConstraintsMixin
import idesyde.identification.choco.models.TileAsyncInterconnectCommsMixin
import idesyde.utils.CoreUtils.wfor

trait SDFTimingAnalysisSASMixin extends ChocoModelMixin {
  this: SingleProcessSingleMessageMemoryConstraintsMixin  =>

  def actors: Array[Int]
  def schedulers: Array[Int]
  def balanceMatrix: Array[Array[Int]]
  def initialTokens: Array[Int]
  def actorDuration: Array[Array[Int]]

  def channelsCommunicate: Array[Array[Array[BoolVar]]]
  def channelsTravelTime: Array[Array[Array[IntVar]]]
  def firingsInSlots: Array[Array[Array[IntVar]]]
  def initialLatencies: Array[IntVar]
  def slotMaxDurations: Array[IntVar]
  def slotPeriods: Array[IntVar]
  def globalInvThroughput: IntVar

  def maxRepetitionsPerActors(actorId: Int): Int
  def isSelfConcurrent(actorId: Int): Boolean

  def actorMapping(actorId: Int) = processesMemoryMapping(actors.indexOf(actorId))
  def startLatency(schedulerId: Int): IntVar
  def numActorsScheduledSlotsInStaticCyclic(actorId: Int)(tileId: Int)(slotId: Int): IntVar

  private def maxSlots = firingsInSlots.head.head.size
  private def slotRange = 0 until maxSlots

  def allSlotsForActor(a: Int) =
    (0 until maxSlots)
      .map(slot =>
        (0 until schedulers.size)
          .map(s => numActorsScheduledSlotsInStaticCyclic(a)(s)(slot))
          .toArray
      )
      .toArray

  def slots(ai: Int)(sj: Int) =
    (0 until maxSlots)
      .map(numActorsScheduledSlotsInStaticCyclic(ai)(sj)(_))
      .toArray

  def allInSlot(s: Int)(slot: Int) =
    (0 until actors.size).map(numActorsScheduledSlotsInStaticCyclic(_)(s)(slot)).toArray

  def postOnlySAS(): Unit = {
    actors.zipWithIndex.foreach((a, ai) => {
      // disable self concurreny if necessary
      // if (!isSelfConcurrent(a)) {
      //   for (slot <- slotRange) {
      //     chocoModel.sum(schedulers.map(p => firingsInSlots(a)(p)(slot)), "<=", 1).post()
      //   }
      // }
      // set total number of firings
      chocoModel.sum(firingsInSlots(ai).flatten, "=", maxRepetitionsPerActors(a)).post()
      schedulers.zipWithIndex
        .foreach((s, sj) => {
          // if (actors.size > 1) {
          //   chocoModel
          //     .min(s"0_in_${ai}_${sj}", (0 until actors.size).map(slots(ai)(sj)(_)): _*)
          //     .eq(0)
          //     .post()
          // }
          chocoModel.ifThenElse(
            actorMapping(ai).eq(sj).decompose(),
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
      val onlyOneActor = Tuples(true)
      onlyOneActor.add(Array.fill(actors.size)(0))
      for ((a, ai) <- actors.zipWithIndex; q <- 1 to maxRepetitionsPerActors(a)) {
        val vec = (Array.fill(ai)(0) :+ q) ++ Array.fill(actors.size - ai - 1)(0)
        // println(vec.mkString(", "))
        onlyOneActor.add(vec)
        // chocoModel.ifOnlyIf(
        //   firingsInSlots(a)(sj)(slot).gt(0).decompose(),
        //   chocoModel.and(
        //     actors.filter(_ != a).map(firingsInSlots(_)(sj)(slot).eq(0).decompose()): _*
        //   )
        // )
      }
      chocoModel
        .table(actors.map(a => firingsInSlots(a)(sj)(slot)).toArray, onlyOneActor, "CT+")
        .post()
      // chocoModel.atMostNValues(allInSlot(sj)(slot), chocoModel.intVar(2), true).post()
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
          channelsCommunicate,
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
      val channelsCommunicate: Array[Array[Array[BoolVar]]],
      val channelsTravelTime: Array[Array[Array[IntVar]]],
      val firingsInSlots: Array[Array[Array[IntVar]]],
      val initialLatencies: Array[IntVar],
      val slotMaxDurations: Array[IntVar],
      val slotPeriods: Array[IntVar],
      val globalInvThroughput: IntVar
  ) extends Propagator[IntVar](
        firingsInSlots.flatten.flatten ++ initialLatencies ++ slotMaxDurations ++ slotPeriods ++ channelsCommunicate.flatten.flatten :+ globalInvThroughput,
        PropagatorPriority.CUBIC,
        false
      )
      with SDFChocoRecomputeMethodsMixin {

    val channels   = (0 until initialTokens.size).toArray
    val actors     = (0 until maxFiringsPerActor.size).toArray
    val schedulers = (0 until slotPeriods.size).toArray
    val slots      = (0 until firingsInSlots.head.head.size).toArray

    val mat = CSCMatrix(balanceMatrix: _*)

    // val firingVector: Array[SparseVector[Int]] =
    //   slots.map(slot => SparseVector.zeros[Int](actors.size)).toArray

    val singleActorFire = actors
      .map(a => (0 to maxFiringsPerActor(a)).map(q => mkSingleActorFire(a)(q)).toArray)
      .toArray

    var latestClosedSlot = 0
    var tailZeros        = 0

    def propagate(evtmask: Int): Unit = {
      var nextSlot = -1
      // var sendVecSaved = SparseVector.zeros[Int](channels.size)
      // println("propagate")
      recomputeTokensAndTime()
      // latestClosedSlot = 0
      latestClosedSlot = -1
      // slotsPrettyPrint()
      for (s <- slots) {
        // instantiedCount = 0
        if (slotIsClosed(s)) {
          if (latestClosedSlot >= 0 && latestClosedSlot < s - 1) {
            // println("skipped slot")
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
        if (
          slotIsClosed(s) && max(firingVector(s - 1)) > 0 && max(firingVector(s)) == 0 && max(
            firingVector(s + 1)
          ) > 0
        ) {
          // slotsPrettyPrint()
          // tailZeros += 1
          // println(s"zero ${s}")
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

      // if (latestClosedSlot > -1) println(tokensBefore(latestClosedSlot).toString)
      // make sure that the latest slot only has admissible firings
      if (latestClosedSlot < slots.size - 1) {
        nextSlot = latestClosedSlot + 1
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
      // slotsPrettyPrint()
      // also propagate if any channel sendings are necessary
      // this assumes that an actor is mapped to one and only one processor
      // if (latestClosedSlot > 0) {
      //   for (i <- slots.drop(1).take(latestClosedSlot)) {
          // println(i)
          // wfor(0, _ < schedulers.size, _ + 1) { srci =>
          //   wfor(0, _ < schedulers.size, _ + 1) { dsti =>
          //     if (srci != dsti) {
          //       wfor(0, _ < actors.size, _ + 1) {prodi => 
          //         val src = schedulers(srci)
          //         val prod = actors(prodi)
          //         if (actorMapping(prod).isInstantiatedTo(src)) {
          //           wfor(0, _ < actors.size, _ + 1) {consi => 
          //             val dst = schedulers(dsti)
          //             val cons = actors(consi)
          //             if (actorMapping(cons).isInstantiatedTo(dst)) {
          //               wfor(0, _ < channels.size, _ + 1) { chi =>
          //                 val c = channels(chi)
          //                 if ()
          //                 channelsCommunicate(c)(src)(dst).updateLowerBound(1, this)
          //               }
          //             }
          //           }
          //         wfor(0, _ < actors.size, _ + 1) {consi => 
          //         }
          //       }
          //     }
          //   }
          // }
          // for (
          //   dst <- schedulers; src <- schedulers;
          //   if src != dst && slotAtSchedulerIsTaken(dst)(i) && slotAtSchedulerIsTaken(src)(i - 1)
          // ) {
          //   val diffAtSrc = diffTokenVec(i)(src)(src)
          //   val diffAtDst = diffTokenVec(i)(dst)(dst)
          //   // println(s"src vec from ${src} to ${src} at ${i}: ${diffAtSrc.toString()}")
          //   // println(s"dst vec from ${dst} to ${dst} at ${i}: ${diffAtDst.toString()}")
          //   for (c <- channels) {
          //     // sendVec = prodMat * firingVectorPerCore(i)(src) - consMat * firingVectorPerCore(i - 1)(dst) + tokensAfter(i - 1)
          //     if (diffAtDst(c) < 0 && diffAtSrc(c) >= -diffAtDst(c)) {
          //       // println(s"communicate ${c} from ${src} to ${dst} at ${i}")
          //       channelsCommunicate(c)(src)(dst).updateLowerBound(1, this)
          //     }
          //   }
          // }
      //   }
      // }

      // now try to bound the timing,
      var maxInvTh      = 0
      var latestFinish  = 0
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
      var allFired        = true
      var allCommunicated = true
      // println("checking entailment")
      // slotsPrettyPrint()
      recomputeTokensAndTime()
      recomputeFiringVectors()
      if (latestClosedSlot > 0) {
        for (i <- slots.drop(1).take(latestClosedSlot)) {
          // println(i)
          for (
            dst <- schedulers; src <- schedulers;
            if src != dst && slotAtSchedulerIsTaken(dst)(i) && slotAtSchedulerIsTaken(src)(i - 1)
          ) {
            val diffAtSrc = diffTokenVec(i)(src)(src)
            val diffAtDst = diffTokenVec(i)(dst)(dst)
            // println(s"src vec from ${src} to ${src} at ${i}: ${diffAtSrc.toString()}")
            // println(s"dst vec from ${dst} to ${dst} at ${i}: ${diffAtDst.toString()}")
            for (c <- channels) {
              // should have communicated
              allCommunicated = channelsCommunicate(c)(src)(dst).isInstantiated() && allCommunicated
              if (
                diffAtDst(c) < 0 && diffAtSrc(c) >= -diffAtDst(c) && channelsCommunicate(c)(src)(
                  dst
                ).getLB() < 1
              ) {
                // println(s" failing due to no comm of ${c} from ${src} to ${dst} at ${i}")
                return ESat.FALSE
              }
            }
          }
        }
      }
      // work based first on the slots and update the timing whenever possible
      for (
        i <- slots
        // p <- schedulers
      ) {
        // make sure there are no holes in the schedule
        // if (any(firingVector(i)) && !any(firingVector(i - 1))) then return ESat.FALSE
        // actors.exists(a => firingVector(i).)
        if (min(consMat * firingVector(i) + tokensBefore(i)) < 0) {
          // println("wrong tokens for entailment")
          return ESat.FALSE
        }
        for (
          p <- schedulers;
          a <- actors
        ) {
          allFired = firingsInSlots(a)(p)(i).isInstantiated() && allFired
        }
      }
      // println("all is fired " + allFired)
      // println("all is comm " + allCommunicated)
      if (allFired && allCommunicated) then
        if (tokensAfter.last == tokens0) {
          // println("passed final tokens for entailment")
          return ESat.TRUE
        } else {
          // println("wrong final tokens for entailment")
          return ESat.FALSE
        }
      else return ESat.UNDEFINED
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

  class SASChannelCommunicationPropagator(
    val actorMappings: Array[IntVar], 
    val channelsCommunicate: Array[Array[Array[BoolVar]]]
  )

}
