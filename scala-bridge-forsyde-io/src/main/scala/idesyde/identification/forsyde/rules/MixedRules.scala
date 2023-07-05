package idesyde.identification.forsyde.rules

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.ForSyDeDesignModel
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.SDFToTiledMultiCore
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.typed.viewers.decision.results.AnalysedGenericProcessingModule
import forsyde.io.java.typed.viewers.decision.Scheduled
import forsyde.io.java.typed.viewers.visualization.GreyBox
import forsyde.io.java.typed.viewers.visualization.Visualizable
import forsyde.io.java.typed.viewers.platform.runtime.AbstractScheduler
import forsyde.io.java.typed.viewers.decision.MemoryMapped
import forsyde.io.java.typed.viewers.platform.GenericMemoryModule
import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.common.PartitionedSharedMemoryMultiCore
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import forsyde.io.java.typed.viewers.nonfunctional.UtilizationBoundedProcessingElem
import spire.math.Rational
import forsyde.io.java.typed.viewers.platform.runtime.StaticCyclicScheduler
import scala.jdk.CollectionConverters._
import forsyde.io.java.core.VertexProperty
import forsyde.io.java.typed.viewers.decision.platform.runtime.AllocatedSingleSlotSCS
import forsyde.io.java.typed.viewers.decision.platform.runtime.AllocatedSharedSlotSCS
import scala.collection.mutable.Buffer
import forsyde.io.java.typed.viewers.decision.results.AnalyzedActor

trait MixedRules {

  def integratePeriodicWorkloadToPartitionedSharedMultiCore(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[? <: DesignModel] = {
    val model = designModel
      .flatMap(_ match {
        case ForSyDeDesignModel(forSyDeSystemGraph) =>
          Some(forSyDeSystemGraph)
        case _ => None
      })
      .foldRight(ForSyDeSystemGraph())((a, b) => b.merge(a))
    val solveds = decisionModel.flatMap(_ match {
      case dse: PeriodicWorkloadToPartitionedSharedMultiCore => {
        if (
          !dse.processMappings.isEmpty && !dse.processSchedulings.isEmpty && !dse.channelMappings.isEmpty
        )
          Some(dse)
        else None
      }
      case _ => None
    })
    for (solved <- solveds; rebuilt = ForSyDeSystemGraph().merge(model)) yield {
      for (
        (taskId, schedId) <- solved.processSchedulings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = Scheduled.enforce(rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
        sched = AbstractScheduler
          .enforce(rebuilt.queryVertex(schedId).orElse(rebuilt.newVertex(schedId)))
      ) {
        task.insertSchedulersPort(rebuilt, sched)
        GreyBox.enforce(sched).insertContainedPort(rebuilt, Visualizable.enforce(task))
      }
      for (
        (taskId, memId) <- solved.processMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        task = MemoryMapped
          .enforce(rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
        mem = GenericMemoryModule
          .enforce(rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
      ) {
        task.insertMappingHostsPort(rebuilt, mem)
      }
      for (
        (channelId, memId) <- solved.channelMappings;
        // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
        // TODO: fix it to be stable later
        channel = MemoryMapped
          .enforce(rebuilt.queryVertex(channelId).orElse(rebuilt.newVertex(channelId)));
        mem = GenericMemoryModule
          .enforce(rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
      ) {
        channel.insertMappingHostsPort(rebuilt, mem)
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def integratePeriodicWorkloadToPartitionedSharedMultiCoreFromNothing(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[? <: DesignModel] = {
    val model = designModel
      .flatMap(_ match {
        case ForSyDeDesignModel(forSyDeSystemGraph) =>
          Some(forSyDeSystemGraph)
        case _ => None
      })
      .foldRight(ForSyDeSystemGraph())((a, b) => b.merge(a))
    val solveds = decisionModel.flatMap(_ match {
      case dse: PeriodicWorkloadToPartitionedSharedMultiCore => {
        if (
          !dse.processMappings.isEmpty && !dse.processSchedulings.isEmpty && !dse.channelMappings.isEmpty
        )
          Some(dse)
        else None
      }
      case _ => None
    })
    if (model.vertexSet().isEmpty()) {
      for (solved <- solveds; rebuilt = ForSyDeSystemGraph().merge(model)) yield {
        for (
          (taskId, schedId) <- solved.processSchedulings;
          // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
          // TODO: fix it to be stable later
          task = Scheduled.enforce(rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
          sched = AbstractScheduler
            .enforce(rebuilt.queryVertex(schedId).orElse(rebuilt.newVertex(schedId)))
        ) {
          task.insertSchedulersPort(rebuilt, sched)
          GreyBox.enforce(sched).insertContainedPort(rebuilt, Visualizable.enforce(task))
        }
        for (
          (taskId, memId) <- solved.processMappings;
          // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
          // TODO: fix it to be stable later
          task = MemoryMapped
            .enforce(rebuilt.queryVertex(taskId).orElse(rebuilt.newVertex(taskId)));
          mem = GenericMemoryModule
            .enforce(rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
        ) {
          task.insertMappingHostsPort(rebuilt, mem)
        }
        for (
          (channelId, memId) <- solved.channelMappings;
          // ok for now because it is a 1-to-many situation wit the current Decision Models (2023-01-16)
          // TODO: fix it to be stable later
          channel = MemoryMapped
            .enforce(rebuilt.queryVertex(channelId).orElse(rebuilt.newVertex(channelId)));
          mem = GenericMemoryModule
            .enforce(rebuilt.queryVertex(memId).orElse(rebuilt.newVertex(memId)))
        ) {
          channel.insertMappingHostsPort(rebuilt, mem)
        }
        ForSyDeDesignModel(rebuilt)
      }
    } else Set()
  }

  def integrateSDFToTiledMultiCore(
      decisionModel: Set[DecisionModel],
      designModel: Set[DesignModel]
  ): Set[? <: DesignModel] = {
    val model = designModel
      .flatMap(_ match {
        case ForSyDeDesignModel(forSyDeSystemGraph) =>
          Some(forSyDeSystemGraph)
        case _ => None
      })
      .foldRight(ForSyDeSystemGraph())((a, b) => b.merge(a))
    val solveds = decisionModel.flatMap(_ match {
      case dse: SDFToTiledMultiCore => {
        if (!dse.messageMappings.isEmpty && !dse.processMappings.isEmpty)
          Some(dse)
        else None
      }
      case _ => None
    })
    for (solved <- solveds; rebuilt = ForSyDeSystemGraph().merge(model)) yield {
      // first, we take care of the process mappings
      for (
        (mem, i) <- solved.processMappings.zipWithIndex;
        actorId   = solved.sdfApplications.actorsIdentifiers(i);
        memIdx    = solved.platform.hardware.memories.indexOf(mem);
        proc      = solved.platform.hardware.processors(memIdx);
        scheduler = solved.platform.runtimes.schedulers(memIdx)
      ) {
        val v =
          MemoryMapped.enforce(rebuilt.queryVertex(actorId).orElse(rebuilt.newVertex(actorId)))
        val m =
          GenericMemoryModule.enforce(rebuilt.queryVertex(mem).orElse(rebuilt.newVertex(mem)))
        v.setMappingHostsPort(
          rebuilt,
          java.util.Set.of(GenericMemoryModule.enforce(m))
        )
        val s = AbstractScheduler.enforce(
          rebuilt
            .queryVertex(scheduler)
            .orElse(rebuilt.newVertex(scheduler))
        )
        Scheduled.enforce(v).setSchedulersPort(rebuilt, java.util.Set.of(s))
      }
      // now, we take care of the memory mappings
      for (
        (mem, i) <- solved.messageMappings.zipWithIndex;
        channelID = solved.sdfApplications.channelsIdentifiers(i);
        memIdx    = solved.platform.hardware.memories.indexOf(mem)
      ) {
        val v =
          MemoryMapped.enforce(rebuilt.queryVertex(channelID).orElse(rebuilt.newVertex(channelID)))
        val m =
          GenericMemoryModule.enforce(rebuilt.queryVertex(mem).orElse(rebuilt.newVertex(mem)))
        v.setMappingHostsPort(
          rebuilt,
          java.util.Set.of(GenericMemoryModule.enforce(m))
        )
      }
      // now, we put the schedule in each scheduler
      for (
        (list, si) <- solved.schedulerSchedules.zipWithIndex;
        proc      = solved.platform.hardware.processors(si);
        scheduler = solved.platform.runtimes.schedulers(si)
      ) {
        val scs = AllocatedSingleSlotSCS.enforce(
          rebuilt
            .queryVertex(scheduler)
            .orElse(rebuilt.newVertex(scheduler))
        )
        scs.setEntries(list.asJava)
      }
      // finally, the channel comm allocations
      var commAllocs = solved.platform.hardware.communicationElementsMaxChannels.map(maxVc =>
        Buffer.fill(maxVc)(Buffer.empty[String])
      )
      for (
        (maxVc, ce) <- solved.platform.hardware.communicationElementsMaxChannels.zipWithIndex;
        (dict, c)   <- solved.messageSlotAllocations.zipWithIndex;
        vc          <- 0 until maxVc;
        commElem = solved.platform.hardware.communicationElems(ce);
        if dict.getOrElse(commElem, Vector.fill(maxVc)(false))(vc);
        cId = solved.sdfApplications.channelsIdentifiers(c)
      ) {
        commAllocs(ce)(vc) += cId
      }
      for ((ce, i) <- solved.platform.hardware.communicationElems.zipWithIndex) {
        val comm = AllocatedSharedSlotSCS.enforce(
          rebuilt
            .queryVertex(ce)
            .orElse(rebuilt.newVertex(ce))
        )
        comm.setEntries(commAllocs(i).map(_.asJava).asJava)
      }
      // add the throughputs for good measure
      for (
        (a, ai) <- solved.sdfApplications.actorsIdentifiers.zipWithIndex;
        th = solved.sdfApplications.minimumActorThroughputs(ai)
      ) {
        val act = AnalyzedActor.enforce(
          rebuilt
            .queryVertex(a)
            .orElse(rebuilt.newVertex(a))
        )
        val frac = Rational(th)
        act.setThroughputInSecsNumerator(frac.numeratorAsLong)
        act.setThroughputInSecsDenominator(frac.denominatorAsLong)
      }
      ForSyDeDesignModel(rebuilt)
    }
  }

  def identPeriodicWorkloadToPartitionedSharedMultiCoreWithUtilization(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): Set[PeriodicWorkloadToPartitionedSharedMultiCore] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      val app = identified
        .filter(_.isInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
        .map(_.asInstanceOf[CommunicatingAndTriggeredReactiveWorkload])
      val plat = identified
        .filter(_.isInstanceOf[PartitionedSharedMemoryMultiCore])
        .map(_.asInstanceOf[PartitionedSharedMemoryMultiCore])
      // if ((runtimes.isDefined && plat.isEmpty) || (runtimes.isEmpty && plat.isDefined))
      app.flatMap(a =>
        plat.map(p =>
          PeriodicWorkloadToPartitionedSharedMultiCore(
            workload = a,
            platform = p,
            processMappings = Vector.empty,
            processSchedulings = Vector.empty,
            channelMappings = Vector.empty,
            channelSlotAllocations = Map(),
            maxUtilizations = (for (
              pe <- p.hardware.processingElems;
              peVertex = model.queryVertex(pe);
              if peVertex.isPresent() && UtilizationBoundedProcessingElem.conforms(peVertex.get());
              utilVertex = UtilizationBoundedProcessingElem.safeCast(peVertex.get()).get()
            )
              yield pe -> utilVertex.getMaxUtilizationNumerator().toDouble / utilVertex
                .getMaxUtilizationDenominator()
                .toDouble).toMap
          )
        )
      )
    }
  }
}
