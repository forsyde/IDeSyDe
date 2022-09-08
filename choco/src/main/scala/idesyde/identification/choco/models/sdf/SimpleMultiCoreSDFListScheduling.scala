package idesyde.identification.choco.models.sdf

import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.search.strategy.decision.IntDecision
import org.chocosolver.util.PoolManager
import org.chocosolver.solver.search.strategy.decision.Decision
import breeze.linalg._
import org.chocosolver.solver.search.strategy.assignments.DecisionOperatorFactory
import scala.util.Random
import org.chocosolver.solver.search.loop.monitors.IMonitorSolution
import idesyde.utils.CoreUtils.wfor

class SimpleMultiCoreSDFListScheduling(
    val maxFiringsPerActor: Array[Int],
    val balanceMatrix: Array[Array[Int]],
    val initialTokens: Array[Int],
    val actorDuration: Array[Array[Int]],
    val channelsTravelTime: Array[Array[Array[IntVar]]],
    val firingsInSlots: Array[Array[Array[IntVar]]]
) extends AbstractStrategy[IntVar]((firingsInSlots.flatten.flatten): _*)  with IMonitorSolution {

  var communicationLimiter = 0
  var channels   = (0 until initialTokens.size).toArray
  var actors     = (0 until maxFiringsPerActor.size).toArray
  var schedulers = (0 until firingsInSlots.head.size).toArray
  val slots      = (0 until firingsInSlots.head.head.size).toArray

  val mat         = CSCMatrix(balanceMatrix: _*)

  // val firingVector: Array[SparseVector[Int]] = slots.map(slot => SparseVector.zeros[Int](actors.size)).toArray

  val singleActorFire = actors.map(a => (0 to maxFiringsPerActor(a)).map(q => SDFChocoRecomputeMethods.mkSingleActorFire(a)(q)).toArray).toArray

  val pool = PoolManager[IntDecision]()

  var tokenVec = SparseVector(initialTokens)
  var accumFirings = SparseVector.zeros[Int](actors.size)

  def getDecision(): Decision[IntVar] = {
    // val schedulersShuffled = Random.shuffle(schedulers)
    var bestA = -1
    var bestP = -1
    var bestQ = -1
    var bestSlot = -1
    val lastSlot = SDFChocoRecomputeMethods.recomputeLowerestDecidedSlot()
    // quick guard for errors and end of schedule
    if (lastSlot < -1 || lastSlot >= slots.size - 1) return null
    val earliestOpen = lastSlot + 1
    var d = pool.getE()
    if (d == null) d = IntDecision(pool)
    // count += 1
    // println(s"deciding at $earliestOpen")
    SDFChocoRecomputeMethods.recomputeFiringVectors(firingsInSlots)(firingVector, firingVectorPerCore)
    SDFChocoRecomputeMethods.recomputeTokens(mat)(firingVector, firingVectorPerCore)(tokensBefore, tokensAfter)
    // for (a <- actors) accumFirings(a) = 0
    // for (slot <- slots; if bestSlot < 0) {
      // accumulate the firings vectors
    // accumFirings += firingVector(lastSlot)
    wfor(0, _ < slots.size, _ + 1) {s => 
      wfor(0, _ < schedulers.size, _ + 1) { p => 
        if (!slotAtSchedulerIsTaken(p)(s)) {
          wfor(0, _ < actors.size, _ + 1) { a => 
            wfor(firingsInSlots(a)(p)(s).getUB, _ > 0, _ - 1) { q => 
              tokenVec = consMat * (singleActorFire(a)(q) + firingVector(s)) + tokensBefore(s)
              if (min(tokenVec) >= 0 && q > bestQ) {
                bestA = a
                bestP = p
                bestQ = q
                bestSlot = s
              }
            }
          }
        }
      }
    }
    // slotsPrettyPrint()
    if (bestQ > 0) then {
      // println("adding new firing:  " + (bestA, bestP, bestSlot, bestQ))
      d.set(firingsInSlots(bestA)(bestP)(bestSlot), bestQ, DecisionOperatorFactory.makeIntEq())
      d
    } else {
      // println("returning null")
      null
    }
  }

  def onSolution(): Unit = {
    schedulers = Random.shuffle(schedulers).toArray
    actors = Random.shuffle(actors).toArray
    channels = Random.shuffle(channels).toArray
    communicationLimiter = Math.min(communicationLimiter + 1, actors.size * schedulers.size)
  }

}
