package idesyde.identification.models.workload

import idesyde.identification.ForSyDeDecisionModel
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

/** This class represent the trait that all apriodic-style workloads can satisfy. A workload is
  * aperiodic if at least one job of the workload is always ready to run.
  *
  * So if we have a task graph, it means that at least one job of this task graph is always ready to
  * run, even with precedence and other constraints.
  */
trait AperiodicWorkloadMixin {
  def numTasks: Int
  def taskSizes: Array[Long]
  def numInstances: Array[Array[Int]]
  def numMessageQueues: Array[Int]
  def messageQueuesSizes: Array[Long]

  /** The edges of the communication graph should have numbers describing how much data is
    * transferred from tasks to message queues.
    */
  def communicationGraph: Graph[Int, Long]

  /** The edges of the instance control flow graph detail if a instance T_i,k shoud be preceeded of
    * an instance T_j,l
    *
    * In other words, it is a precedence graph at the instance (sometimes called jobs) level.
    */
  def instanceControlFlowGraph: Graph[(Int, Int), DefaultEdge]
}
