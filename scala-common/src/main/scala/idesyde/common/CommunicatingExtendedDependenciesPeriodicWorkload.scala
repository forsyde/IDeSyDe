package idesyde.common

import spire.math.Rational
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.edge.Implicits._
import scala.collection.mutable.Buffer

/** A decision model for communicating periodically activated processes.
  *
  * Interface that describes a periodic workload model, also commonly known in the real time
  * academic community as "periodic task model". This one in particular closely follows the
  * definitions in [1], but also adds a communication dimension so that further analysis and
  * synthesis steps can treat the execution and communication properly.
  *
  * [1](https://ieeexplore.ieee.org/document/5465989) Scheduling Dependent Periodic Tasks Without
  * Synchronization Mechanisms, Julien Forget Frédéric Boniol, E. G. D. L. C. 2010 16th IEEE
  * Real-Time and Embedded Technology and Applications Symposium, 2010, 301-310
  *
  * @param additionalCoveredElements
  *   this extra field exist to support wild design models being reduced to this decision model
  * @param additionalCoveredElementRelations
  *   this extra field exist to support wild design models being reduced to this decision model
  */
trait CommunicatingExtendedDependenciesPeriodicWorkload {

  def periods: Vector[Double]
  def offsets: Vector[Double]
  def relativeDeadlines: Vector[Double]
  def affineControlGraph: Set[(Int, Int, Int, Int, Int, Int)]
  // def affineControlGraphSrcs: Vector[String]
  // def affineControlGraphDsts: Vector[String]
  // def affineControlGraphSrcRepeats: Vector[Int]
  // def affineControlGraphSrcSkips: Vector[Int]
  // def affineControlGraphDstRepeats: Vector[Int]
  // def affineControlGraphDstSkips: Vector[Int]
  // val coveredElements = (processes ++ channels).toSet

  // val coveredElementRelations = affineControlGraphSrcs
  //   .zip(affineControlGraphDsts)
  //   .toSet

  def numVirtualTasks: Int = periods.size

  /** The edges of the instance control flow graph detail if a instance T_i,k shoud be preceeded of
    * an instance T_j,l.
    *
    * In other words, it is a precedence graph at the instance (sometimes called jobs) level.
    */
  def affineRelationsGraph = {
    val g = DefaultDirectedGraph[Int, (Int, Int, Int, Int)]();
    for ((src, dst, srcRepeat, srcSkip, dstRepeat, dstSkip) <- affineControlGraph) {
      g.addEdge(src, dst, (srcRepeat, srcSkip, dstRepeat, dstSkip))
    }
    g
  //   Graph.from(
  //   0 until numVirtualTasks,
  //   affineControlGraph
  //     .map((src, dst, srcRepeat, srcSkip, dstRepeat, dstSkip) =>
  //       (src ~+#> dst)(
  //         (
  //           srcRepeat,
  //           srcSkip,
  //           dstRepeat,
  //           dstSkip
  //         )
  //       )
  //     )
  // )
  }

  /** The edges of the communication graph should have numbers describing how much data is
    * transferred from tasks to message queues. The numbering is done so that,
    *
    * task_0, task_1, ..., task_n, channel_1, ..., channel_m
    */
  // def communicationGraph = Graph.from(
  //   processes ++ channels,
  //   dataTransferGraph.map((src, dst, d) => src ~> dst % d)
  //   // processes.zipWithIndex.flatMap((p, i) =>
  //   //   channels.zipWithIndex
  //   //     .filter((c, j) => processWritesToChannel(i)(j) > 0L)
  //   //     .map((c, j) => p ~> c % processWritesToChannel(i)(j))
  //   // ) ++
  //   //   processes.zipWithIndex.flatMap((p, i) =>
  //   //     channels.zipWithIndex
  //   //       .filter((c, j) => processReadsFromChannel(i)(j) > 0L)
  //   //       .map((c, j) => c ~> p % processReadsFromChannel(i)(j))
  //   //   )
  // )

  def hyperPeriod: Double = {
    val factors = periods.filter(t =>
      !periods.exists(tt => {
        val quod = (t / tt)
        val err  = Math.abs(quod - quod.round.toDouble)
        err <= 1e-6
      })
    )
    factors.reduce(_ * _)
  }
  // periods.reduce((t1, t2) => t1.lcm(t2))

  def tasksNumInstances: Vector[Int] =
    periods
      .map(p => hyperPeriod / p)
      .map(_.toInt)

  def offsetsWithDependencies = {
    val g          = affineRelationsGraph
    var offsetsMut = offsets.toBuffer
    for (
      sorted <- g.topologicalSort();
      innerI <- sorted;
      i = innerI.value
    ) {
      // offsetsMut(i) = innerI.diPredecessors.flatMap(predecessor => predecessor.)
      offsetsMut(i) = innerI.diPredecessors
        .flatMap(pred => {
          val predIdx = pred.value
          pred
            .connectionsWith(innerI)
            .map(e => {
              val (ni: Int, oi: Int, nj: Int, oj: Int) = e.label: @unchecked
              val offsetDelta = offsetsMut(i) - offsetsMut(predIdx) +
                (periods(i) * oj - periods(predIdx) * oi)
              val periodDelta = periods(i) * nj - periods(predIdx) * ni
              if (periodDelta > Rational.zero) offsetsMut(i) - offsetDelta
              else {
                val maxIncrementCoef =
                  Math.max(tasksNumInstances(i) / nj, tasksNumInstances(predIdx) / ni)
                offsetsMut(i) - offsetDelta - periodDelta * maxIncrementCoef
              }
            })
        })
        .maxOption
        .getOrElse(offsetsMut(i))
    }
    offsetsMut.toVector
  }

  def relativeDeadlinesWithDependencies =
    relativeDeadlines.zipWithIndex.map((d, i) => d + offsets(i) - offsetsWithDependencies(i))

  def interTaskOccasionalBlock = {
    val g              = affineRelationsGraph
    val numTasks       = numVirtualTasks
    var canBlockMatrix = Array.fill(numTasks)(Array.fill(numTasks)(false))
    for (
      sorted <- g.topologicalSort();
      node   <- sorted;
      pred   <- node.diPredecessors;
      edge   <- pred.connectionsWith(node);
      nodeIdx = node.value;
      predIdx = pred.value
    ) {
      // first look one behind to see immediate predecessors
      canBlockMatrix(predIdx)(nodeIdx) = true
      // now look to see all tasks that might send an
      // stimulus to this current next tasl
      for (i <- 0 until numTasks) {
        if (canBlockMatrix(i)(predIdx)) canBlockMatrix(i)(nodeIdx) = true
      }
    }
    canBlockMatrix
  }

  def interTaskAlwaysBlocks = {
    val g                 = affineRelationsGraph
    val numTasks          = numVirtualTasks
    var alwaysBlockMatrix = Array.fill(numTasks)(Array.fill(numTasks)(false))
    for (
      sorted <- g.topologicalSort();
      node   <- sorted;
      pred   <- node.diPredecessors;
      edge   <- pred.connectionsWith(node);
      nodeIdx = node.value;
      predIdx = pred.value
    ) {
      // first look one behind to see immediate predecessors
      if (edge.label == (1, 0, 1, 0)) alwaysBlockMatrix(nodeIdx)(predIdx) = true
      // now look to see all tasks that might send an
      // stimulus to this current next tasl
      for (i <- 0 until numTasks) {
        if (alwaysBlockMatrix(i)(predIdx)) alwaysBlockMatrix(i)(nodeIdx) = true
      }
    }
    alwaysBlockMatrix
  }

  def largestOffset = offsetsWithDependencies.max

  def eventHorizon =
    if (largestOffset != Rational.zero) then largestOffset + (hyperPeriod * 2)
    else hyperPeriod

  def prioritiesForDependencies = {
    val g             = affineRelationsGraph
    val numTasks      = numVirtualTasks
    var prioritiesMut = Buffer.fill(numTasks)(numTasks)
    for (
      sorted <- g.topologicalSort();
      node   <- sorted;
      pred   <- node.diPredecessors;
      nodeIdx = node.value;
      predIdx = pred.value;
      if prioritiesMut(nodeIdx) <= prioritiesMut(predIdx)
    ) {
      prioritiesMut(nodeIdx) = prioritiesMut(predIdx) - 1
    }
    // scribe.debug(prioritiesMut.mkString("[", ",", "]"))
    prioritiesMut
  }

  def prioritiesRateMonotonic = {
    var prioritiesMut = prioritiesForDependencies
    for (
      i <- 0 until prioritiesMut.size;
      j <- 0 until prioritiesMut.size;
      if i != j;
      if prioritiesMut(i) > prioritiesMut(j) || (prioritiesMut(i) == prioritiesMut(j) && periods(
        i
      ) < periods(j))
    ) {
      prioritiesMut(j) -= 1
    }
    prioritiesMut
  }

}
