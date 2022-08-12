package idesyde.identification.choco.models.sdf

import idesyde.identification.choco.interfaces.ChocoModelMixin
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat
import scala.collection.mutable.HashMap

trait SDFTimingAnalysisSASMixin extends ChocoModelMixin {

  def actors: Array[Int]
  def schedulers: Array[Int]

  def maxRepetitionsPerActors(actorId: Int): Int

  def actorMapping(actorId: Int)(schedulerId: Int): BoolVar
  def startLatency(schedulerId: Int): IntVar
  def slotMaxDuration(schedulerId: Int): IntVar
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

  class SASTimingAndTokensPropagator(
      val maxFiringsPerActor: Array[Int],
      val balanceMatrix: Array[Array[Int]],
      val initialTokens: Array[Int],
      val actorDuration: Array[Array[Int]],
      val firingsInSlots: Array[Array[Array[IntVar]]],
      val initialLatencies: Array[IntVar],
      val slotMaxDurations: Array[IntVar],
      val slotPeriods: Array[IntVar],
      val globalThroughput: IntVar
  ) extends Propagator[IntVar](
        firingsInSlots.flatten.flatten ++ initialLatencies ++ slotMaxDurations ++ slotPeriods :+ globalThroughput,
        PropagatorPriority.BINARY,
        false
      ) {

    val actors     = 0 until maxFiringsPerActor.size
    val schedulers = 0 until slotPeriods.size
    val slots      = 0 until firingsInSlots.head.head.size

    // these two are created here so that they are note recreated every time
    // wasting time with memory allocations etc
    val possibleFirings = maxFiringsPerActor.clone()
    val tokens          = initialTokens.clone()
    var start           = firingsInSlots.map(r => r.map(_ => initialLatencies.map(_.getUB()).min))
    var duration        = firingsInSlots.map(r => r.map(_ => 0))
    var finish          = firingsInSlots.map(r => r.map(_ => initialLatencies.map(_.getUB()).min))
    val stateTime       = HashMap(0 -> initialTokens.clone())
    def propagate(evtmask: Int): Unit = {
      // make sure that the slots
      val latestSlot = schedulers
        .map(p => slots.indexWhere(q => actors.exists(a => firingsInSlots(a)(p)(q).getLB() > 0)))
        .max
      // then nullify all previous slots
      for (
        q <- slots.filter(_ < latestSlot);
        p <- schedulers
      ) {
        if (!actors.exists(a => firingsInSlots(a)(p)(q).getLB() > 0))
          actors.foreach(a => firingsInSlots(a)(p)(q).updateUpperBound(0, this))
      }
      // first, we try to eliminate firings that are impossible
      for (
        q <- slots;
        p <- schedulers
      ) {}
    }

    def isEntailed(): ESat = {
      val allFired = firingsInSlots.zipWithIndex.forall((vs, a) =>
        vs.flatten.filter(v => v.isInstantiated()).map(v => v.getValue()).sum >= maxFiringsPerActor(
          a
        )
      )
      if (allFired) ESat.TRUE else ESat.UNDEFINED
    }

    def computeStateTime(): Unit = {
      stateTime.clear()
      stateTime += 0 -> initialTokens
      // we can do a simple for loop since we assume
      // that the schedules are SAS
      for (
        q <- slots;
        p <- schedulers;
        a <- actors;
        if firingsInSlots(a)(p)(q).isInstantiated();
        firings = firingsInSlots(a)(p)(q).getValue()
      ) {
        stateTime.foreach((t, b) => {
          // if ()
        })
      }
    }
  }

}
