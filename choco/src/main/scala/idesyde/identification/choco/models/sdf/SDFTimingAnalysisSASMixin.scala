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

  def allSlotsForActor(a: Int) =
    (0 until firingsInSlots.head.head.size)
      .map(slot =>
        (0 until schedulers.size).map(s => numActorsScheduledSlotsInStaticCyclic(a)(s)(slot)).toArray
      )
      .toArray
  
  
  def slots(ai: Int)(sj: Int) =
    (0 until firingsInSlots.head.head.size).map(numActorsScheduledSlotsInStaticCyclic(ai)(sj)(_)).toArray
  
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
      slot <- 0 until firingsInSlots.head.head.size
    ) {
      chocoModel.atMostNValues(allInSlot(sj)(slot), chocoModel.intVar(2), true).post()
    }
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
      ) {

    val channels   = 0 until initialTokens.size
    val actors     = 0 until maxFiringsPerActor.size
    val schedulers = 0 until slotPeriods.size
    val slots      = 0 until firingsInSlots.head.head.size

    def actorArrayOfSlot(p: Int)(i: Int) = firingsInSlots.map(as => as(p)(i))

    // these two are created here so that they are note recreated every time
    // wasting time with memory allocations etc
    val tokens = slots.map(_ => DenseVector.zeros[Int](channels.size)).toArray
    // val tokensAfter =
    //     slots.map(_ => DenseVector.zeros[Int](channels.size)).toArray
    val startTimes  = schedulers.map(_ => slots.map(_ => 0).toArray).toArray
    val finishTimes = schedulers.map(_ => slots.map(_ => 0).toArray).toArray
    val mat     = DenseMatrix(balanceMatrix: _*)
    val prodMat = mat.map(f => if (f >= 0) then f else 0)
    val consMat = mat.map(f => if (f <= 0) then f else 0)

    def singleActorFire(a: Int)(q: Int): DenseVector[Int] = {
      val v = DenseVector.zeros[Int](actors.size)
      v(a) = q
      v
    }

    def slotIsTaken(p: Int)(slot: Int): Boolean = actors.exists(a => firingsInSlots(a)(p)(slot).getLB() > 0)

    def firingVector(slot: Int): DenseVector[Int] = DenseVector(
          actors
            .map(a => schedulers.map(p => firingsInSlots(a)(p)(slot).getLB()).find(_ > 0).getOrElse(0))
            .toArray
        )
    
    def computeTokensAndTime() : Unit = {
      // for the very first initial conditions
      tokens(0) = mat * firingVector(0) + DenseVector(initialTokens)
      for (p <- schedulers) {
        finishTimes(p)(0) = actors.map(a => firingVector(0)(a) * actorDuration(a)(p)).sum + startTimes(p)(0)
      }
      // work based first on the slots and update the timing whenever possible
      for (
        i <- slots.drop(1)
        // p <- schedulers
      ) {
        // println(s"at ${i} : ${firingVector(i)}")
        tokens(i) = mat * firingVector(i) + tokens(i - 1)
        for (p <- schedulers) {
          startTimes(p)(i) =
            if (i > 0) then
              schedulers
                .filter(_ != p)
                .map(pp => (pp, tokens(i) - tokens(i - 1)))
                .map((pp, b) =>
                  // this is the actual essential equation
                  finishTimes(pp)(i - 1) + channels
                    .map(c => b(c) * channelsTravelTime(c)(pp)(p).getUB())
                    .sum
                )
                .max
            else 0
          finishTimes(p)(i) = actors.map(a => firingVector(i)(a) * actorDuration(a)(p)).sum + startTimes(p)(i)
        }
      }
    }

    def propagate(evtmask: Int): Unit = {
      println("propagate")
      computeTokensAndTime()
      println(
      schedulers.map(s => {
        slots.map(slot => {
          actors.map(a => firingsInSlots(a)(s)(slot).getLB() + "|" + firingsInSlots(a)(s)(slot).getUB()).mkString("(", ", ", ")")
        }).mkString("[", ", ", "]")
      }).mkString("[\n ", "\n ", "\n]")
      )      
      // work based first on the slots and update the timing whenever possible
      for (
        i <- slots
        // p <- schedulers
      ) {
        // there is a mapping
        // if (mappedA.isDefined) {
        // val a = mappedA.get
        // println((a, p, i, firingsInSlots(a)(p)(i).getValue()))
        // firingVector(a) = firingsInSlots(a)(p)(i).getValue()
        // if (i < slots.size - 1) then tokens(i + 1) = mat * firingVector + tokens(i)
        // if (i < slots.size - 1) then tokensBefore(p)(i + 1) = tokensAfter(p)(i)
        // redue the tokens before for these parallel slots
        // for (pp <- schedulers) tokensBefore(pp)(i) -= prodMat * firingVector

        // } else
        for (p <- schedulers) {
          if (i == 0) { // there is still no mapping and the slots can be reduced
            for (
              a <- actors;
              q <- firingsInSlots(a)(p)(i).getUB() to 1 by -1
            ) {
              // firingVector(a) = q
              val afterCons = consMat * singleActorFire(a)(q) + DenseVector(initialTokens)
              if (min(afterCons) < 0) {
                println("taking out " + (a, p, i, q) + " because " + afterCons.toString)
                firingsInSlots(a)(p)(i).updateUpperBound(q - 1, this)
              }
              // firingVector(a) = 0
            }
          } else if(slotIsTaken(p)(i - 1)) { // there is still no mapping and the slots can be reduced
            for (
              a <- actors;
              q <- firingsInSlots(a)(p)(i).getUB() to 1 by -1
            ) {
              // firingVector(a) = q
              val afterCons = consMat * singleActorFire(a)(q) + tokens(i - 1)
              if (min(afterCons) < 0) {
                println("taking out " + (a, p, i, q) + " because " + afterCons.toString)
                firingsInSlots(a)(p)(i).updateUpperBound(q - 1, this)
              }
              // firingVector(a) = 0
            }
          }
        }
      }
      // now try to bound the timing,
      globalInvThroughput.updateLowerBound(
        schedulers
          .map(p => {
            finishTimes(p).max - startTimes(p).min
          })
          .max,
        this
      )
      
    }

    def isEntailed(): ESat = {
      println("checking entailment")
      computeTokensAndTime()
      // work based first on the slots and update the timing whenever possible
      for (
        i <- slots.drop(1)
        // p <- schedulers
      ) {
        // actors.exists(a => firingVector(i).)
        if (min(consMat * firingVector(i) + tokens(i-1)) < 0) then return ESat.FALSE
      }
      val allFired = firingsInSlots.zipWithIndex.forall((vs, a) =>
        vs.flatten.filter(v => v.isInstantiated()).map(v => v.getValue()).sum >= maxFiringsPerActor(
          a
        )
      )
      if (allFired) then 
        if (tokens.last == DenseVector(initialTokens)) then ESat.TRUE else ESat.FALSE
      else  ESat.UNDEFINED
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
