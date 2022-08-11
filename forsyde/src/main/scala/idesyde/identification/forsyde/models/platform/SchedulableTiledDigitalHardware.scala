package idesyde.identification.forsyde.models.platform

import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.typed.viewers.platform.runtime.FixedPriorityScheduler
import forsyde.io.java.typed.viewers.platform.runtime.TimeTriggeredScheduler
import forsyde.io.java.typed.viewers.platform.runtime.RoundRobinScheduler
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import forsyde.io.java.core.Vertex
import forsyde.io.java.core.ForSyDeSystemGraph
import java.util.stream.Collectors
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.decision.Allocated
import forsyde.io.java.typed.viewers.platform.GenericProcessingModule
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

final case class SchedulableTiledDigitalHardware(
    val tiledDigitalHardware: TiledDigitalHardware,
    val schedulers: Array[AbstractScheduler]
) extends ForSyDeDecisionModel {

  val coveredVertexes: Iterable[Vertex] = schedulers.map(_.getViewedVertex())
    ++ tiledDigitalHardware.coveredVertexes

  val isFixedPriority = schedulers.map(FixedPriorityScheduler.conforms(_).booleanValue())
  val isTimeTriggered = schedulers.map(TimeTriggeredScheduler.conforms(_).booleanValue())
  val isRoundRobin    = schedulers.map(RoundRobinScheduler.conforms(_).booleanValue())
  val isStaticCycle   = schedulers.map(StaticCyclicScheduler.conforms(_).booleanValue())

  val fixedPrioritySchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isFixedPriority(i))
      .map((s, i) => FixedPriorityScheduler.enforce(s))
  val timeTriggeredSchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isTimeTriggered(i))
      .map((s, i) => TimeTriggeredScheduler.enforce(s))
  val roundRobinSchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isRoundRobin(i))
      .map((s, i) => RoundRobinScheduler.enforce(s))
  val staticCycleSchedulers =
    schedulers.zipWithIndex
      .filter((s, i) => isStaticCycle(i))
      .map((s, i) => StaticCyclicScheduler.enforce(s))

  val fixedPriorityTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isFixedPriority(i))
  val timeTriggeredTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isTimeTriggered(i))
  val roundRobinTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isRoundRobin(i))
  val staticCyclicTiles: Array[Int] = tiledDigitalHardware.tileSet
    .filter(i => isStaticCycle(i))

  val schedulerSet = (0 until schedulers.size).toArray

  val uniqueIdentifier: String = "SchedulableTiledDigitalHardware"
}

object SchedulableTiledDigitalHardware {

  def identFromAny(
    model: Any,
    identified: Set[DecisionModel]
  ): IdentificationResult[SchedulableTiledDigitalHardware] = 
    model match {
      case m: ForSyDeSystemGraph =>
        val tiledDigitalHardware = identified.find(_.isInstanceOf[TiledDigitalHardware]).map(_.asInstanceOf[TiledDigitalHardware])
        tiledDigitalHardware.map(t => indetFromForSyDeSystemGraphWithDeps(m, t)).getOrElse(IdentificationResult.unfixedEmpty())
      case _ => IdentificationResult.fixedEmpty()
    }

  def indetFromForSyDeSystemGraphWithDeps(
    model: ForSyDeSystemGraph,
    tiledDigitalHardware: TiledDigitalHardware
  ): IdentificationResult[SchedulableTiledDigitalHardware] = {
    val schedulers = model.vertexSet.stream
      .filter(AbstractScheduler.conforms(_))
      .map(AbstractScheduler.enforce(_))
      .collect(Collectors.toList())
      .asScala
      .toArray
    // check if all schedulers are allocated to each tile
    val schedulerAllocations: Array[Int] = schedulers.map(scheduler => {
      Allocated
        .safeCast(scheduler)
        .flatMap(allocated => {
          allocated
            .getAllocationHostsPort(model)
            .stream
            .flatMap(host => {
              GenericProcessingModule
                .safeCast(host)
                .stream
                .mapToInt(peHost => {
                  tiledDigitalHardware.processors.indexOf(peHost)
                })
                .boxed
            })
            .findAny
        })
        .orElse(-1)
    })
    if (!tiledDigitalHardware.tileSet.forall(i => schedulerAllocations.contains(i)))
      scribe.debug("Some processing elements are not allocated. Skipping.")
      IdentificationResult.fixedEmpty()
    else
      scribe.debug(
        "found a conforming Schedulable Networked HW Model with: " +
          s"${schedulers.size} schedulers"
      )
      IdentificationResult.fixed(
        SchedulableTiledDigitalHardware(
          tiledDigitalHardware = tiledDigitalHardware,
          schedulers = schedulers
        )
      )
  }

}

