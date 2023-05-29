package idesyde.common

final case class SDFToPartitionedSharedMemory(
    val sdfApplications: SDFApplicationWithFunctions,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Vector[String],
    val memoryMappings: Vector[String],
    val messageSlotAllocations: Vector[Map[String, Vector[Boolean]]]
) extends StandardDecisionModel
    with WCETComputationMixin(sdfApplications, platform.hardware) {

  val coveredElements = sdfApplications.coveredElements ++ platform.coveredElements ++ (
    sdfApplications.actorsIdentifiers.zip(processMappings) ++
      sdfApplications.channelsIdentifiers.zip(memoryMappings) ++
      messageSlotAllocations.zipWithIndex.flatMap((slots, i) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => sdfApplications.channelsIdentifiers(i) -> ce)
      )
  ).map(_.toString)

  val wcets = computeWcets

  val uniqueIdentifier: String = "SDFToPartitionedSharedMemory"

}
