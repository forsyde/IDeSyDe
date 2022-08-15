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

  def slotsInAll(i: Int) =
    (0 until actors.size)
      .map(q =>
        (0 until schedulers.size).map(s => numActorsScheduledSlotsInStaticCyclic(i)(s)(q)).toArray
      )
      .toArray
  def slots(i: Int)(j: Int) =
    (0 until actors.size).map(numActorsScheduledSlotsInStaticCyclic(i)(j)(_)).toArray

  def postOnlySAS(): Unit = {
    actors.zipWithIndex.foreach((a, i) => {
      chocoModel.sum(slotsInAll(i).flatten, "=", maxRepetitionsPerActors(a)).post()
      schedulers.zipWithIndex
        .foreach((s, j) => {
          if (actors.size > 1) {
            chocoModel
              .min(s"0_in_${i}_${j}", (0 until actors.size).map(slots(i)(j)(_)): _*)
              .eq(0)
              .post()
          }
          chocoModel.ifThenElse(
            actorMapping(i)(j),
            chocoModel.sum(slots(i)(j), ">=", 1),
            chocoModel.sum(slots(i)(j), "=", 0)
          )
          chocoModel.atMostNValues(slots(i)(j), chocoModel.intVar(2), true).post()
          chocoModel.sum(slots(i)(j), "<=", maxRepetitionsPerActors(a)).post()
        })
    })
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
        PropagatorPriority.BINARY,
        false
      ) {

    val channels = 0 until initialTokens.size
    val actors     = 0 until maxFiringsPerActor.size
    val schedulers = 0 until slotPeriods.size
    val slots      = 0 until firingsInSlots.head.head.size

    def actorArrayOfSlot(p: Int)(i: Int) = firingsInSlots.map(as => as(p)(i))

    // these two are created here so that they are note recreated every time
    // wasting time with memory allocations etc
    val mat = DenseMatrix(balanceMatrix:_*)
    val firingVector = DenseVector.zeros[Int](actors.size)

    def propagate(evtmask: Int): Unit = {
      val tokensBefore = schedulers.map(_ => slots.map(_ => DenseVector(initialTokens)).toArray).toArray
      val tokensAfter = schedulers.map(_ => slots.map(_ => DenseVector(initialTokens)).toArray).toArray
      val startTimes = schedulers.map(_ => slots.map(_ => 0).toArray).toArray
      val finishTimes = schedulers.map(_ => slots.map(_ => 0).toArray).toArray
      // work based first on the slots and update the timing whenever possible
      for (
        i <- slots;
        p <- schedulers
      ) {
        val mappedA = actors.find(a => firingsInSlots(a)(p)(i).isInstantiated())
        // there is a mapping
        if (mappedA.isDefined) {
          val a = mappedA.get
          firingVector(a) = firingsInSlots(a)(p)(i).getValue() 
          tokensAfter(p)(i) = mat * firingVector + tokensBefore(p)(i)
          startTimes(p)(i) = if (i > 0) then 
            schedulers.filter(_ != p).map(pp => (pp, tokensBefore(p)(i) - tokensAfter(pp)(i-1)))
              .map((pp, b) => 
                // this is the actual essential equation
                finishTimes(pp)(i-1) + channels.map(c => b(c) * channelsTravelTime(c)(pp)(p).getUB()).sum
                ).max
            else 
              0
          finishTimes(p)(i) = firingVector(a) * actorDuration(a)(p) + startTimes(p)(i)
          firingVector(a) = 0
        } else if (i == 0 || actors.exists(firingsInSlots(_)(p)(i-1).isInstantiated())) { // there is still no mapping and the slots can be reduced
          for (
            a <- actors;
            q <- firingsInSlots(a)(p)(i).getUB() to 1 by -1
          ) {
            firingVector(a) = q
            val possibleTokensAfter = mat * firingVector + tokensBefore(p)(i)
            if (min(possibleTokensAfter) < 0) then firingsInSlots(a)(p)(i).updateUpperBound(q-1, this)
            firingVector(a) = 0
          }
        }
      }
      // now try to bound the timing,
      globalInvThroughput.updateLowerBound(schedulers.map(p => {
        finishTimes(p).max - startTimes(p).min
      }).max, this)

    }

    def isEntailed(): ESat = {
      val allFired = firingsInSlots.zipWithIndex.forall((vs, a) =>
        vs.flatten.filter(v => v.isInstantiated()).map(v => v.getValue()).sum >= maxFiringsPerActor(
          a
        )
      )
      if (allFired) ESat.TRUE else ESat.UNDEFINED
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
