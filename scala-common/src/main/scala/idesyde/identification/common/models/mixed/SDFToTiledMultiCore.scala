package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import idesyde.identification.common.models.platform.SchedulableTiledMultiCore
import idesyde.identification.common.models.sdf.SDFApplication

final case class SDFToTiledMultiCore(
    val sdfApplications: SDFApplication,
    val platform: SchedulableTiledMultiCore,
    val processMappings: Array[String],
    val memoryMappings: Array[String],
    val messageSlotAllocations: Array[Map[String, Array[Boolean]]]
) extends StandardDecisionModel {

  val coveredElements = sdfApplications.coveredElements ++ platform.coveredElements
  val coveredElementRelations =
    sdfApplications.coveredElementRelations ++ platform.coveredElementRelations ++
      sdfApplications.actorsIdentifiers.zip(processMappings) ++
      sdfApplications.channelsIdentifiers.zip(memoryMappings) ++
      messageSlotAllocations.zipWithIndex.flatMap((slots, i) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => sdfApplications.channelsIdentifiers(i) -> ce)
      )

  val processorsFrequency: Array[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Array[Map[String, Map[String, spire.math.Rational]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Array[Long] = sdfApplications.messagesMaxSizes
  val processComputationalNeeds: Array[Map[String, Map[String, Long]]] =
    sdfApplications.actorComputationalNeeds
  val processSizes: Array[Long] = sdfApplications.processSizes

  val uniqueIdentifier: String = "SDFToTiledMultiCore"
}
