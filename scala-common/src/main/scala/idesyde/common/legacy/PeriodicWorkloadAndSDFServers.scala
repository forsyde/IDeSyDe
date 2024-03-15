package idesyde.common.legacy

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

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }

  override def part(): ju.Set[String] =
    (workload.part().asScala ++ sdfApplications.part().asScala).asJava
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    workload.processComputationalNeeds ++ sdfApplications.processComputationalNeeds
  val processSizes: Vector[Long] = sdfApplications.actorSizes ++ workload.processSizes

  val messagesMaxSizes: Vector[Long] = workload.messagesMaxSizes ++ sdfApplications.messagesMaxSizes
  override def category(): String             = "PeriodicWorkloadAndSDFServers"
}
