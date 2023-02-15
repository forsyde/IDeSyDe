package idesyde.identification.choco.models.sdf

import scala.jdk.CollectionConverters._

import org.chocosolver.solver.variables.BoolVar
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.constraints.Propagator
import org.chocosolver.solver.constraints.PropagatorPriority
import org.chocosolver.util.ESat

import idesyde.utils.CoreUtils.wfor
import scala.annotation.tailrec
import org.chocosolver.util.objects.graphs.DirectedGraph
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.alg.cycle.JohnsonSimpleCycles
import scala.collection.mutable.Buffer
import java.{util => ju}

class StreamingJobsThroughputPropagator(
    val nJobs: Int,
    val isSuccessor: (Int) => (Int) => Boolean,
    val hasDataCycle: (Int) => (Int) => Boolean,
    val jobOrdering: Array[IntVar],
    val jobMapping: Array[IntVar],
    val jobWeight: Array[IntVar],
    val edgeWeight: Array[Array[IntVar]],
    val jobThroughput: Array[IntVar]
) extends Propagator[IntVar](
      jobOrdering.toArray ++ jobMapping.toArray ++ edgeWeight.flatten.toArray ++ jobWeight.toArray,
      PropagatorPriority.CUBIC,
      false
    ) {

  val possibleFiringsGraph = {
    val g = new SimpleDirectedGraph[Int, DefaultEdge](classOf[DefaultEdge])
    for (i <- 0 until nJobs) g.addVertex(i)
    g
  }
  // val mustFiringsGraph = {
  //   val g = new SimpleDirectedGraph[Int, DefaultEdge](classOf[DefaultEdge])
  //   for (i <- 0 until nJobs) g.addVertex(i)
  //   g
  // }

  // val minimumDistanceMatrix = Array.fill(nJobs)(Array.fill(nJobs)(0))
  // val maximumDistanceMatrix = Array.fill(nJobs)(Array.fill(nJobs)(0))
  // val explored              = Array.fill(nJobs)(Array.fill(nJobs)(true))

  /** This function checks if jib i can be succeed by j, based on the original precedences and the
    * mapping + ordering ones.
    */
  def canSucceed(i: Int)(j: Int): Boolean =
    isSuccessor(i)(j) || hasDataCycle(i)(j) || (jobMapping(i)
      .stream()
      .anyMatch(jobMapping(j).contains(_)) && (jobOrdering(
      i
    ).stream().anyMatch(oi => jobOrdering(j).contains(oi + 1))))

  def isLastToFirst(i: Int)(j: Int): Boolean = jobMapping(i)
    .stream()
    .anyMatch(jobMapping(j).contains(_)) && jobOrdering(j).contains(0) && jobOrdering.zipWithIndex
    .filter((_, k) => k != i)
    .forall((vk, _) => vk.getLB() < jobOrdering(i).getUB())

  def mustSuceed(i: Int)(j: Int): Boolean =
    isSuccessor(i)(j) || hasDataCycle(i)(j) || (jobMapping(i).isInstantiated() && jobMapping(j)
      .isInstantiated() && jobMapping(i)
      .getValue() == jobMapping(j).getValue() && jobOrdering(i).isInstantiated() && jobOrdering(j)
      .isInstantiated() && jobOrdering(i).getValue() + 1 == jobOrdering(j).getValue())

  def mustCycle(i: Int)(j: Int): Boolean =
    hasDataCycle(i)(j) ||
      (jobMapping(i).isInstantiated() && jobMapping(j).isInstantiated() && jobMapping(i)
        .getValue() == jobMapping(j).getValue() && jobOrdering(i)
        .getUB() < jobOrdering(j).getLB())

  def canCycle(i: Int)(j: Int): Boolean =
    jobMapping(i).stream().anyMatch(jobMapping(j).contains(_)) && jobOrdering(i)
      .getLB() < jobOrdering(j).getUB()

  def propagate(evtmask: Int): Unit = {
    // reset the graph
    wfor(0, _ < nJobs, _ + 1) { i =>
      wfor(0, _ < nJobs, _ + 1) { j =>
        if (i != j && (canSucceed(i)(j) || isLastToFirst(i)(j))) {
          possibleFiringsGraph.addEdge(i, j)
        } else {
          possibleFiringsGraph.removeEdge(i, j)
        }
      // if (i != j && mustSuceed(i)(j)) {
      //   mustFiringsGraph.addEdge(i, j)
      // } else {
      //   mustFiringsGraph.removeEdge(i, j)
      // }
      }
    }
    // no enuemrate the cycles
    var minCycle                   = jobThroughput.map(_.getUB()).max
    var maxCycle                   = jobThroughput.map(_.getLB()).min
    var minCycleList: ju.List[Int] = ju.List.of()
    var maxCycleList: ju.List[Int] = ju.List.of()
    val couldCycleAlg              = JohnsonSimpleCycles(possibleFiringsGraph)
    // val mustCycleAlg               = JohnsonSimpleCycles(mustFiringsGraph)
    couldCycleAlg
      .findSimpleCycles()
      .forEach(cycle => {
        var ubCycleLength = 0
        wfor(0, _ < cycle.size() - 1, _ + 1) { i =>
          ubCycleLength = ubCycleLength + jobWeight(cycle.get(i))
            .getUB() + edgeWeight(cycle.get(i))(cycle.get(i + 1)).getUB()
        }
        ubCycleLength += jobWeight(cycle.get(cycle.size() - 1))
          .getUB() + edgeWeight(cycle.get(cycle.size() - 1))(cycle.get(0)).getUB()
        if (ubCycleLength > maxCycle) {
          maxCycle = ubCycleLength
          maxCycleList = cycle
        }
        var lbCycleLength = 0
        wfor(0, _ < cycle.size() - 1, _ + 1) { i =>
          lbCycleLength = lbCycleLength + jobWeight(cycle.get(i))
            .getLB() + edgeWeight(cycle.get(i))(cycle.get(i + 1)).getLB()
        }
        lbCycleLength += jobWeight(cycle.get(cycle.size() - 1))
          .getLB() + edgeWeight(cycle.get(cycle.size() - 1))(cycle.get(0)).getLB()
        if (lbCycleLength < minCycle) {
          minCycle = lbCycleLength
          minCycleList = cycle
        }
      })
    // println(mustFiringsGraph.toString())
    // println(possibleFiringsGraph.toString())
    // println(minCycleList.toString())
    // println(maxCycleList.toString())
    // println(minCycle)
    // println(maxCycle)
    // perform the bounding now,
    minCycleList.forEach(v => jobThroughput(v).updateLowerBound(minCycle, this))
    maxCycleList.forEach(v => jobThroughput(v).updateUpperBound(maxCycle, this))
    // // first set up the matrix again
    // wfor(0, _ < nJobs, _ + 1) { i =>
    //   wfor(0, _ < nJobs, _ + 1) { j =>
    //     if (i == j) {
    //       minimumDistanceMatrix(i)(j) = jobWeight(i).getLB()
    //       maximumDistanceMatrix(i)(j) = jobWeight(i).getUB()
    //       explored(i)(i) = true
    //     } else if (canSucceed(i)(j)) { // can be or is a sucessor
    //       minimumDistanceMatrix(i)(j) =
    //         jobWeight(i).getLB() + edgeWeight(i)(j).getLB() + jobWeight(j).getLB()
    //       maximumDistanceMatrix(i)(j) =
    //         jobWeight(i).getUB() + edgeWeight(i)(j).getUB() + jobWeight(j).getUB()
    //       explored(i)(j) = true
    //     } else {
    //       wfor(0, _ < nJobs, _ + 1) { k =>
    //         if (j != k && canSucceed(i)(k) && explored(k)(j)) {
    //           minimumDistanceMatrix(i)(j) = Math.max(
    //             minimumDistanceMatrix(i)(j),
    //             jobWeight(i).getLB() + edgeWeight(i)(k).getLB() + minimumDistanceMatrix(k)(j)
    //           )
    //           maximumDistanceMatrix(i)(j) = Math.max(
    //             maximumDistanceMatrix(i)(j),
    //             jobWeight(i).getUB() + edgeWeight(i)(k).getUB() + maximumDistanceMatrix(k)(j)
    //           )
    //           explored(i)(j) = true
    //           explored(i)(k) = true
    //         }
    //       }
    //     }
    //   }
    // }
    // // and now perform the fix-point calculation common of Floyd Warshall
    // // println(
    // //   (0 until nJobs)
    // //     .map(i => (0 until nJobs).map(j => canSucceed(i)(j)).mkString(","))
    // //     .mkString("\n")
    // // )
    // // println("########")
    // // println(minimumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
    // // println("========")
    // // println(maximumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
    // // println("+++++")
    // // finally cehck for the biggest throughputs
    // wfor(0, _ < nJobs, _ + 1) { i =>
    //   var lb = jobThroughput(i).getLB()
    //   // var ub = jobThroughput(i).getLB()
    //   wfor(0, _ < nJobs, _ + 1) { j =>
    //     if (i != j && mustCycle(i)(j)) {
    //       lb = Math.max(lb, minimumDistanceMatrix(i)(j) + edgeWeight(j)(i).getLB())
    //     }
    //   // if (i != j && canCycle(i)(j)) {
    //   //   ub = Math.max(ub, maximumDistanceMatrix(i)(j))
    //   // }
    //   }
    //   jobThroughput(i).updateLowerBound(lb, this)
    // // jobThroughput(i).updateUpperBound(ub, this)
    // }
  }

  def isEntailed(): ESat = if (
    (0 until nJobs).map(jobThroughput(_)).forall(_.isInstantiated())
  ) then ESat.TRUE
  else ESat.UNDEFINED

}
