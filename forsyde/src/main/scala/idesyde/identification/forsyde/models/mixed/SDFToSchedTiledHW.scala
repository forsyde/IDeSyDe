package idesyde.identification.forsyde.models.mixed

import scala.jdk.CollectionConverters.*

import idesyde.identification.forsyde.models.sdf.SDFApplication
import idesyde.identification.forsyde.models.platform.SchedulableTiledDigitalHardware
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.decision.Allocated
import forsyde.io.java.typed.viewers.decision.MemoryMapped
import forsyde.io.java.typed.viewers.decision.Scheduled
import forsyde.io.java.typed.viewers.platform.InstrumentedProcessingModule
import scala.collection.mutable
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.platform.InstrumentedCommunicationModule
import forsyde.io.java.typed.viewers.visualization.GreyBox
import forsyde.io.java.typed.viewers.visualization.Visualizable
import spire.math.*
import spire.algebra.*
import spire.implicits.*
import idesyde.identification.models.mixed.WCETComputationMixin
import idesyde.identification.IdentificationResult
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import scala.collection.mutable.Buffer
import forsyde.io.java.typed.viewers.decision.sdf.PASSedSDFActor

given scala.math.Fractional[Rational] = spire.compat.fractional[Rational]
given Conversion[Double, Rational]    = (d: Double) => Rational(d)

final case class SDFToSchedTiledHW(
    val sdfApplications: SDFApplication,
    val platform: SchedulableTiledDigitalHardware,
    /** A matrix of mapping from each SDF actor to each HW tile */
    val existingSchedulings: Array[Array[Boolean]],
    /** A matrix of mapping from each SDF channel to each HW tile _and_ router */
    val existingMappings: Array[Array[Boolean]]
) extends ForSyDeDecisionModel
    with WCETComputationMixin[Rational] {

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
      channelMappings: Array[Array[Boolean]],
      actorSchedulings: Array[Array[Boolean]],
      actorStaticSlots: Array[Array[Array[Int]]]
  ): ForSyDeSystemGraph = {
    val rebuilt = ForSyDeSystemGraph()
    coveredVertexes.foreach(v => rebuilt.addVertex(v))
    val finalMappings = existingMappings.zipWithIndex.map((row, i) =>
      row.zipWithIndex.map((m, j) => channelMappings(i)(j) || m)
    )
    val finalSchedulings = existingSchedulings.zipWithIndex.map((row, i) =>
      row.zipWithIndex.map((m, j) => actorSchedulings(i)(j) || m)
    )
    val allME = platform.tiledDigitalHardware.memories
    val allCE = platform.tiledDigitalHardware.allCommElems
    finalMappings.zipWithIndex.foreach((row, i) => {
      row.zipWithIndex
        .filter((m, j) => m)
        .foreach((m, j) => {
          val sdfChannel = sdfApplications.channels(i)
          val allocated  = Allocated.enforce(sdfChannel)
          allocated.insertAllocationHostsPort(rebuilt, allCE(j))
          if (j < platform.tiledDigitalHardware.tileSet.size) {
            allocated.insertAllocationHostsPort(rebuilt, allME(j))
            val mapped = MemoryMapped.enforce(sdfChannel)
            mapped.insertMappingHostsPort(rebuilt, allME(j))
            GreyBox.enforce(allME(j)).insertContainedPort(rebuilt, Visualizable.enforce(sdfChannel))
          }
        })
    })
    finalSchedulings.zipWithIndex.foreach((row, i) => {
      row.zipWithIndex
        .filter((m, j) => m)
        .foreach((m, j) => {
          val sdfActor  = sdfApplications.actors(i)
          val scheduled = Scheduled.enforce(sdfActor)
          val mapped    = MemoryMapped.enforce(sdfActor)
          scheduled.insertSchedulersPort(rebuilt, platform.schedulers(j))
          mapped.insertMappingHostsPort(rebuilt, allME(j))
          GreyBox
            .enforce(platform.schedulers(j))
            .insertContainedPort(rebuilt, Visualizable.enforce(sdfActor))
        })
    })
    // println(actorStaticSlots.map(_.map(_.mkString("[", ", ", "]")).mkString("[", ", ", "]")).mkString("[", "\n", "]"))
    val entries = platform.schedulers.map(s => Buffer[String]())
    actorStaticSlots.zipWithIndex.foreach((tile, i) => {
      // val passedList = Buffer[Integer]()
      tile.zipWithIndex.foreach((slots, j) => {
        val actor     = sdfApplications.actors(i)
        val scheduler = platform.schedulers(j)
        slots.zipWithIndex.foreach((q, l) => {
          if (q > 0) {
            StaticCyclicScheduler
              .safeCast(scheduler)
              .ifPresent(s => {
                for (k <- 0 until q) {
                  // passedList += l
                  entries(j) :+= actor.getIdentifier()
                }
              })
          }
        })
        // val passed = PASSedSDFActor.enforce(actor)
        // passed.setFiringSlots(passedList.asJava)
      })
    })
    for (
      (s, i) <- platform.schedulers.zipWithIndex
    ) {
      StaticCyclicScheduler.safeCast(s).ifPresent(scs => scs.setEntries(entries(i).asJava))
    }
    rebuilt
  }

  def uniqueIdentifier: String = "SDFToSchedTiledHW"
}

object SDFToSchedTiledHW {

  def identFromAny(
      model: Any,
      identified: Set[DecisionModel]
  )(using
      Fractional[Rational]
  )(using Conversion[Double, Rational]): IdentificationResult[SDFToSchedTiledHW] =
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
            platformModel.map(plat => identFromForSyDeSystemGraphWithDeps(m, sdfApps, plat))
          )
          .getOrElse(IdentificationResult.unfixedEmpty())
      case _ => IdentificationResult.fixedEmpty()
    }

  def identFromForSyDeSystemGraphWithDeps(
      model: ForSyDeSystemGraph,
      sdfApplications: SDFApplication,
      platform: SchedulableTiledDigitalHardware
  )(using
      Fractional[Rational]
  )(using Conversion[Double, Rational]): IdentificationResult[SDFToSchedTiledHW] = {
    // for all tasks, there exists at least one PE where all runnables are executable
    val isExecutable = sdfApplications.processComputationalNeeds.forall(aMap => {
      platform.tiledDigitalHardware.processorsProvisions.exists(pMap => {
        aMap.exists((_, opGroup) =>
          pMap.exists((_, ipcGroup) => opGroup.keySet.subsetOf(ipcGroup.keySet))
        )
      })
    })
    // All mappables (tasks, channels) have at least one element to be mapped at
    val isMappable = sdfApplications.processSizes.zipWithIndex.forall((taskSize, i) => {
      platform.tiledDigitalHardware.memories.zipWithIndex.exists((me, j) => {
        taskSize <= me.getSpaceInBits
      })
    }) && sdfApplications.messagesMaxSizes.zipWithIndex.forall((channelSize, i) => {
      platform.tiledDigitalHardware.memories.zipWithIndex.exists((me, j) => {
        channelSize <= me.getSpaceInBits
      })
    })
    // query all existing channelMappings
    // lazy val actorMappings = sdfApplications.actors.map(task => {
    //   platform.tiledDigitalHardware.memories.map(mem => {
    //     MemoryMapped
    //       .safeCast(task)
    //       .map(_.getMappingHostsPort(model).contains(mem))
    //       .orElse(false)
    //   })
    // })
    // now for channels
    lazy val channelMappings = sdfApplications.channels.map(channel => {
      platform.tiledDigitalHardware.memories.map(mem => {
        MemoryMapped
          .safeCast(channel)
          .map(_.getMappingHostsPort(model).contains(mem))
          .orElse(false)
      }) ++
        platform.tiledDigitalHardware.routers.map(router => {
          Allocated
            .safeCast(channel)
            .map(_.getAllocationHostsPort(model).contains(router))
            .orElse(false)
        })
    })
    // now find if any of task are already scheduled (mapped to a processor)
    lazy val actorSchedulings = sdfApplications.actors.map(task => {
      platform.schedulers.map(mem => {
        Scheduled
          .safeCast(task)
          .map(_.getSchedulersPort(model).contains(mem))
          .orElse(false)
      })
    })
    // finish with construction
    if (isMappable && isExecutable) then
      IdentificationResult.fixed(
        SDFToSchedTiledHW(
          sdfApplications = sdfApplications,
          platform = platform,
          existingMappings = channelMappings,
          existingSchedulings = actorSchedulings
        )
      )
    else IdentificationResult.fixedEmpty()
  }
}
