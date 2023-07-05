package idesyde.common

import upickle.default.*

import idesyde.core.CompleteDecisionModel

final case class SDFToTiledMultiCore(
    val sdfApplications: SDFApplicationWithFunctions,
    val platform: SchedulableTiledMultiCore,
    val processMappings: Vector[String],
    val messageMappings: Vector[String],
    val schedulerSchedules: Vector[Vector[String]],
    val messageSlotAllocations: Vector[Map[String, Vector[Boolean]]],
    val actorThroughputs: Vector[Double]
) extends StandardDecisionModel
    with CompleteDecisionModel
    with WCETComputationMixin(sdfApplications, platform)
    derives ReadWriter {

  val coveredElements =
    sdfApplications.coveredElements ++ platform.coveredElements ++ (sdfApplications.actorsIdentifiers
      .zip(processMappings) ++
      sdfApplications.channelsIdentifiers.zip(messageMappings) ++
      messageSlotAllocations.zipWithIndex.flatMap((slots, i) =>
        platform.hardware.communicationElems
          .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
          .map(ce => sdfApplications.channelsIdentifiers(i) -> ce)
      )).map(_.toString)

  val processorsFrequency: Vector[Long] = platform.hardware.processorsFrequency
  val processorsProvisions: Vector[Map[String, Map[String, Double]]] =
    platform.hardware.processorsProvisions

  val messagesMaxSizes: Vector[Long] = sdfApplications.messagesMaxSizes
  val processComputationalNeeds: Vector[Map[String, Map[String, Long]]] =
    sdfApplications.actorComputationalNeeds
  val processSizes: Vector[Long] = sdfApplications.processSizes

  val wcets = computeWcets

  val bodyAsBinary: Array[Byte] = writeBinary(this)

  val bodyAsText: String = write(this)

  val category: String = "SDFToTiledMultiCore"
}
