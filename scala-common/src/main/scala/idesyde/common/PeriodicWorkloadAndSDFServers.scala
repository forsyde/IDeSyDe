package idesyde.common

import upickle.default._

import idesyde.core.CompleteDecisionModel

final case class PeriodicWorkloadAndSDFServers(
    val workload: CommunicatingAndTriggeredReactiveWorkload,
    val sdfApplications: SDFApplicationWithFunctions
) extends StandardDecisionModel
    with CompleteDecisionModel
    with InstrumentedWorkloadMixin
    derives ReadWriter {

  def bodyAsText: String = write(this)

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  val coveredElements = workload.coveredElements ++ sdfApplications.coveredElements
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    workload.processComputationalNeeds ++ sdfApplications.processComputationalNeeds
  val processSizes: Vector[Long] = sdfApplications.actorSizes ++ workload.processSizes

  val messagesMaxSizes: Vector[Long] = workload.messagesMaxSizes ++ sdfApplications.messagesMaxSizes
  val category: String       = "PeriodicWorkloadAndSDFServers"
}
