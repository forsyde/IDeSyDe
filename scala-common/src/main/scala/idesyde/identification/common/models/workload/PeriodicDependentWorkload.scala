package idesyde.identification.common.models.workload

import spire.math.Rational
import scalax.collection.Graph
import scalax.collection.GraphPredef._
import scalax.collection.edge.Implicits._
import idesyde.identification.common.StandardDecisionModel

/** Interface that describes a periodic workload model, also commonly known in the real time
  * academic community as "periodic task model". This one in particular closely follows the
  * definitions in:
  *
  * Scheduling Dependent Periodic Tasks Without Synchronization Mechanisms, Julien Forget Frédéric
  * Boniol, E. G. D. L. C. 2010 16th IEEE Real-Time and Embedded Technology and Applications
  * Symposium, 2010, 301-310
  *
  * but also adds a communication dimension so that further analysis and synthesis steps can treat
  * the execution and communication properly.
  */
final case class PeriodicDependentWorkload(
    val processes: Array[String],
    val offsets: Array[Rational],
    val periods: Array[Rational],
    val relativeDeadlines: Array[Rational],
    val processSizes: Array[Long],
    val processComputationalNeeds: Array[Map[String, Map[String, Long]]],
    val channels: Array[String],
    val channelSizes: Array[Long],
    val communicationGraphSrcs: Array[String],
    val communicationGraphDsts: Array[String],
    val communicationGraphDataMoved: Array[Long],
    val affineControlGraphSrcs: Array[String],
    val affineControlGraphDsts: Array[String],
    val affineControlGraphSrcSkips: Array[Array[Int]],
    val affineControlGraphSrcRepeats: Array[Array[Int]],
    val affineControlGraphDstSkips: Array[Array[Int]],
    val affineControlGraphDstRepeats: Array[Array[Int]]
) extends StandardDecisionModel
    with InstrumentedWorkloadMixin {

  val coveredElements = (processes ++ channels).toSet

  val coveredElementRelations = (communicationGraphSrcs.zip(
    communicationGraphDsts
  ) ++ affineControlGraphSrcs.zip(affineControlGraphDsts)).toSet

  /** The edges of the instance control flow graph detail if a instance T_i,k shoud be preceeded of
    * an instance T_j,l.
    *
    * In other words, it is a precedence graph at the instance (sometimes called jobs) level.
    */
  val affineRelationsGraph = Graph(
    (0 until affineControlGraphSrcs.size).toArray.map(i => {
      (affineControlGraphSrcs(i) ~+#> affineControlGraphDsts(i))(
        (
          affineControlGraphSrcRepeats(i),
          affineControlGraphSrcSkips(i),
          affineControlGraphDstRepeats(i),
          affineControlGraphDstSkips(i)
        )
      )
    }): _*
  )

  /** The edges of the communication graph should have numbers describing how much data is
    * transferred from tasks to message queues.
    */
  val communicationGraph = Graph(
    communicationGraphSrcs.zipWithIndex.map((src, i) =>
      (src ~+> communicationGraphDsts(i))(
        communicationGraphDataMoved(i)
      )
    ): _*
  )

  val hyperPeriod: Rational = periods.reduce((t1, t2) => t1.lcm(t2))

  val tasksNumInstances: Array[Int] =
    (0 until processes.length)
      .map(i => hyperPeriod / periods(i))
      .map(_.toInt)
      .toArray

  val offsetsWithDependencies = {
    var offsetsMut = offsets.clone
    for (
      sorted <- affineRelationsGraph.topologicalSort();
      node   <- sorted;
      nodeIdx = processes.indexOf(node.value)
    ) {
      offsetsMut(nodeIdx) = node.diPredecessors
        .flatMap(pred => {
          val predIdx = processes.indexOf(pred.value)
          pred
            .connectionsWith(node)
            .map(e => {
              val (ni: Int, oi: Int, nj: Int, oj: Int) = e.label
              val offsetDelta = offsetsMut(nodeIdx) - offsetsMut(predIdx) +
                (periods(nodeIdx) * oj - periods(predIdx) * oi)
              val periodDelta = periods(nodeIdx) * nj - periods(predIdx) * ni
              if (periodDelta > Rational.zero) offsetsMut(nodeIdx) - offsetDelta
              else {
                val maxIncrementCoef =
                  Math.max(tasksNumInstances(nodeIdx) / nj, tasksNumInstances(predIdx) / ni)
                offsetsMut(nodeIdx) - offsetDelta - periodDelta * maxIncrementCoef
              }
            })
        })
        .maxOption
        .getOrElse(offsetsMut(nodeIdx))
    }
    offsetsMut
  }

  val relativeDeadlinesWithDependencies =
    relativeDeadlines.zipWithIndex.map((d, i) => d + offsets(i) - offsetsWithDependencies(i))

  val (interTaskOccasionalBlock, interTaskAlwaysBlocks) = {
    val numTasks          = processes.size
    var canBlockMatrix    = Array.fill(numTasks)(Array.fill(numTasks)(false))
    var alwaysBlockMatrix = Array.fill(numTasks)(Array.fill(numTasks)(false))
    for (
      sorted <- affineRelationsGraph.topologicalSort();
      node   <- sorted;
      pred   <- node.diPredecessors;
      edge   <- pred.connectionsWith(node);
      nodeIdx = processes.indexOf(node.value);
      predIdx = processes.indexOf(pred.value)
    ) {
      // first look one behind to see immediate predecessors
      canBlockMatrix(predIdx)(nodeIdx) = true
      if (edge.label == (1, 0, 1, 0)) alwaysBlockMatrix(nodeIdx)(predIdx) = true
      // now look to see all tasks that might send an
      // stimulus to this current next tasl
      for (i <- 0 until numTasks) {
        if (canBlockMatrix(i)(predIdx)) canBlockMatrix(i)(nodeIdx) = true
        if (alwaysBlockMatrix(i)(predIdx)) alwaysBlockMatrix(i)(nodeIdx) = true
      }
    }
    (canBlockMatrix, alwaysBlockMatrix)
  }

  val largestOffset = offsetsWithDependencies.max

  val eventHorizon =
    if (largestOffset != Rational.zero) then largestOffset + (hyperPeriod * 2)
    else hyperPeriod

  val prioritiesForDependencies = {
    val numTasks      = processes.size
    var prioritiesMut = Array.fill(numTasks)(numTasks)
    for (
      sorted <- affineRelationsGraph.topologicalSort();
      node   <- sorted;
      pred   <- node.diPredecessors;
      nodeIdx = processes.indexOf(node.value);
      predIdx = processes.indexOf(pred.value)
      if prioritiesMut(nodeIdx) <= prioritiesMut(predIdx)
    ) {
      prioritiesMut(nodeIdx) = prioritiesMut(predIdx) - 1
    }
    // scribe.debug(prioritiesMut.mkString("[", ",", "]"))
    prioritiesMut
  }

  val messagesMaxSizes = channelSizes

  def uniqueIdentifier: String = "PeriodicDependentWorkload"

}
