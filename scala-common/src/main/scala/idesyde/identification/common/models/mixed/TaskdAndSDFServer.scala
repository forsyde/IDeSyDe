package idesyde.identification.common.models.mixed

import upickle.default._

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.workload.{InstrumentedWorkloadMixin}
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import idesyde.core.CompleteDecisionModel

final case class TaskdAndSDFServer(
    val sdfApplications: SDFApplication,
    val workload: CommunicatingAndTriggeredReactiveWorkload,
    val sdfServerPeriod: Vector[Double],
    val sdfServerBudget: Vector[Double]
) extends StandardDecisionModel
    with CompleteDecisionModel
    with InstrumentedWorkloadMixin
    derives ReadWriter {

  def bodyAsText: String = write(this)

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  val coveredElements = sdfApplications.coveredElements ++ workload.coveredElements
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    sdfApplications.processComputationalNeeds ++ workload.processComputationalNeeds
  val processSizes: Vector[Long] = sdfApplications.actorSizes ++ workload.processSizes

  val messagesMaxSizes: Vector[Long] = sdfApplications.messagesMaxSizes ++ workload.messagesMaxSizes
  val uniqueIdentifier: String       = "TaskdAndSDFServer"
}
