package idesyde.identification.models.workload

import idesyde.identification.ForSyDeDecisionModel
import idesyde.identification.models.workload.AperiodicWorkloadMixin
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.impl.DataBlock

final case class SimpleAperiodicWorkload()
    extends ForSyDeDecisionModel
    with AperiodicWorkloadMixin {

  // Members declared in idesyde.identification.models.workload.AperiodicWorkloadMixin
  def communicationGraph: org.jgrapht.Graph[Int, Long]                                       = ???
  def instanceControlFlowGraph: org.jgrapht.Graph[(Int, Int), org.jgrapht.graph.DefaultEdge] = ???
  def messageQueuesSizes: Array[Long]                                                        = ???
  def numInstances: Array[Array[Int]]                                                        = ???
  def numMessageQueues: Array[Int]                                                           = ???
  def numTasks: Int                                                                          = ???
  def taskSizes: Array[Long]                                                                 = ???

  // Members declared in idesyde.identification.DecisionModel
  def coveredVertexes: Iterable[SimpleAperiodicWorkload.this.VertexT] = ???
  def uniqueIdentifier: String                                        = ???

}
