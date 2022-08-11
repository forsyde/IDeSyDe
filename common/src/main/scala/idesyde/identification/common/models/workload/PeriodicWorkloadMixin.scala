package idesyde.identification.models.workload

import idesyde.utils.MultipliableFractional
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph

import math.Fractional.Implicits.infixFractionalOps
import math.Ordering.Implicits.infixOrderingOps
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.traverse.TopologicalOrderIterator
import java.util.Comparator
import org.jgrapht.graph.builder.GraphBuilder
import scala.reflect.ClassTag

/** Interface that describes a periodic workload model, also commonly known in the real time
  * academic community as "periodic task model". This one in particular closely follows the
  * definitions in:
  *
  * Scheduling Dependent Periodic Tasks Without Synchronization Mechanisms, Julien Forget Frédéric
  * Boniol, E. G. D. L. C. 2010 16th IEEE Real-Time and Embedded Technology and Applications
  * Symposium, 2010, 301-310
  *
  * @tparam TaskT
  *   The type representing each task.
  * @tparam TimeT
  *   The type that represents a time tag.
  */
trait PeriodicWorkloadMixin[TimeT](using fracT: Fractional[TimeT])(using Conversion[Int, TimeT])(using ClassTag[TimeT]):

  def numTasks: Int
  // def periodicTasks: Array[TaskT]
  def offsets: Array[TimeT]
  def periods: Array[TimeT]
  def relativeDeadlines: Array[TimeT]
  def taskSizes: Array[Long]
  /** this represent the implementation-to-computation needs each task has */
  def taskComputationNeeds: Array[Map[String, Map[String, Long]]]
  // def instancePreceeds(src: TaskT)(dst: TaskT)(srcI: Int)(dstI: Int): Boolean
  def numMessageQeues: Int
  // def messageQueues: Array[MQueueT]
  def messageQueuesSizes: Array[Long]

  val periodicTasks = 0 until numTasks
  val messageQueues = numTasks until (numTasks + numMessageQeues)

  /** The edges of the instance control flow graph detail if a instance T_i,k shoud be preceeded of
    * an instance T_j,l.def
    *
    * In other words, it is a precedence graph at the instance (sometimes called jobs) level.
    */
  def affineRelationsGraph: Graph[Int, (Int, Int, Int, Int)]

  /** The edges of the communication graph should have numbers describing how much data is
    * transferred from tasks to message queues.
    */
  def communicationGraph: Graph[Int, Long]

  /** a function that returns the LCM upper bound of two time values
    */
  def computeLCM(t1: TimeT, t2: TimeT): TimeT

  lazy val hyperPeriod: TimeT = periods.reduce((t1, t2) => computeLCM(t1, t2))
  lazy val tasksNumInstances: Array[Int] =
    (0 until periodicTasks.length)
      .map(i => fracT.div(hyperPeriod, periods(i)))
      .map(fracT.toInt(_))
      .toArray

  lazy val offsetsWithDependencies = {
    var offsetsMut = offsets.clone
    val iter       = TopologicalOrderIterator(affineRelationsGraph)
    while (iter.hasNext) {
      val idxTask = iter.next
      offsetsMut(idxTask) = affineRelationsGraph
        .incomingEdgesOf(idxTask)
        .stream
        .map(e => {
          val inTaskIdx        = affineRelationsGraph.getEdgeSource(e)
          val (ni, nj, oi, oj) = e
          val offsetDelta = offsetsMut(idxTask) - offsetsMut(inTaskIdx) +
            (periods(idxTask) * oj - periods(inTaskIdx) * oi)

          val periodDelta = periods(idxTask) * nj - periods(inTaskIdx) * ni
          if (periodDelta > fracT.zero) offsetsMut(idxTask) - offsetDelta
          else
            val maxIncrementCoef =
              Math.max(tasksNumInstances(idxTask) / nj, tasksNumInstances(inTaskIdx) / ni)
            offsetsMut(idxTask) - offsetDelta - periodDelta * maxIncrementCoef
        })
        .max(Comparator.comparingDouble(f => fracT.toDouble(f)))
        .orElse(offsetsMut(idxTask))
    }
    offsetsMut
  }

  lazy val relativeDeadlinesWithDependencies =
    relativeDeadlines.zipWithIndex.map((d, i) => d + offsets(i) - offsetsWithDependencies(i))

  lazy val (interTaskOccasionalBlock, interTaskAlwaysBlocks) = {
    var canBlockMatrix    = Array.fill(numTasks)(Array.fill(numTasks)(false))
    var alwaysBlockMatrix = Array.fill(numTasks)(Array.fill(numTasks)(false))
    val topoIter          = TopologicalOrderIterator(affineRelationsGraph)
    while (topoIter.hasNext) {
      val next = topoIter.next
      affineRelationsGraph
        .incomingEdgesOf(next)
        .forEach(e => {
          // first look one behind to see immediate predecessors
          val src = affineRelationsGraph.getEdgeSource(e)
          canBlockMatrix(src)(next) = true
          if (e == (1, 0, 1, 0)) alwaysBlockMatrix(src)(next) = true
          // now look to see all tasks that might send an
          // stimulus to this current next tasl
          for (i <- 0 until numTasks) {
            if (canBlockMatrix(i)(src)) canBlockMatrix(src)(next) = true
            if (alwaysBlockMatrix(i)(src)) alwaysBlockMatrix(src)(next) = true
          }
        })
    }
    // scribe.debug(canBlockMatrix.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
    // scribe.debug(alwaysBlockMatrix.map(_.mkString("[", ",", "]")).mkString("[", ",", "]"))
    (canBlockMatrix, alwaysBlockMatrix)
  }

  lazy val largestOffset: TimeT = offsetsWithDependencies.max

  lazy val eventHorizon: TimeT =
    if (largestOffset != fracT.zero) then largestOffset + (hyperPeriod * 2) 
    else hyperPeriod

  lazy val prioritiesForDependencies = {
    var prioritiesMut = Array.fill(numTasks)(numTasks)
    val iter = TopologicalOrderIterator(
      affineRelationsGraph
    )
    while (iter.hasNext) {
      val next = iter.next
      prioritiesMut(next) = affineRelationsGraph
        .incomingEdgesOf(next)
        .stream
        .map(affineRelationsGraph.getEdgeSource(_))
        .mapToInt(src => {
          prioritiesMut(src) - 1
        })
        .min
        .orElse(prioritiesMut(next))
    }
    // scribe.debug(prioritiesMut.mkString("[", ",", "]"))
    prioritiesMut
  }

  lazy val taskReadsMessageQueue = (0 until numTasks).map(t =>
    (numTasks until numMessageQeues + numTasks).map(m =>
      communicationGraph.containsEdge(m, t)
    ).toArray).toArray

  lazy val taskWritesMessageQueue = (0 until numTasks).map(t =>
    (numTasks until numMessageQeues + numTasks).map(m =>
      communicationGraph.containsEdge(t, m)
    ).toArray).toArray

end PeriodicWorkloadMixin
