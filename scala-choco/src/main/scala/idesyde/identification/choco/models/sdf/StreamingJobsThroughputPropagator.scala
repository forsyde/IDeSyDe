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
import org.jgrapht.graph.SimpleDirectedWeightedGraph

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

  // val possibleFiringsGraph = {
  //   val g = new SimpleDirectedWeightedGraph[Int, DefaultEdge](classOf[DefaultEdge])
  //   for (i <- 0 until nJobs) g.addVertex(i)
  //   g
  // }
  // val mustFiringsGraph = {
  //   val g = new SimpleDirectedGraph[Int, DefaultEdge](classOf[DefaultEdge])
  //   for (i <- 0 until nJobs) g.addVertex(i)
  //   g
  // }

  val minimumDistanceMatrix = Buffer.fill(nJobs)(Buffer.fill(nJobs)(0L))
  val maximumDistanceMatrix = Buffer.fill(nJobs)(Buffer.fill(nJobs)(0L))
  val minNext = Buffer.fill(nJobs)(Buffer.fill(nJobs)(0))
  val maxNext = Buffer.fill(nJobs)(Buffer.fill(nJobs)(0))
  // val explored              = Array.fill(nJobs)(Array.fill(nJobs)(true))

  /** This function checks if jib i can be succeed by j, based on the original precedences and the
    * mapping + ordering ones.
    */
  def canSucceed(i: Int)(j: Int): Boolean = if (jobMapping(i)
      .stream()
      .anyMatch(jobMapping(j).contains(_))) {
        jobOrdering(i).stream().anyMatch(oi => jobOrdering(j).contains(oi + 1))
      } else {
        isSuccessor(i)(j)
      }

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

  final def countPathLength(nexts: Buffer[Buffer[Int]])(i: Int)(j: Int): Long = {
    var count = 0
    var k = i
    while (k != j) {
      count += 1
      k = nexts(k)(j)
    }
    count
  }

  def propagate(evtmask: Int): Unit = {
    // println(getModel().getSolver().getDecisionPath().toString())
    var bigM = 0L
    var bigSum = 0L
    wfor(0, _ < nJobs, _ + 1) { i =>
      bigSum += jobWeight(i).getUB()
      wfor(0, _ < nJobs, _ + 1) { j =>
        if (bigM < jobWeight(i).getUB() + edgeWeight(i)(j).getUB()) {
          bigM = jobWeight(i).getUB() + edgeWeight(i)(j).getUB()
        }
        bigSum += edgeWeight(i)(j).getUB()
      }
    }
    // reset the graph
    wfor(0, _ < nJobs, _ + 1) { i =>
      maximumDistanceMatrix(i)(i) = 0
      minimumDistanceMatrix(i)(i) = 0
      minNext(i)(i) = i
      maxNext(i)(i) = i
      wfor(0, _ < nJobs, _ + 1) { j =>
        if (i != j) {
          if (canSucceed(i)(j)) {
            // possibleFiringsGraph.addEdge(i, j)
            // possibleFiringsGraph.setEdgeWeight(i, j, maxTh - jobWeight(i).getLB() - edgeWeight(i)(j).getLB())
            maximumDistanceMatrix(i)(j) = bigM - jobWeight(i).getLB() - edgeWeight(i)(j).getLB()
            minimumDistanceMatrix(i)(j) = bigM - jobWeight(i).getUB() - edgeWeight(i)(j).getUB()
            minNext(i)(j) = j
            maxNext(i)(j) = j
          } else {
            // possibleFiringsGraph.removeEdge(i, j)
            maximumDistanceMatrix(i)(j) = bigSum
            minimumDistanceMatrix(i)(j) = bigSum
            minNext(i)(j) = -1
            maxNext(i)(j) = -1
          }
        }
      }
    }
    // calculate longest cycles
    wfor(0, _ < nJobs, _ + 1) { k =>
      wfor(0, _ < nJobs, _ + 1) { i =>
        wfor(0, _ < nJobs, _ + 1) { j =>
          if (minimumDistanceMatrix(i)(j) > minimumDistanceMatrix(i)(k) + minimumDistanceMatrix(k)(j)) {
            minimumDistanceMatrix(i)(j) = minimumDistanceMatrix(i)(k) + minimumDistanceMatrix(k)(j)
            minNext(i)(j) = minNext(i)(k)
          }
          if (maximumDistanceMatrix(i)(j) > maximumDistanceMatrix(i)(k) + maximumDistanceMatrix(k)(j)) {
            maximumDistanceMatrix(i)(j) = maximumDistanceMatrix(i)(k) + maximumDistanceMatrix(k)(j)
            maxNext(i)(j) = maxNext(i)(k)
          }
        }
      }
    }
    val maxCounts =  countPathLength(maxNext)
    val minCounts = countPathLength(minNext)
    wfor(0, _ < nJobs, _ + 1) { i =>
        wfor(0, _ < nJobs, _ + 1) { j =>
          if (maxNext(i)(j) > -1) {
            maximumDistanceMatrix(i)(j) = maxCounts(i)(j) * bigM - maximumDistanceMatrix(i)(j)
            minimumDistanceMatrix(i)(j) = minCounts(i)(j) * bigM - minimumDistanceMatrix(i)(j)
          }
        }
      }
    // no enuemrate the cycles
    // var minCycle                   = 0
    // var maxCycle                   = 0
    // var visited = Buffer.fill(nJobs)(false)
    // wfor(0, _ < nJobs, _ + 1) { i =>
    //   if (!visited(i)) {
    //     visited(i) = true
                
    //   }

    // }
    // var minCycleList: ju.List[Int] = ju.List.of()
    // var maxCycleList: ju.List[Int] = ju.List.of()
    // val couldCycleAlg              = JohnsonSimpleCycles(possibleFiringsGraph)
    // // val mustCycleAlg               = JohnsonSimpleCycles(mustFiringsGraph)
    // couldCycleAlg
    //   .findSimpleCycles()
    //   .forEach(cycle => {
    //     var ubCycleLength = 0
    //     var lbCycleLength = 0
    //     wfor(0, _ < cycle.size() - 1, _ + 1) { i =>
    //       ubCycleLength = ubCycleLength + jobWeight(cycle.get(i))
    //         .getUB() + edgeWeight(cycle.get(i))(cycle.get(i + 1)).getUB()
    //       lbCycleLength = lbCycleLength + jobWeight(cycle.get(i))
    //         .getLB() + edgeWeight(cycle.get(i))(cycle.get(i + 1)).getLB()
    //     }
    //     ubCycleLength += jobWeight(cycle.get(cycle.size() - 1))
    //       .getUB() + edgeWeight(cycle.get(cycle.size() - 1))(cycle.get(0)).getUB()
    //     lbCycleLength += jobWeight(cycle.get(cycle.size() - 1))
    //       .getLB() + edgeWeight(cycle.get(cycle.size() - 1))(cycle.get(0)).getLB()
    //     if (ubCycleLength > maxCycle || maxCycle == 0) {
    //       maxCycle = ubCycleLength
    //       maxCycleList = cycle
    //     }
    //     if (lbCycleLength < minCycle || minCycle == 0) {
    //       minCycle = lbCycleLength
    //       minCycleList = cycle
    //     }
    //   })
    // println(mustFiringsGraph.toString())
    // println(minimumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
    // println("-----")
    // println(maximumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
    // println("-----")
    // println(minNext.map(_.mkString(",")).mkString("\n"))
    // println("-----")
    // println(maxNext.map(_.mkString(",")).mkString("\n"))
    // println("=====")
    // perform the bounding now,
    wfor(0, _ < nJobs, _ + 1) { i =>
      var lb = 0
      // var ub = 0
      wfor(0, _ < nJobs, _ + 1) { j =>
        if (isLastToFirst(i)(j)) {
          if (lb == 0) {
            lb = minimumDistanceMatrix(j)(i).toInt + jobWeight(i).getLB() + edgeWeight(j)(i).getLB()
          } else {
            lb = Math.min(lb, minimumDistanceMatrix(j)(i).toInt + jobWeight(i).getLB() + edgeWeight(j)(i).getLB()) 
          }
          // if (ub == 0) {
          //   ub = maximumDistanceMatrix(i)(j).toInt + jobWeight(j).getUB() + edgeWeight(j)(i).getUB()
          // } else {
          //   ub = Math.max(ub, maximumDistanceMatrix(i)(j).toInt + jobWeight(j).getUB() + edgeWeight(j)(i).getUB()) 
          // }
        }
      }
      // println(s"$lb  for $i")
      jobThroughput(i).updateLowerBound(lb, this)
      // jobThroughput(i).updateUpperBound(ub, this)
    }
    // minCycleList.forEach(v => jobThroughput(v).updateLowerBound(minCycle, this))
    // maxCycleList.forEach(v => jobThroughput(v).updateUpperBound(maxCycle, this))
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
