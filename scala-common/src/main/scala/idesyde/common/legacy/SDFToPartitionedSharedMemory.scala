package idesyde.common.legacy

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

  override def asJsonString(): java.util.Optional[String] = try { java.util.Optional.of(write(this)) } catch { case _ => java.util.Optional.empty() }

  override def asCBORBinary(): java.util.Optional[Array[Byte]] = try { java.util.Optional.of(writeBinary(this)) } catch { case _ => java.util.Optional.empty() }
  override def category(): String                   = "SDFToPartitionedSharedMemory"

}
