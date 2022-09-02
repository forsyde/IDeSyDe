package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.search.strategy.decision.IntDecision
import org.chocosolver.util.PoolManager
import org.chocosolver.solver.search.strategy.decision.Decision
import breeze.linalg._
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory
import scala.util.Random

class SimpleMultiCoreSDFListScheduling(
    val maxFiringsPerActor: Array[Int],
    val balanceMatrix: Array[Array[Int]],
    val initialTokens: Array[Int],
    val actorDuration: Array[Array[Int]],
    val channelsTravelTime: Array[Array[Array[IntVar]]],
    val firingsInSlots: Array[Array[Array[IntVar]]]
) extends AbstractStrategy[IntVar]((firingsInSlots.flatten.flatten): _*) with SDFChocoRecomputeMethodsMixin {

  val channels   = (0 until initialTokens.size).toArray
  val actors     = (0 until maxFiringsPerActor.size).toArray
  val schedulers = (0 until firingsInSlots.head.size).toArray
  val slots      = (0 until firingsInSlots.head.head.size).toArray

  val mat         = CSCMatrix(balanceMatrix: _*)

  // val firingVector: Array[SparseVector[Int]] = slots.map(slot => SparseVector.zeros[Int](actors.size)).toArray

  val singleActorFire = actors.map(a => (0 to maxFiringsPerActor(a)).map(q => mkSingleActorFire(a)(q)).toArray).toArray

  val pool = PoolManager[IntDecision]()

  var bestA = -1
  var bestP = -1
  var bestQ = -1
  var bestSlot = -1
  var tokenVec = SparseVector(initialTokens)
  var accumFirings = SparseVector.zeros[Int](actors.size)

  def getDecision(): Decision[IntVar] = {
    // val schedulersShuffled = Random.shuffle(schedulers)
    var d = pool.getE()
    if (d == null) d = IntDecision(pool)
    // count += 1
    // println("deciding")
    bestA = -1
    bestP = -1
    bestQ = -1
    bestSlot = -1
    recomputeFiringVectors()
    recomputeTokensAndTime()
    for (a <- actors) accumFirings(a) = 0
    for (slot <- slots; if bestSlot < 0) {
      // accumulate the firings vectors
      accumFirings += firingVector(slot)
      for (p <- schedulers; if !slotAtSchedulerIsTaken(p)(slot)) {
        for (a <- actors; q <- firingsInSlots(a)(p)(slot).getUB to 1 by -1; if q + accumFirings(a) <= maxFiringsPerActor(a)) {
          tokenVec = consMat * singleActorFire(a)(q) + tokensBefore(slot)
          if (min(tokenVec) >= 0 && q > bestQ) {
            bestA = a
            bestP = p
            bestQ = q
            bestSlot = slot
          }
        }
      }
    }
    // slotsPrettyPrint()
    if (bestSlot > -1 && bestQ > 0) then {
      // println("adding new firing:  " + (bestA, bestP, bestSlot, bestQ))
      d.set(firingsInSlots(bestA)(bestP)(bestSlot), bestQ, DecisionOperatorFactory.makeIntEq())
      d
    } else {
      // println("returning null")
      null
    }
  }

}