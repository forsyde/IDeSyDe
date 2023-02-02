package idesyde.identification.choco.models.mixed

import idesyde.identification.common.models.mixed.SDFandTask

case class SDFTaskandPlatform {
  val mixmodel: SDFandTask,
  val platform: PartitionedSharedMemoryMultiCore,
  val processMappings: Vector[(String, String)],
  val processSchedulings: Vector[(String, String)],
  val channelMappings: Vector[(String, String)],
  val channelSlotAllocations: Map[String, Map[String, Vector[Boolean]]],
  val maxUtilizations: Map[String, Rational]
  ) extends StandardDecisionModel
  with WCETComputationMixin(workload, platform.hardware) {

    val coveredElements: Set[String] = mixmodel.coveredElements ++ platform.coveredElements

    val coveredElementRelations: Set[(String, String)] =
      mixmodel.coveredElementRelations ++ platform.coveredElementRelations ++
        processSchedulings.toSet ++
        processMappings.toSet ++
        channelMappings.toSet ++
        channelSlotAllocations
          .flatMap[String, String]((channel, slots) =>
            platform.hardware.communicationElems
              .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
              .map(ce => (channel, ce))
          )
          .toSet

  

    def uniqueIdentifier: String = "SDFTaskandPlatform"
  }
