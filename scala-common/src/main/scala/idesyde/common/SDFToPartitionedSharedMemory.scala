package idesyde.common

import scala.jdk.CollectionConverters.*

import upickle.default._

import idesyde.core.DecisionModel
import java.{util => ju}

final case class SDFToPartitionedSharedMemory(
    val sdfApplications: SDFApplicationWithFunctions,
    val platform: PartitionedSharedMemoryMultiCore,
    val processMappings: Vector[String],
    val memoryMappings: Vector[String],
    val messageSlotAllocations: Vector[Map[String, Vector[Boolean]]]
) extends DecisionModel
    with WCETComputationMixin(sdfApplications, platform.hardware)
    derives ReadWriter {

  override def part(): ju.Set[String] =
    (sdfApplications.part().asScala ++ platform.part().asScala ++ (
      sdfApplications.actorsIdentifiers.zip(processMappings) ++
        sdfApplications.channelsIdentifiers.zip(memoryMappings) ++
        messageSlotAllocations.zipWithIndex.flatMap((slots, i) =>
          platform.hardware.communicationElems
            .filter(ce => slots.contains(ce) && slots(ce).exists(b => b))
            .map(ce => sdfApplications.channelsIdentifiers(i) -> ce)
        )
    ).map(_.toString)).asJava

  val wcets = computeWcets

  override def asJsonString(): String = write(this)

  override def asCBORBinary(): Array[Byte] = writeBinary(this)
  def category(): String                   = "SDFToPartitionedSharedMemory"

}
