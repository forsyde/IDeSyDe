package idesyde.identification.models.workload

import idesyde.identification.DecisionModel
import org.jgrapht.Graph
import org.jgrapht.graph.DefaultEdge

/** This class represent the trait that all apriodic-style workloads can satisfy. A workload is
  * aperiodic if at least one job of the workload is always ready to run.
  *
  * So if we have a task graph, it means that at least one job of this task graph is always ready to
  * run, even with precedence and other constraints.
  */
trait AperiodicWorkload[TaskT, MQueueT] extends DecisionModel {
  def tasks: Array[TaskT]
  def messageQueues: Array[MQueueT]
  def controlFlowGraph: Graph[(TaskT, Int), DefaultEdge]
}
