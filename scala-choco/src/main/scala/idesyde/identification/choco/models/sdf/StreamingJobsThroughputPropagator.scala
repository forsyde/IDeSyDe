package idesyde.identification.choco.models.sdf

import scala.jdk.CollectionConverters._

import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat

import idesyde.choco.HasUtils
import scala.annotation.tailrec
import org.chocosolver.util.objects.graphs.DirectedGraph
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.cycle.JohnsonSimpleCycles
import scala.collection.mutable.Buffer
import org.jgrapht.graph.SimpleDirectedWeightedGraph
import scala.collection.mutable.Stack
import scala.collection.mutable.HashSet

class StreamingJobsThroughputPropagator(
    val nJobs: Int,
    val isSuccessor: (Int) => (Int) => Boolean,
    val hasDataCycle: (Int) => (Int) => Boolean,
    val jobOrdering: Array[IntVar],
    val jobMapping: Array[IntVar],
    val jobWeight: Array[IntVar],
    val edgeWeight: Array[Array[IntVar]],
    val jobMaxCycleLength: Array[IntVar]
) extends Propagator[IntVar](
      jobOrdering.toArray ++ jobMapping.toArray ++ edgeWeight.flatten.toArray ++ jobWeight.toArray,
      PropagatorPriority.CUBIC,
      false
    )
    with HasUtils {

  val minimumDistanceMatrix = Buffer.fill(nJobs)(0)
  // val maximumDistanceMatrix = Buffer.fill(nJobs)(Buffer.fill(nJobs)(0))
  var dfsStack = new Stack[Int](initialSize = nJobs)
  val visited  = Buffer.fill(nJobs)(false)
  val previous = Buffer.fill(nJobs)(-1)

  /** This function checks if jib i can be succeed by j, based on the original precedences and the
    * mapping + ordering ones.
    */
  def canSucceed(i: Int)(j: Int): Boolean = if (
    jobMapping(i)
      .stream()
      .anyMatch(jobMapping(j).contains(_))
  ) {
    jobOrdering(i).stream().anyMatch(oi => jobOrdering(j).contains(oi + 1))
  } else {
    isSuccessor(i)(j)
  }

  def mustLastToFirst(i: Int)(j: Int): Boolean =
    hasDataCycle(i)(j) || (jobMapping(j).isInstantiated() && jobMapping(i).isInstantiatedTo(
      jobMapping(j).getValue()
    )) && jobOrdering(j).isInstantiatedTo(0)

  def canLastToFirst(i: Int)(j: Int): Boolean =
    hasDataCycle(i)(j) || (jobMapping(i)
      .stream()
      .anyMatch(jobMapping(j).contains(_))) && jobOrdering(j).getLB() == 0 && jobOrdering(i)
      .getLB() > 0

  def mustSuceed(i: Int)(j: Int): Boolean = if (
    jobMapping(i).isInstantiated() && jobMapping(j)
      .isInstantiated() && jobMapping(i)
      .getValue() == jobMapping(j).getValue()
  ) {
    jobOrdering(i).stream().anyMatch(oi => jobOrdering(j).contains(oi + 1))
  } else {
    isSuccessor(i)(j)
  }

  def mustCycle(i: Int)(j: Int): Boolean =
    hasDataCycle(i)(j) ||
      (jobMapping(i).isInstantiated() && jobMapping(j).isInstantiated() && jobMapping(i)
        .getValue() == jobMapping(j).getValue() && jobOrdering(j)
        .getUB() == 0 && jobOrdering(i).getLB() > 0)

  def canCycle(i: Int)(j: Int): Boolean =
    jobMapping(i).stream().anyMatch(jobMapping(j).contains(_)) && jobOrdering(i)
      .getLB() < jobOrdering(j).getUB()

  final def countPathLength(prevs: Buffer[Buffer[Int]])(i: Int)(j: Int): Int = {
    var count = 0
    var k     = j
    while (k != i) {
      count += 1
      k = prevs(i)(k)
    }
    count
  }

  private final def minCommTimes(i: Int)(j: Int): Int = if (
    jobMapping(i).isInstantiated() && jobMapping(j)
      .isInstantiated() && jobMapping(i)
      .getValue() == jobMapping(j).getValue()
  ) {
    0
  } else {
    edgeWeight(i)(j).getLB()
  }

  def propagate(evtmask: Int): Unit = {
    // println(getModel().getSolver().getDecisionPath().toString())
    wfor(0, _ < nJobs, _ + 1) { src =>
      // this is used instead of popAll in the hopes that no list is allocated
      while (!dfsStack.isEmpty) dfsStack.pop()
      wfor(0, _ < nJobs, _ + 1) { j =>
        visited(j) = false
        previous(j) = -1
        minimumDistanceMatrix(j) = Int.MinValue
      }
      dfsStack.push(src)
      while (!dfsStack.isEmpty) {
        val i = dfsStack.pop()
        if (!visited(i)) {
          visited(i) = true
          wfor(0, _ < nJobs, _ + 1) { j =>
            if (mustSuceed(i)(j) || mustCycle(i)(j)) { // adjacents
              if (j == src) {                          // found a cycle
                minimumDistanceMatrix(i) = jobWeight(i).getLB() + minCommTimes(i)(j)
                var k = i
                // go backwards until the src
                while (k != src) {
                  val kprev = previous(k)
                  minimumDistanceMatrix(kprev) = Math.max(
                    minimumDistanceMatrix(kprev),
                    jobWeight(kprev).getLB() + minCommTimes(kprev)(k) + minimumDistanceMatrix(k)
                  )
                  k = kprev
                }
              } else if (visited(j) && minimumDistanceMatrix(j) > Int.MinValue) { // found a previous cycle
                var k = j
                // go backwards until the src
                while (k != src) {
                  val kprev = previous(k)
                  minimumDistanceMatrix(kprev) = Math.max(
                    minimumDistanceMatrix(kprev),
                    jobWeight(kprev).getLB() + minCommTimes(kprev)(k) + minimumDistanceMatrix(k)
                  )
                  k = kprev
                }
              } else if (!visited(j)) {
                dfsStack.push(j)
                previous(j) = i
              }
            }
          }
        }
      }
      jobMaxCycleLength(src).updateLowerBound(minimumDistanceMatrix(src), this)
    }
  }

  def isEntailed(): ESat = if (
    (0 until nJobs).map(jobMaxCycleLength(_)).forall(_.isInstantiated())
  ) then ESat.TRUE
  else ESat.UNDEFINED

}
