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
    with WCETComputationMixin(sdfApplications, platform.hardware) {

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

  val wcets = computeWcets

  val uniqueIdentifier: String = "SDFToPartitionedSharedMemory"

}
