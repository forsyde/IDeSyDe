package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.forsyde.models.sdf.SDFApplication
import idesyde.identification.common.models.platform.PartitionedSharedMemoryMultiCore
import idesyde.identification.models.mixed.WCETComputationMixin
import spire.math.Rational

final case class SDFToPartitionedSharedMemory(
    val sdfApplications: SDFApplication,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Array[String],
    val memoryMappings: Array[String],
    val messageSlotAllocations: Array[Map[String, Array[Boolean]]]
) extends StandardDecisionModel
    with WCETComputationMixin[Rational] {

  def coveredVertexes = sdfApplications.coveredVertexes ++ platform.coveredVertexes

  def processorsFrequency: Array[Long] = platform.hardware.processorsFrequency
  def processorsProvisions: Array[Map[String, Map[String, spire.math.Rational]]] =
    platform.hardware.processorsProvisions

  def messagesMaxSizes: Array[Long] = sdfApplications.messagesMaxSizes
  def processComputationalNeeds: Array[Map[String, Map[String, Long]]] =
    sdfApplications.actorComputationalNeeds
  def processSizes: Array[Long] = sdfApplications.processSizes

  def uniqueIdentifier: String = "SDFToPartitionedSharedMemory"

}
