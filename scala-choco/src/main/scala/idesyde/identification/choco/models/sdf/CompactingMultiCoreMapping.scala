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
import spire._
import spire.math._
import spire.implicits._

class CompactingMultiCoreMapping[DistT](
    val processorsDistances: Array[Array[DistT]],
    val processesComponents: Array[Int],
    val processesMappings: Array[IntVar],
    val processesIsFollowedBy: (Int) => (Int) => Boolean
    // val durations: Array[IntVar]
    // val invThroughputs: Array[IntVar],
)(using distT: spire.math.Integral[DistT])
    extends AbstractStrategy[IntVar](processesMappings: _*) {

  private val numProcesses: Int  = processesMappings.size
  private val numSchedulers: Int = processorsDistances.size

  val pool = PoolManager[IntDecision]()

  /** This function checks whether the firing schedule can induce a self-contention between actors
    * of the same sub-application, i.e. that are connected. The logic it uses is to use the provided
    * scheduler ordering an always suggest to map actors in a feed-forward manner.
    *
    * @param actor
    *   @param scheduler
    * @param slot
    *   @return whether firing the actor at this scheduler and slot can produce a self-contention
    */
  def calculateDistanceScore(processIdx: Int)(scheduler: Int): DistT = {
    var score = distT.zero
    // first calculate the distance from current slot to dependent ones
    wfor(0, _ < numProcesses, _ + 1) { p =>
      // check whether a is a predecessor of actor in previous slots
      if (p != processIdx && processesComponents(p) == processesComponents(processIdx)) {
        wfor(0, _ < numSchedulers, _ + 1) { s =>
          if (s != scheduler && processesMappings(p).isInstantiatedTo(s)) {
            score = score + processorsDistances(s)(scheduler)
          }
        }
      }
      // now check for higher distances in the same slot
      else if (p != processIdx && processesComponents(p) != processesComponents(processIdx)) {
        wfor(0, _ < numSchedulers, _ + 1) { s =>
          if (s != scheduler && processesMappings(p).isInstantiatedTo(s)) {
            score = distT.minus(score, processorsDistances(s)(scheduler))
          }
        }
      }
    }
    score
  }

  def calculateCrossings(processorIdx: Int)(scheduler: Int): Int = {
    var crossings = 0
    wfor(0, _ < numProcesses, _ + 1) { other =>
      if (processesIsFollowedBy(other)(processorIdx) && !processesMappings(other).contains(scheduler)) {
        wfor(0, _ < numProcesses, _ + 1) {prevOther => 
          if (processesIsFollowedBy(prevOther)(other) && processesMappings(prevOther).isInstantiatedTo(scheduler)) {
            crossings += 1
          }
        }
      } 
    }
    crossings 
  }

  def getDecision(): Decision[IntVar] = {
    // val schedulersShuffled = Random.shuffle(schedulers)
    // normal
    var bestPCompact     = -1
    var bestSCompact     = -1
    var bestScoreCompact = (distT.fromInt(Int.MaxValue), Int.MaxValue)
    // var bestDuration = Int.MaxValue
    wfor(0, _ < numProcesses, _ + 1) { job =>
      if (!processesMappings(job).isInstantiated()) {
        wfor(processesMappings(job).getLB(), _ <= processesMappings(job).getUB(), _ + 1) { s =>
          if (processesMappings(job).contains(s)) {
            val score = (calculateDistanceScore(job)(s), calculateCrossings(job)(s))
            // val invTh = invThroughputs(s).getLB() + durations(job).getUB()
            if (bestPCompact == -1 || score < bestScoreCompact) { // || (score == bestScoreCompact && durations(job).getUB() < bestDuration)) {
              bestPCompact = job
              bestSCompact = s
              bestScoreCompact = score
              // bestDuration = durations(job).getUB()
            }
          }
        }
      }
    }
    // println(s"${bestPCompact} - ${bestSCompact} : ${bestScoreCompact}")
    if (bestPCompact > -1) {
      var d = pool.getE()
      if (d == null) d = IntDecision(pool)
      d.set(
        processesMappings(bestPCompact),
        bestSCompact,
        DecisionOperatorFactory.makeIntEq()
      )
      d
    } else {
      null
    }
  }

}
