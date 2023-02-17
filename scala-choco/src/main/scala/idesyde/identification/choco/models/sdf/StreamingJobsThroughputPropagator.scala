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
      PropagatorPriority.TERNARY,
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

  val minimumDistanceMatrix = Buffer.fill(nJobs + 1)(Buffer.fill(nJobs)(0))
  val maximumDistanceMatrix = Buffer.fill(nJobs + 1)(Buffer.fill(nJobs)(0))
  val minNext               = Buffer.fill(nJobs + 1)(Buffer.fill(nJobs)(0))
  val maxNext               = Buffer.fill(nJobs + 1)(Buffer.fill(nJobs)(0))
  // val explored              = Array.fill(nJobs)(Array.fill(nJobs)(true))

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

  final def countPathLength(prevs: Buffer[Buffer[Int]])(i: Int)(j: Int): Int = {
    var count = 0
    var k     = j
    while (k != i) {
      count += 1
      k = prevs(i)(k)
    }
    count
  }

  def propagate(evtmask: Int): Unit = {
    // println(getModel().getSolver().getDecisionPath().toString())
    wfor(0, _ < nJobs, _ + 1) { src =>
      // println(s"Source is $src")
      wfor(0, _ <= nJobs, _ + 1) { k =>
        wfor(0, _ < nJobs, _ + 1) { v =>
          maximumDistanceMatrix(k)(v) = Int.MinValue
          minimumDistanceMatrix(k)(v) = Int.MinValue
          minNext(k)(v) = -1
          maxNext(k)(v) = -1
        }
      }
      maximumDistanceMatrix(0)(src) = 0
      minimumDistanceMatrix(0)(src) = 0

      // body
      wfor(1, _ <= nJobs, _ + 1) { k =>
        wfor(0, _ < nJobs, _ + 1) { v =>
          wfor(0, _ < nJobs, _ + 1) { pred =>
            if (canSucceed(pred)(v) || canLastToFirst(pred)(v)) {
              if (
                minimumDistanceMatrix(k)(v) < minimumDistanceMatrix(k - 1)(pred) + jobWeight(pred)
                  .getLB() + edgeWeight(pred)(v).getLB()
              ) {
                minimumDistanceMatrix(k)(v) =
                  minimumDistanceMatrix(k - 1)(pred) + jobWeight(pred).getLB() + edgeWeight(pred)(v)
                    .getLB()
                minNext(k)(v) = pred
              }
              if (
                maximumDistanceMatrix(k)(v) < maximumDistanceMatrix(k - 1)(pred) + jobWeight(pred)
                  .getUB() + edgeWeight(pred)(v).getUB()
              ) {
                maximumDistanceMatrix(k)(v) =
                  maximumDistanceMatrix(k - 1)(pred) + jobWeight(pred).getUB() + edgeWeight(pred)(v)
                    .getUB()
                maxNext(k)(v) = pred
              }
            }
          }
        }
      }
      // println("-----")
      // println(
      //   (0 until nJobs)
      //     .map(i => (0 until nJobs).map(j => canLastToFirst(i)(j)).mkString(", "))
      //     .mkString("\n")
      // )
      // println("-----")
      // println(
      //   (0 until nJobs)
      //     .map(i => (0 until nJobs).map(j => canSucceed(i)(j)).mkString(", "))
      //     .mkString("\n")
      // )
      // println("------")
      // println(minNext.map(_.mkString(",")).mkString("\n"))
      // println("------")
      // println(maxNext.map(_.mkString(",")).mkString("\n"))
      // println(src)
      // println(minimumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
      // println("-----")
      // println(maximumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
      // println("=====")

      // tail
      var lb = Int.MaxValue
      var ub = Int.MaxValue
      wfor(0, _ < nJobs, _ + 1) { v =>
        if (hasDataCycle(src)(v)) {
          val maxLength = countPathLength(minNext)(v)(src)
          val proposal  = minimumDistanceMatrix(maxLength)(src)
          if (0 < proposal && proposal < lb) {
            // println(s"found $lb")
            lb = proposal
          }
        }
      }
      wfor(1, _ <= nJobs, _ + 1) { k =>
        val proposal = minimumDistanceMatrix(k)(src)
        if (0 < proposal && proposal < lb) {
          // println(s"found $lb")
          lb = proposal
        }
      }

      // println(s"$lb for $src")
      if (lb < Int.MaxValue && jobThroughput(src).getLB() < lb)
        jobThroughput(src).updateLowerBound(lb, this)
    // if (ub < Int.MaxValue) jobThroughput(i).updateUpperBound(ub, this)
    }

    // var bigM   = 0L
    // var bigSum = 0L
    // wfor(0, _ < nJobs, _ + 1) { i =>
    //   bigSum += jobWeight(i).getUB()
    //   wfor(0, _ < nJobs, _ + 1) { j =>
    //     if (bigM < jobWeight(i).getUB() + edgeWeight(i)(j).getUB()) {
    //       bigM = jobWeight(i).getUB() + edgeWeight(i)(j).getUB()
    //     }
    //     bigSum += edgeWeight(i)(j).getUB()
    //   }
    // }
    // // reset the graph
    // wfor(0, _ < nJobs, _ + 1) { i =>
    //   maximumDistanceMatrix(i)(i) = 0
    //   minimumDistanceMatrix(i)(i) = 0
    //   minNext(i)(i) = i
    //   // maxNext(i)(i) = i
    //   wfor(0, _ < nJobs, _ + 1) { j =>
    //     if (i != j) {
    //       if (canSucceed(i)(j)) {
    //         // possibleFiringsGraph.addEdge(i, j)
    //         // possibleFiringsGraph.setEdgeWeight(i, j, maxTh - jobWeight(i).getLB() - edgeWeight(i)(j).getLB())
    //         maximumDistanceMatrix(i)(j) = - jobWeight(i).getUB() - edgeWeight(i)(j).getUB()
    //         minimumDistanceMatrix(i)(j) = - jobWeight(i).getLB() - edgeWeight(i)(j).getLB()
    //         minNext(i)(j) = j
    //         maxNext(i)(j) = j
    //       } else {
    //         // possibleFiringsGraph.removeEdge(i, j)
    //         maximumDistanceMatrix(i)(j) = Long.MaxValue / 2L
    //         minimumDistanceMatrix(i)(j) = Long.MaxValue / 2L
    //         minNext(i)(j) = -1
    //         maxNext(i)(j) = -1
    //       }
    //     }
    //   }
    // }
    // // calculate longest cycles
    // wfor(0, _ < nJobs, _ + 1) { k =>
    //   wfor(0, _ < nJobs, _ + 1) { i =>
    //     wfor(0, _ < nJobs, _ + 1) { j =>
    //       if (
    //         minimumDistanceMatrix(i)(j) > minimumDistanceMatrix(i)(k) + minimumDistanceMatrix(k)(j)
    //       ) {
    //         minimumDistanceMatrix(i)(j) = minimumDistanceMatrix(i)(k) + minimumDistanceMatrix(k)(j)
    //         minNext(i)(j) = minNext(i)(k)
    //       }
    //       if (
    //         maximumDistanceMatrix(i)(j) > maximumDistanceMatrix(i)(k) + maximumDistanceMatrix(k)(j)
    //       ) {
    //         maximumDistanceMatrix(i)(j) = maximumDistanceMatrix(i)(k) + maximumDistanceMatrix(k)(j)
    //         maxNext(i)(j) = maxNext(i)(k)
    //       }
    //     }
    //   }
    // }
    // // println(minNext.map(_.mkString(",")).mkString("\n"))
    // // println("------")
    // // println(maxNext.map(_.mkString(",")).mkString("\n"))
    // val maxCounts = countPathLength(maxNext)
    // val minCounts = countPathLength(minNext)
    // wfor(0, _ < nJobs, _ + 1) { i =>
    //   wfor(0, _ < nJobs, _ + 1) { j =>
    //     if (minNext(i)(j) > -1) {
    //       minimumDistanceMatrix(i)(j) = - minimumDistanceMatrix(i)(j)
    //     }
    //     if (maxNext(i)(j) > -1) {
    //       maximumDistanceMatrix(i)(j) = - maximumDistanceMatrix(i)(j)
    //     }
    //   }
    // }
    // // no enuemrate the cycles
    // // var minCycle                   = 0
    // // var maxCycle                   = 0
    // // var visited = Buffer.fill(nJobs)(false)
    // // wfor(0, _ < nJobs, _ + 1) { i =>
    // //   if (!visited(i)) {
    // //     visited(i) = true

    // //   }

    // // }
    // // var minCycleList: ju.List[Int] = ju.List.of()
    // // var maxCycleList: ju.List[Int] = ju.List.of()
    // // val couldCycleAlg              = JohnsonSimpleCycles(possibleFiringsGraph)
    // // // val mustCycleAlg               = JohnsonSimpleCycles(mustFiringsGraph)
    // // couldCycleAlg
    // //   .findSimpleCycles()
    // //   .forEach(cycle => {
    // //     var ubCycleLength = 0
    // //     var lbCycleLength = 0
    // //     wfor(0, _ < cycle.size() - 1, _ + 1) { i =>
    // //       ubCycleLength = ubCycleLength + jobWeight(cycle.get(i))
    // //         .getUB() + edgeWeight(cycle.get(i))(cycle.get(i + 1)).getUB()
    // //       lbCycleLength = lbCycleLength + jobWeight(cycle.get(i))
    // //         .getLB() + edgeWeight(cycle.get(i))(cycle.get(i + 1)).getLB()
    // //     }
    // //     ubCycleLength += jobWeight(cycle.get(cycle.size() - 1))
    // //       .getUB() + edgeWeight(cycle.get(cycle.size() - 1))(cycle.get(0)).getUB()
    // //     lbCycleLength += jobWeight(cycle.get(cycle.size() - 1))
    // //       .getLB() + edgeWeight(cycle.get(cycle.size() - 1))(cycle.get(0)).getLB()
    // //     if (ubCycleLength > maxCycle || maxCycle == 0) {
    // //       maxCycle = ubCycleLength
    // //       maxCycleList = cycle
    // //     }
    // //     if (lbCycleLength < minCycle || minCycle == 0) {
    // //       minCycle = lbCycleLength
    // //       minCycleList = cycle
    // //     }
    // //   })
    // println(minimumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
    // println("-----")
    // println(maximumDistanceMatrix.map(_.mkString(",")).mkString("\n"))
    // println("-----")
    // println(
    //   (0 until nJobs)
    //     .map(i => (0 until nJobs).map(j => mustLastToFirst(i)(j)).mkString(", "))
    //     .mkString("\n")
    // )
    // println("-----")
    // println(
    //   (0 until nJobs)
    //     .map(i => (0 until nJobs).map(j => canLastToFirst(i)(j)).mkString(", "))
    //     .mkString("\n")
    // )
    // println("=====")
    // // perform the bounding now,
    // wfor(0, _ < nJobs, _ + 1) { i =>
    //   var lb = 0
    //   var ub = 0
    //   wfor(0, _ < nJobs, _ + 1) { j =>
    //     if (mustLastToFirst(i)(j)) {
    //       if (lb == 0) {
    //         lb = minimumDistanceMatrix(j)(i).toInt + jobWeight(i).getLB() + edgeWeight(i)(j).getLB()
    //       } else {
    //         lb = Math.max(
    //           lb,
    //           minimumDistanceMatrix(j)(i).toInt + jobWeight(i).getLB() + edgeWeight(i)(j).getLB()
    //         )
    //       }
    //     }
    //     if (canLastToFirst(i)(j)) {
    //       if (ub == 0) {
    //         ub = maximumDistanceMatrix(j)(i).toInt + jobWeight(i).getUB() + edgeWeight(i)(j).getUB()
    //       } else {
    //         ub = Math.max(ub, maximumDistanceMatrix(j)(i).toInt + jobWeight(i).getUB() + edgeWeight(i)(j).getUB())
    //       }
    //     }
    //   }
    //   // println(s"$lb and $ub for $i")
    //   if (lb > 0) jobThroughput(i).updateLowerBound(lb, this)
    //   if (ub > 0) jobThroughput(i).updateUpperBound(ub, this)
    // }
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
