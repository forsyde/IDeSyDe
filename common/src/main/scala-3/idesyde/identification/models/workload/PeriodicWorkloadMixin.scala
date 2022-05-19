package idesyde.identification.models.workload

import forsyde.io.java.core.Vertex
import idesyde.identification.ForSyDeDecisionModel
import idesyde.utils.MultipliableFractional
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph

import idesyde.utils.MultipliableFractionalImplicits.infixMultipliableFractionalOps
import math.Ordering.Implicits.infixOrderingOps
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge
import forsyde.io.java.typed.viewers.execution.Upsample
import forsyde.io.java.typed.viewers.execution.Downsample
import org.jgrapht.traverse.TopologicalOrderIterator

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
trait PeriodicWorkloadMixin[TimeT](using fracT: MultipliableFractional[TimeT]):

  def numTasks: Int
  // def periodicTasks: Array[TaskT]
  def offsets: Array[TimeT]
  def periods: Array[TimeT]
  def relativeDeadlines: Array[TimeT]
  def taskSizes: Array[Long]
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
  def communicationGraph: Graph[Int, DefaultEdge]

  /** a function that returns the LCM upper bound of two time values
    */
  def computeLCM(t1: TimeT, t2: TimeT): TimeT

  // the following implementations is not efficient. But generic.
  // TODO: finding a way that is both eficient and generic
  // def instancesReleases(tidx: Int)(int: Int): TimeT =
  //   (1 until int).map(_ => periods(tidx)).sum + offsets(tidx) // *(fracT.one * int)
  // def instancesDeadlines(tidx: Int)(int: Int): TimeT =
  //   (1 until int).map(_ => relativeDeadlines(tidx)).sum + offsets(tidx) //*(fracT.one * int)
  lazy val hyperPeriod: TimeT = periods.reduce((t1, t2) => computeLCM(t1, t2))
  lazy val tasksNumInstances: Array[Int] =
    (0 until periodicTasks.length)
      .map(i => fracT.div(hyperPeriod, periods(i)))
      .map(fracT.toInt(_))
      .toArray

  // def maximalInterference(src: Int)(dst: Int)(using num: Numeric[TimeT]): Int =
  //   // val src = periodicTasks.indexOf(srcTask)
  //   // val dst = periodicTasks.indexOf(dstTask)
  //   (0 until tasksNumInstances(dst))
  //     .map(dstIdx => {
  //       (0 until tasksNumInstances(src))
  //         // .filterNot(srcIdx =>
  //         //   instancePreceeds(src)(dst)(srcIdx)(dstIdx)
  //         // )
  //         .count(srcIdx => {
  //           // check intersection by comparing the endpoints
  //           (
  //             num.compare(
  //               instancesReleases(src)(srcIdx),
  //               instancesReleases(dst)(dstIdx)
  //             ) <= 0 &&
  //             num.compare(
  //               instancesReleases(dst)(dstIdx),
  //               instancesDeadlines(src)(srcIdx)
  //             ) <= 0
  //           )
  //           ||
  //           (
  //             num.compare(
  //               instancesReleases(dst)(dstIdx),
  //               instancesReleases(src)(srcIdx)
  //             ) <= 0 &&
  //             num.compare(
  //               instancesReleases(src)(srcIdx),
  //               instancesDeadlines(dst)(dstIdx)
  //             ) <= 0
  //           )
  //         })
  //     })
  //     .max

// def maximumOffsetDislocation(
//     maxInstances: Int
// )(deltaOffset: TimeT, deltaPeriod: TimeT): TimeT = {
//   // TODO: make this more efficient
//   if (deltaPeriod > fracT.zero) then
//     deltaOffset + (0 until maxInstances).map(_ => deltaPeriod).sum
//   else if (deltaPeriod <= fracT.zero && deltaOffset >= fracT.zero) then deltaOffset
//   else {
//     val instance = (deltaOffset / (-deltaPeriod.toDouble.floor.toLong)) + 1
//     deltaOffset.add(
//       deltaPeriod.multiply(if (instance <= maxInstances) then instance else maxInstances)
//     )
//   }
// }

// lazy val maximalOffsetDislocations: Array[Array[TimeT]] =
//   tasks.zipWithIndex.map((src, i) => {
//     tasks.zipWithIndex.map((dst, j) => {
//       reactiveStimulus.zipWithIndex
//         .filter((stimulus, k) => {
//           reactiveStimulusSrcs(k).contains(i) &&
//           reactiveStimulusDst(k) == j
//         })
//         .map((stimulus, _) => {
//           DownsampleReactiveStimulus
//             .safeCast(stimulus)
//             .map(downsample => {
//               for (
//                 n <- 0 until tasksNumInstancesInHyperPeriod(i);
//                 m = n * downsample.getRepetitivePredecessorSkips + downsample.getInitialPredecessorSkips
//               ) yield (m.toInt, n)
//             })
//             .or(() => {
//               UpsampleReactiveStimulus
//                 .safeCast(stimulus)
//                 .map(upsample => {
//                   for (
//                     m <- 0 until tasksNumInstancesInHyperPeriod(j);
//                     n = m * upsample.getRepetitivePredecessorHolds + upsample.getInitialPredecessorHolds
//                   ) yield (m, n.toInt)
//                 })
//             })
//             .orElseGet(() => for (m <- 0 until tasksNumInstancesInHyperPeriod(j)) yield (m, m))
//         })
//     })
//   })
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
    }
    offsetsMut
  }

end PeriodicWorkloadMixin
