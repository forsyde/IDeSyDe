package idesyde.identification.forsyde.models.workload

import idesyde.identification.forsyde.ForSyDeDecisionModel
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

/** This class represent the trait that all static-datalflow-like workloads can satisfy.
  * Two examples of such workloads is SDF and CSDF.
  *
  * In essence, this trait covers streaming task workloads that can have have a _finite_ state represetation
  * of their execution.
  */
trait StaticDataflowWorkloadMixin {
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
