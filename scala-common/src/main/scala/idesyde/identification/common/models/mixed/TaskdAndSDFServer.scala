package idesyde.identification.common.models.mixed

import upickle.default._

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.sdf.SDFApplication
import idesyde.identification.common.models.workload.{InstrumentedWorkloadMixin}
import idesyde.identification.common.models.CommunicatingAndTriggeredReactiveWorkload
import idesyde.core.CompleteDecisionModel

final case class TaskdAndSDFServer(
    val sdfApplication: SDFApplication,
    val workload: CommunicatingAndTriggeredReactiveWorkload,
    val sdfServerPeriod: Vector[Double],
    val sdfServerBudget: Vector[Double]
) extends StandardDecisionModel
    with CompleteDecisionModel
    with InstrumentedWorkloadMixin
    derives ReadWriter {

  def bodyAsText: String = write(this)

  def bodyAsBinary: Array[Byte] = writeBinary(this)

  val coveredElements = sdfApplication.coveredElements ++ workload.coveredElements
  val coveredElementRelations =
    sdfApplication.coveredElementRelations ++ workload.coveredElementRelations
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    sdfApplication.processComputationalNeeds ++ workload.processComputationalNeeds
  val processSizes: Vector[Long] = sdfApplication.actorSizes ++ workload.processSizes

  val messagesMaxSizes: Vector[Long] = sdfApplication.messagesMaxSizes ++ workload.messagesMaxSizes
  val uniqueIdentifier: String       = "TaskdAndSDFServer"
}
