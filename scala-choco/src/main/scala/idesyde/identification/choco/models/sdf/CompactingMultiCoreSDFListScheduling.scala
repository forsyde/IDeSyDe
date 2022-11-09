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
import idesyde.utils.CoreUtils
import idesyde.identification.forsyde.models.sdf.SDFApplication
import org.chocosolver.solver.variables.BoolVar

class CompactingMultiCoreSDFListScheduling(
    val sdfApplications: SDFApplication,
    val actorDuration: Array[Array[Int]],
    val tileDistances: Array[Array[Int]],
    val firingsInSlots: Array[Array[Array[BoolVar]]],
    val invThroughputs: Array[IntVar],
    val globalInvThroughput: IntVar
) extends AbstractStrategy[IntVar]((firingsInSlots.flatten.flatten): _*)
    with IMonitorSolution {

  private val recomputeMethods = SDFChocoRecomputeMethods(firingsInSlots)

  private val numActors     = firingsInSlots.size
  private val numSchedulers = firingsInSlots.head.size
  private val numSlots      = firingsInSlots.head.head.size
  var channels              = (0 until sdfApplications.initialTokens.size).toArray
  var actors                = sdfApplications.decreasingActorConsumptionOrder
  var schedulers            = (0 until firingsInSlots.head.size).toArray
  val slots                 = (0 until firingsInSlots.head.head.size).toArray

  private val mat     = DenseMatrix(sdfApplications.sdfBalanceMatrix: _*)
  private val consMat = mat.map(v => if (v < 0) then v else 0)
  private val prodMat = mat.map(v => if (v > 0) then v else 0)

  val pool = PoolManager[IntDecision]()

  val follows = actors.map(src =>
    actors.map(dst => {
      channels.exists(c => mat(c, src) > 0 && mat(c, dst) < 0)
    })
  )

  val followsMatrix = CSCMatrix(
    follows: _*
  )

  val followsClosure = CoreUtils.reachibilityClosure(follows)

  /** This function checks whether the firing schedule can induce a self-contention between actors
    * of the same sub-application, i.e. that are connected. The logic it uses is to use the provided
    * scheduler ordering an always suggest to map actors in a feed-forward manner.
    *
    * @param actor
    *   @param scheduler
    * @param slot
    *   @return whether firing the actor at this scheduler and slot can produce a self-contention
    */
  def calculateDistanceScore(actor: Int)(scheduler: Int)(slot: Int): Int = {
    var score = 0
    val disjointComponentActor = sdfApplications.sdfDisjointComponents.indexWhere(comp => comp.contains(actor))
    // first calculate the distance from current slot to dependent ones
    wfor(0, _ < numActors, _ + 1) { a =>
      val disjointComponentA = sdfApplications.sdfDisjointComponents.indexWhere(comp => comp.contains(a))
      // check whether a is a predecessor of actor in previous slots
      if (a != actor && disjointComponentA == disjointComponentActor) {
        wfor(0, _ <= slot, _ + 1) { slotA =>
          wfor(0, _ < numSchedulers, _ + 1) { p =>
            if (
              firingsInSlots(a)(p)(slotA).isInstantiated() && firingsInSlots(a)(p)(slotA).getLB() > 0
            ) {
              score += tileDistances(p)(scheduler)
            }
          }
        }
      }
      // now check for higher distances in the same slot
      else if (a != actor && disjointComponentA != disjointComponentActor) {
        wfor(0, _ < numSchedulers, _ + 1) { p =>
          if (
            p != scheduler && firingsInSlots(a)(p)(slot).isInstantiated() && firingsInSlots(a)(p)(slot).getLB() > 0
          ) {
            score -= tileDistances(p)(scheduler)
          }
        }
      }
    }
    score
  }

  private def availableActors(slot: Int): Int = {
    var num = 0
    var aIsAvailable = false
    wfor(0, _ < numActors, _ + 1) { a =>
      aIsAvailable = false
      wfor(0, _ < numSchedulers && !aIsAvailable, _ + 1) { p =>
        aIsAvailable = firingsInSlots(a)(p)(slot).getUB() > 0
      }
      if (aIsAvailable) {
        num += 1
      }
    }
    num
  }

  def getDecision(): Decision[IntVar] = {
    // val schedulersShuffled = Random.shuffle(schedulers)
    // normal
    var bestACompact     = -1
    var bestPCompact     = -1
    var bestQCompact     = -1
    var bestSlotCompact  = slots.size
    var bestScoreCompact = Int.MaxValue
    var bestCompactMetric = Int.MaxValue
    // normal not-compact
    var bestA     = -1
    var bestP     = -1
    var bestQ     = -1
    var bestSlot  = slots.size
    var bestScore = Int.MaxValue
    // penalized
    var bestAPenalized     = -1
    var bestPPenalized     = -1
    var bestQPenalized     = -1
    var bestSlotPenalized  = slots.size
    var bestScorePenalized = Int.MaxValue
    // other auxliaries
    var score              = 0
    var currentParallelism = 0
    // var bestSlot = -1
    val lastSlot = recomputeMethods.recomputeLowerestDecidedSlot()
    // println(s"deciding with last slot $lastSlot")
    // quick guard for errors and end of schedule
    if (lastSlot >= slots.size - 1) return null
    val earliestOpen = lastSlot + 1
    val horizon = availableActors(earliestOpen)
    var d = pool.getE()
    if (d == null) d = IntDecision(pool)
    // println(s"deciding at $earliestOpen with $horizon")
    wfor(earliestOpen, it => it < numSlots && it <= earliestOpen + horizon, _ + 1) { s =>
      wfor(0, _ < schedulers.size, _ + 1) { p =>
        wfor(0, _ < actors.size, _ + 1) { a =>
          if (firingsInSlots(a)(p)(s).getUB() > 0 && !firingsInSlots(a)(p)(s).isInstantiated()) {
            // compute the best slot otherwise
            // currentComRank = if (currentComRank <= communicationLimiter) then 0 else currentComRank - communicationLimiter
            // chains of lexographic greedy objectives
            score = invThroughputs(p).getLB() + actorDuration(a)(p) 
            val globalScore = Math.max(score, globalInvThroughput.getLB())
            val antiCompactness = calculateDistanceScore(a)(p)(s)
            if (s < bestSlot || 
              (s == bestSlot && antiCompactness < bestCompactMetric) ||
              (s == bestSlot && antiCompactness == bestCompactMetric && globalScore < bestScoreCompact)
            ) { // this ensures we load the same cpu first
              bestACompact = a
              bestPCompact = p
              bestQCompact = 1
              bestSlotCompact = s
              bestScoreCompact = globalScore
              bestCompactMetric = antiCompactness
            }
            if (s < bestSlot || (s == bestSlot && globalScore < bestScore)) { // this ensures we load the same cpu first
              bestA = a
              bestP = p
              bestQ = 1
              bestSlot = s
              bestScore = globalScore
            }
            if (globalScore < bestScorePenalized) {
              bestAPenalized = a
              bestPPenalized = p
              bestQPenalized = 1
              bestSlotPenalized = s
              bestScorePenalized = globalScore
            }
          }
        }
      }
    }
    // }
    // recomputeMethods.slotsPrettyPrint()
    // recomputeMethods.schedulePrettyPrint()
    // println(invThroughputs.map(_.getLB()).mkString(", ") + " ;; " + globalInvThroughput.getLB())
    // println("bestCompact:  " + (bestACompact, bestPCompact, bestSlotCompact, bestQCompact, bestScoreCompact, bestCompactMetric))
    // println("best:  " + (bestA, bestP, bestSlot, bestQ, bestScore))
    // println(
    //   "bestPenalized:  " + (bestAPenalized, bestPPenalized, bestSlotPenalized, bestQPenalized, bestScorePenalized)
    // )
    if (bestQCompact > 0) {
      // println("adding it!")
      d.set(firingsInSlots(bestACompact)(bestPCompact)(bestSlotCompact), bestQCompact, DecisionOperatorFactory.makeIntEq())
      d
    } else if (bestQ > 0) {
      // println("adding it!")
      d.set(firingsInSlots(bestA)(bestP)(bestSlot), bestQ, DecisionOperatorFactory.makeIntEq())
      d
    } else if (bestQPenalized > 0) {
      d.set(
        firingsInSlots(bestAPenalized)(bestPPenalized)(bestSlotPenalized),
        bestQPenalized,
        DecisionOperatorFactory.makeIntEq()
      )
      d
    } else {
      null
    }
  }

  def onSolution(): Unit = {
    // schedulers = Random.shuffle(schedulers).toArray
    // val model = globalInvThroughput.getModel()
    // val (_, maxSched) = invThroughputs.zipWithIndex.maxBy((v, _) => v.getLB())
    // println("blacklist: " + maxSched)
    // model.arithm(invThroughputs(maxSched), "<", invThroughputs(maxSched).getLB()).post()
    // model.or(
    //   ,
    //   model.arithm(globalInvThroughput, "<", globalInvThroughput.getLB())
    // ).post()
    
    // invThroughputs.zipWithIndex.filter((v, i) => v.getLB() == 0).foreach((v, i) => schedulerWhiteListing(i) = false)
    // if (schedulerWhiteListing.count(p => p) > 1) {
    // }
    // actors = Random.shuffle(actors).toArray
    // channels = Random.shuffle(channels).toArray
    // parallelism = Math.min(parallelism + 1, actors.size * schedulers.size)
  }

}
