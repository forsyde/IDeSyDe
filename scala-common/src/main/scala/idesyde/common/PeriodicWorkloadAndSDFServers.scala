package idesyde.common

import scala.jdk.CollectionConverters._

import upickle.default._

import idesyde.core.DecisionModel
import java.{util => ju}

final case class PeriodicWorkloadAndSDFServers(
    val workload: CommunicatingAndTriggeredReactiveWorkload,
    val sdfApplications: SDFApplicationWithFunctions
) extends DecisionModel
    with InstrumentedWorkloadMixin
    derives ReadWriter {

  override def asJsonString(): String = write(this)

  override def asCBORBinary(): Array[Byte] = writeBinary(this)

  override def part(): ju.Set[String] =
    (workload.part().asScala ++ sdfApplications.part().asScala).asJava
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    workload.processComputationalNeeds ++ sdfApplications.processComputationalNeeds
  val processSizes: Vector[Long] = sdfApplications.actorSizes ++ workload.processSizes

  val messagesMaxSizes: Vector[Long] = workload.messagesMaxSizes ++ sdfApplications.messagesMaxSizes
  def category(): String             = "PeriodicWorkloadAndSDFServers"
}
