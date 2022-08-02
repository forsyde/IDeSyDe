package idesyde.identification.models.mixed

import scala.jdk.CollectionConverters.*

import idesyde.identification.models.sdf.SDFApplication
import idesyde.identification.models.platform.SchedulableTiledDigitalHardware
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.decision.Allocated
import forsyde.io.java.typed.viewers.decision.MemoryMapped
import forsyde.io.java.typed.viewers.decision.Scheduled
import idesyde.identification.IdentificationResult
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import scala.collection.mutable
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import idesyde.identification.DecisionModel

final case class SDFToSchedTiledHW(
    val sdfApplications: SDFApplication,
    val platform: SchedulableTiledDigitalHardware,
    /** A matrix of mapping from each SDF actor to each HW tile */
    val existingSchedulings: Array[Array[Boolean]],
    /** A matrix of mapping from each SDF channel to each HW tile _and_ router */
    val existingMappings: Array[Array[Boolean]]
)(using Fractional[BigFraction])(using Conversion[Double, BigFraction])
    extends ForSyDeDecisionModel
    with WCETComputationMixin[BigFraction] {

  def coveredVertexes: Iterable[Vertex] = sdfApplications.coveredVertexes ++
    platform.coveredVertexes

  def processComputationalNeeds: Array[Map[String, Map[String, Long]]] =
    sdfApplications.processComputationalNeeds

  def processSizes: Array[Long] = sdfApplications.processSizes

  def messagesMaxSizes: Array[Long] = sdfApplications.messagesMaxSizes

  def processorsFrequency: Array[Long] = platform.tiledDigitalHardware.processorsFrequency

  def processorsProvisions: Array[Map[String, Map[String, Double]]] =
    platform.tiledDigitalHardware.processorsProvisions

  def addMappingsAndRebuild(
      mappings: Array[Array[Boolean]],
      schedulings: Array[Array[Boolean]]
  ): ForSyDeSystemGraph = {
    val rebuilt = ForSyDeSystemGraph()
    coveredVertexes.foreach(v => rebuilt.addVertex(v))
    val finalMappings = existingMappings.zipWithIndex.map((row, i) =>
      row.zipWithIndex.map((m, j) => mappings(i)(j) || m)
    )
    val finalSchedulings = existingSchedulings.zipWithIndex.map((row, i) =>
      row.zipWithIndex.map((m, j) => schedulings(i)(j) || m)
    )
    val allME = platform.tiledDigitalHardware.memories
    val allCE = platform.tiledDigitalHardware.allCommElems
    finalMappings.zipWithIndex.foreach((row, i) => {
      row.zipWithIndex.foreach((m, j) => {
        val sdfChannel = sdfApplications.channels(i)
        val allocated  = Allocated.enforce(sdfChannel)
        if (j > platform.tiledDigitalHardware.tileSet.size) {
          allocated.insertAllocationHostsPort(rebuilt, allCE(j))
        } else {
          allocated.insertAllocationHostsPort(rebuilt, allME(j))
          val mapped = MemoryMapped.enforce(sdfChannel)
          mapped.insertMappingHostsPort(rebuilt, allME(j))
        }
      })
    })
    finalSchedulings.zipWithIndex.foreach((row, i) => {
      row.zipWithIndex.foreach((m, j) => {
        val sdfActor  = sdfApplications.actors(i)
        val scheduled = Scheduled.enforce(sdfActor)
        val mapped    = MemoryMapped.enforce(sdfActor)
        scheduled.insertSchedulersPort(rebuilt, platform.schedulers(j))
        mapped.insertMappingHostsPort(rebuilt, allME(j))
      })
    })
    rebuilt
  }

  def uniqueIdentifier: String = "SDFToSchedTiledHW"
}

object SDFToSchedTiledHW {

  def identFromAny(
      model: Any,
      identified: Set[DecisionModel]
  )(using
      Fractional[BigFraction]
  )(using Conversion[Double, BigFraction]): IdentificationResult[SDFToSchedTiledHW] =
    model match {
      case m: ForSyDeSystemGraph =>
        var sdfApplications = Option.empty[SDFApplication]
        var platformModel   = Option.empty[SchedulableTiledDigitalHardware]
        identified.foreach(d => {
          d match {
            case dWorkloadModel: SDFApplication => sdfApplications = Option(dWorkloadModel)
            case dPlatformModel: SchedulableTiledDigitalHardware =>
              platformModel = Option(dPlatformModel)
            case _ =>
          }
        })
        sdfApplications
          .flatMap(sdfApps =>
            platformModel.map(plat => identForSyDeSystemGraphWithDeps(m, sdfApps, plat))
          )
          .getOrElse(IdentificationResult.unfixedEmpty())
      case _ => IdentificationResult.fixedEmpty()
    }

  def identForSyDeSystemGraphWithDeps(
      model: ForSyDeSystemGraph,
      sdfApplications: SDFApplication,
      platform: SchedulableTiledDigitalHardware
  )(using
      Fractional[BigFraction]
  )(using Conversion[Double, BigFraction]): IdentificationResult[SDFToSchedTiledHW] = {
    // for all tasks, there exists at least one PE where all runnables are executable
    lazy val isExecutable = sdfApplications.processComputationalNeeds.forall(aMap => {
      platform.tiledDigitalHardware.processorsProvisions.exists(pMap => {
        aMap.exists((_, opGroup) =>
          pMap.exists((_, ipcGroup) => opGroup.keySet.subsetOf(ipcGroup.keySet))
        )
      })
    })
    // All mappables (tasks, channels) have at least one element to be mapped at
    lazy val isMappable = sdfApplications.processSizes.zipWithIndex.forall((taskSize, i) => {
      platform.tiledDigitalHardware.memories.zipWithIndex.exists((me, j) => {
        taskSize <= me.getSpaceInBits
      })
    }) && sdfApplications.messagesMaxSizes.zipWithIndex.forall((channelSize, i) => {
      platform.tiledDigitalHardware.memories.zipWithIndex.exists((me, j) => {
        channelSize <= me.getSpaceInBits
      })
    })
    // query all existing mappings
    val actorMappings = sdfApplications.actors.map(task => {
      platform.tiledDigitalHardware.memories.map(mem => {
        MemoryMapped
          .safeCast(task)
          .map(_.getMappingHostsPort(model).contains(mem))
          .orElse(false)
      })
    })
    // now for channels
    val channelMappings = sdfApplications.channels.map(channel => {
      platform.tiledDigitalHardware.memories.map(mem => {
        MemoryMapped
          .safeCast(channel)
          .map(_.getMappingHostsPort(model).contains(mem))
          .orElse(false)
      })
    })
    // now find if any of task are already scheduled (mapped to a processor)
    val actorSchedulings = sdfApplications.actors.map(task => {
      platform.schedulers.map(mem => {
        Scheduled
          .safeCast(task)
          .map(_.getSchedulersPort(model).contains(mem))
          .orElse(false)
      })
    })
    // finish with construction
    // scribe.debug(s"1 ${instrumentedExecutables.length == workloadModel.tasks.length} &&" +
    //   s"2 ${instrumentedPEsRange.length == platformModel.hardware.processingElems.length} &&" +
    //   s"${isMappable} && ${isExecutable}")
    if (isMappable && isExecutable) then
      IdentificationResult.fixed(
        SDFToSchedTiledHW(
          sdfApplications = sdfApplications,
          platform = platform,
          existingMappings = actorMappings ++ channelMappings,
          existingSchedulings = actorSchedulings
        )
      )
    else IdentificationResult.fixedEmpty()
  }
}
