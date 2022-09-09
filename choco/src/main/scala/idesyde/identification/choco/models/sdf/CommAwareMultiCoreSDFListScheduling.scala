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

class CommAwareMultiCoreSDFListScheduling(
    val maxFiringsPerActor: Array[Int],
    val balanceMatrix: Array[Array[Int]],
    val initialTokens: Array[Int],
    val actorDuration: Array[Array[Int]],
    val channelsTravelTime: Array[Array[Array[IntVar]]],
    val firingsInSlots: Array[Array[Array[IntVar]]],
    val invThroughputs: Array[IntVar]
) extends AbstractStrategy[IntVar]((firingsInSlots.flatten.flatten): _*)
    with IMonitorSolution {

  private val recomputeMethods = SDFChocoRecomputeMethods(firingsInSlots)

  private val numActors     = firingsInSlots.size
  private val numSchedulers = firingsInSlots.head.size
  private val numSlots      = firingsInSlots.head.head.size
  private val numChannels   = channelsTravelTime.size
  var channels              = (0 until initialTokens.size).toArray
  var actors                = (0 until maxFiringsPerActor.size).toArray
  var schedulers            = (0 until firingsInSlots.head.size).toArray
  val slots                 = (0 until firingsInSlots.head.head.size).toArray

  private val mat     = DenseMatrix(balanceMatrix: _*)
  private val consMat = mat.map(v => if (v < 0) then v else 0)
  private val prodMat = mat.map(v => if (v > 0) then v else 0)

  // val firingVector: Array[SparseVector[Int]] = slots.map(slot => SparseVector.zeros[Int](actors.size)).toArray

  var parallelism = 0

  val singleActorFire =
    actors
      .map(a =>
        (0 to maxFiringsPerActor(a)).map(q => recomputeMethods.mkSingleActorFire(a)(q)).toArray
      )
      .toArray

  val pool = PoolManager[IntDecision]()

  var tokenVec     = DenseVector(initialTokens)
  var accumFirings = DenseVector.zeros[Int](actors.size)

  val follows = CSCMatrix(
    actors.map(src =>
      actors.map(dst => {
        channels.exists(c => mat(c, src) > 0 && mat(c, dst) < 0)
      })
    ): _*
  )

  def getDecision(): Decision[IntVar] = {
    // val schedulersShuffled = Random.shuffle(schedulers)
    // normal
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
    var d            = pool.getE()
    if (d == null) d = IntDecision(pool)
    // count += 1
    // println(s"deciding at $earliestOpen")
    // slotsPrettyPrint()
    // recomputeFiringVectors()
    // recomputeTokens()
    // recomputeInvThroughput()
    // for (a <- actors) accumFirings(a) = 0
    // for (slot <- slots; if bestSlot < 0) {
    // accumulate the firings vectors
    // accumFirings += firingVector(lastSlot)
    wfor(earliestOpen, it => it < numSlots && it <= bestSlot, _ + 1) { s =>
      wfor(0, _ < schedulers.size, _ + 1) { p =>
        wfor(0, _ < actors.size, _ + 1) { a =>
          if (firingsInSlots(a)(p)(s).getUB() > 0 && !firingsInSlots(a)(p)(s).isInstantiated()) {
            // compute ComRank
            currentParallelism = 0
            if (s > 0) {
              wfor(0, _ < actors.size, _ + 1) { aOther =>
                if (a != aOther) {
                  wfor(0, _ < schedulers.size, _ + 1) { pOther =>
                    if (p != pOther) {
                      wfor(0, _ < s, _ + 1) { sBefore =>
                        if (
                          follows(aOther, a) && firingsInSlots(aOther)(
                            pOther
                          )(sBefore).getLB() > 0
                        ) {
                          currentParallelism += 1
                        }
                      }
                    }
                  }
                }

              }
            }
            // compute the best slot otherwise
            wfor(firingsInSlots(a)(p)(s).getUB(), it => it > 0 && it >= bestQ, _ - 1) { q =>
              // currentComRank = if (currentComRank <= communicationLimiter) then 0 else currentComRank - communicationLimiter
              // chains of lexographic greedy objectives
              score = (invThroughputs(p).getLB() + actorDuration(a)(p) * q)
              if (currentParallelism <= parallelism) {
                if (score < bestScore) {
                  bestA = a
                  bestP = p
                  bestQ = q
                  bestSlot = s
                  bestScore = score
                }
              }
              if (score < bestScorePenalized) {
                bestAPenalized = a
                bestPPenalized = p
                bestQPenalized = q
                bestSlotPenalized = s
                bestScorePenalized = score
              }
            }
          }
        }
      }
    }
    // }
    // slotsPrettyPrint()
    // timingPrettyPrint()
    // println(invThroughputPerSchedulers.mkString(", "))
    // println("best:  " + (bestA, bestP, bestSlot, bestQ, bestScore))
    // println(
    //   "bestPenalized:  " + (bestAPenalized, bestPPenalized, bestSlotPenalized, bestQPenalized, bestScorePenalized)
    // )
    if (bestQ > 0) then {
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
      // println("returning null")
    } else {
      null
    }
  }

  def onSolution(): Unit = {
    schedulers = Random.shuffle(schedulers).toArray
    actors = Random.shuffle(actors).toArray
    channels = Random.shuffle(channels).toArray
    parallelism = Math.min(parallelism + 1, actors.size * schedulers.size)
  }

}
