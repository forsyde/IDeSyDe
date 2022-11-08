package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.sdf.SDFApplication
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

  val coveredElements = sdfApplications.coveredElements ++ platform.coveredElements
  val coveredElementRelations =
    sdfApplications.coveredElementRelations ++ platform.coveredElementRelations ++
      sdfApplications.actors.zip(processMappings) ++
      sdfApplications.channels.zip(memoryMappings) ++
      messageSlotAllocations.zipWithIndex.flatMap((slots, i) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => sdfApplications.channels(i) -> ce)
      )

  val processorsFrequency: Array[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Array[Map[String, Map[String, spire.math.Rational]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Array[Long] = sdfApplications.messagesMaxSizes
  val processComputationalNeeds: Array[Map[String, Map[String, Long]]] =
    sdfApplications.actorComputationalNeeds
  val processSizes: Array[Long] = sdfApplications.processSizes

  val uniqueIdentifier: String = "SDFToPartitionedSharedMemory"

}