package idesyde.forsydeio

import scala.jdk.StreamConverters._
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger
import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.forsydeio.ForSyDeIdentificationUtils
import scala.collection.mutable.Buffer
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.connectivity.ConnectivityInspector
import spire.math._
import scala.collection.mutable
import org.jgrapht.traverse.TopologicalOrderIterator
import java.util.stream.Collectors
import forsyde.io.lib.behavior.execution.Task
import forsyde.io.lib.behavior.execution.PeriodicStimulator
import forsyde.io.lib.behavior.execution.Upsample
import forsyde.io.lib.behavior.execution.Downsample
import forsyde.io.lib.implementation.functional.RegisterLike
import forsyde.io.lib.ForSyDeHierarchy
import forsyde.io.core.SystemGraph

trait WorkloadRules {

  def identPeriodicDependentWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  ): (Set[CommunicatingAndTriggeredReactiveWorkload], Set[String]) = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var errors                  = mutable.Set[String]()
      var tasks                   = Buffer[Task]()
      var registers               = Buffer[RegisterLike]()
      var periodicStimulus        = Buffer[PeriodicStimulator]()
      var upsamples               = Buffer[Upsample]()
      var downsamples             = Buffer[Downsample]()
      var communicationGraphEdges = Buffer[(String, String, Long)]()
      model.vertexSet.forEach(v =>
        ForSyDeHierarchy.Task
          .tryView(model, v)
          .ifPresent(task => tasks :+= task)
        ForSyDeHierarchy.RegisterLike
          .tryView(model, v)
          .ifPresent(channel => registers :+= channel)
        ForSyDeHierarchy.PeriodicStimulator
          .tryView(model, v)
          .ifPresent(stim => periodicStimulus :+= stim)
        ForSyDeHierarchy.Upsample
          .tryView(model, v)
          .ifPresent(upsample => {
            upsamples :+= upsample
          })
        ForSyDeHierarchy.Downsample
          .tryView(model, v)
          .ifPresent(downsample => {
            downsamples :+= downsample
          })
      )
      // nothing can be done if there are no tasks
      // so we terminate early to avoid undefined analysis results
      // println(s"Num of tasks found in model: ${tasks.size}")
      // if (tasks.isEmpty)
      //   return Set.empty
      // now take a look which of the relevant vertexes are connected
      // taskStimulusGraph.vertexSet.forEach(src =>
      //   taskStimulusGraph.vertexSet.forEach(dst =>
      //     if (model.hasConnection(src, dst)) then taskStimulusGraph.addEdge(src, dst)
      //   )
      // )
      // do the task communication calculations
      for (
        task <- tasks;
        reg  <- registers
      ) {
        ForSyDeHierarchy.CommunicatingTask
          .tryView(task)
          .ifPresent(commTask => {
            if (model.hasConnection(commTask, reg)) {
              ForSyDeHierarchy.RegisterArrayLike
                .tryView(reg)
                .ifPresentOrElse(
                  tokenDB => {
                    val dataWritten = model
                      .getAllEdges(commTask.getViewedVertex, reg.getViewedVertex)
                      .stream
                      .mapToLong(e =>
                        e.getSourcePort
                          .map(outPort =>
                            commTask
                              .portDataWrittenSize()
                              .getOrDefault(outPort, tokenDB.elementSizeInBits())
                          )
                          .orElse(0L)
                      )
                      .sum
                    communicationGraphEdges :+= (commTask.getIdentifier(), reg
                      .getIdentifier(), dataWritten)
                  },
                  () => {
                    val dataWritten = model
                      .getAllEdges(commTask.getViewedVertex, reg.getViewedVertex)
                      .stream
                      .mapToLong(e =>
                        e.getSourcePort
                          .map(outPort =>
                            commTask
                              .portDataWrittenSize()
                              .getOrDefault(outPort, reg.sizeInBits())
                          )
                          .orElse(0L)
                      )
                      .sum
                    communicationGraphEdges :+= (commTask.getIdentifier(), reg
                      .getIdentifier(), dataWritten)
                  }
                )
            } else if (model.hasConnection(reg, commTask)) {
              ForSyDeHierarchy.RegisterArrayLike
                .tryView(reg)
                .ifPresentOrElse(
                  tokenDB => {
                    val dataRead = model
                      .getAllEdges(reg.getViewedVertex, commTask.getViewedVertex)
                      .stream
                      .mapToLong(e =>
                        e.getTargetPort
                          .map(inPort =>
                            commTask
                              .portDataReadSize()
                              .getOrDefault(inPort, tokenDB.elementSizeInBits())
                          )
                          .orElse(0L)
                      )
                      .sum
                    communicationGraphEdges :+= (reg.getIdentifier(), commTask
                      .getIdentifier(), dataRead)
                  },
                  () => {
                    val dataRead = model
                      .getAllEdges(reg.getViewedVertex, commTask.getViewedVertex)
                      .stream
                      .mapToLong(e =>
                        e.getTargetPort
                          .map(inPort =>
                            commTask
                              .portDataReadSize()
                              .getOrDefault(inPort, reg.sizeInBits())
                          )
                          .orElse(0L)
                      )
                      .sum
                    communicationGraphEdges :+= (reg.getIdentifier(), commTask
                      .getIdentifier(), dataRead)
                  }
                )
            }
          })
      }
      for (
        task       <- tasks;
        ctask      <- ForSyDeHierarchy.LoopingTask.tryView(task).toScala;
        executable <- ctask.loopSequence().asScala;
        commexec   <- ForSyDeHierarchy.CommunicatingTask.tryView(executable).toScala;
        register   <- registers
      ) {
        if (model.hasConnection(commexec, register)) {
          ForSyDeHierarchy.RegisterArrayLike
            .tryView(register)
            .ifPresentOrElse(
              tokenDB => {
                val dataWritten = model
                  .getAllEdges(commexec.getViewedVertex, register.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getSourcePort
                      .map(outPort =>
                        commexec
                          .portDataWrittenSize()
                          .getOrDefault(outPort, tokenDB.elementSizeInBits())
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (ctask.getIdentifier(), register
                  .getIdentifier(), dataWritten)
              },
              () => {
                val dataWritten = model
                  .getAllEdges(commexec.getViewedVertex, register.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getSourcePort
                      .map(outPort =>
                        commexec
                          .portDataWrittenSize()
                          .getOrDefault(outPort, register.sizeInBits())
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (ctask.getIdentifier(), register
                  .getIdentifier(), dataWritten)
              }
            )
        }
        if (model.hasConnection(register, commexec)) {
          ForSyDeHierarchy.RegisterArrayLike
            .tryView(register)
            .ifPresentOrElse(
              tokenDB => {
                val dataRead = model
                  .getAllEdges(register.getViewedVertex, commexec.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getTargetPort
                      .map(inPort =>
                        commexec
                          .portDataReadSize()
                          .getOrDefault(inPort, tokenDB.elementSizeInBits())
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (register.getIdentifier(), ctask
                  .getIdentifier(), dataRead)
              },
              () => {
                val dataRead = model
                  .getAllEdges(register.getViewedVertex, commexec.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getTargetPort
                      .map(inPort =>
                        commexec
                          .portDataReadSize()
                          .getOrDefault(inPort, register.sizeInBits())
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (register.getIdentifier(), ctask
                  .getIdentifier(), dataRead)
              }
            )
        }
      }
      // check if every task has a periodic stimulus
      lazy val stimulusGraph =
        AsSubgraph(
          model,
          (tasks ++ periodicStimulus ++ upsamples ++ downsamples)
            .map(_.getViewedVertex())
            .toSet
            .asJava
        )
      lazy val connectivityInspector = ConnectivityInspector(stimulusGraph)
      lazy val allTasksAreStimulated = tasks.forall(task =>
        periodicStimulus.exists(stim =>
          connectivityInspector.pathExists(stim.getViewedVertex(), task.getViewedVertex())
        )
      )
      // println(s"Are all tasks reachable by a periodic stimulus? ${allTasksAreStimulated}")
      if (tasks.isEmpty) { errors += "identPeriodicDependentWorkload: there are no tasks" }
      if (!allTasksAreStimulated) {
        errors += "identPeriodicDependentWorkload: not all tasks are stimulated"
      }
      val m =
        if (tasks.isEmpty || !allTasksAreStimulated)
          Set.empty
        else
          Set(
            CommunicatingAndTriggeredReactiveWorkload(
              tasks.map(_.getIdentifier()).toVector,
              tasks
                .map(t =>
                  ForSyDeHierarchy.InstrumentedBehaviour
                    .tryView(t)
                    .map(
                      _.maxSizeInBits().values().asScala.max.toLong
                    )
                    .orElse(0L) +
                    ForSyDeHierarchy.LoopingTask
                      .tryView(t)
                      .map(lt =>
                        lt.initSequence()
                          .stream()
                          .mapToLong(r =>
                            ForSyDeHierarchy.InstrumentedBehaviour
                              .tryView(r)
                              .map(_.maxSizeInBits().values().asScala.max.toLong)
                              .orElse(0L)
                          )
                          .sum() + lt
                          .loopSequence()
                          .stream()
                          .mapToLong(r =>
                            ForSyDeHierarchy.InstrumentedBehaviour
                              .tryView(r)
                              .map(_.maxSizeInBits().values().asScala.max.toLong)
                              .orElse(0L)
                          )
                          .sum()
                      )
                      .orElse(0L)
                )
                .toVector,
              tasks.map(t => taskComputationNeeds(t, model)).toVector,
              registers.map(_.getIdentifier()).toVector,
              registers.map(_.sizeInBits().toLong).toVector,
              communicationGraphEdges.toVector.map((s, t, m) => s),
              communicationGraphEdges.toVector.map((s, t, m) => t),
              communicationGraphEdges.toVector.map((s, t, m) => m),
              periodicStimulus.map(_.getIdentifier()).toVector,
              periodicStimulus.map(_.periodNumerator().toLong).toVector,
              periodicStimulus.map(_.periodDenominator().toLong).toVector,
              periodicStimulus.map(_.offsetNumerator().toLong).toVector,
              periodicStimulus.map(_.offsetDenominator().toLong).toVector,
              upsamples.map(_.getIdentifier()).toVector,
              upsamples.map(_.repetitivePredecessorHolds().toLong).toVector,
              upsamples.map(_.initialPredecessorHolds().toLong).toVector,
              downsamples.map(_.getIdentifier()).toVector,
              downsamples.map(_.repetitivePredecessorSkips().toLong).toVector,
              downsamples.map(_.initialPredecessorSkips().toLong).toVector,
              stimulusGraph
                .edgeSet()
                .stream()
                .map(e => stimulusGraph.getEdgeSource(e).getIdentifier())
                .collect(Collectors.toList())
                .asScala
                .toVector,
              stimulusGraph
                .edgeSet()
                .stream()
                .map(e => stimulusGraph.getEdgeTarget(e).getIdentifier())
                .collect(Collectors.toList())
                .asScala
                .toVector,
              tasks.filter(_.hasORSemantics()).map(_.getIdentifier()).toSet ++ upsamples
                .filter(_.hasORSemantics())
                .map(_.getIdentifier())
                .toSet ++ downsamples
                .filter(_.hasORSemantics())
                .map(_.getIdentifier())
                .toSet
            )
          )
      (m, errors.toSet)
    }
  }

  private def taskComputationNeeds(
      task: Task,
      model: SystemGraph
  ): Map[String, Map[String, Long]] = {
    var maps = mutable.Map[String, mutable.Map[String, Long]]()
    ForSyDeHierarchy.LoopingTask
      .tryView(task)
      .ifPresent(lt => {
        java.util.stream.Stream
          .concat(lt.initSequence().stream(), lt.loopSequence().stream())
          .forEach(exec => {
            ForSyDeHierarchy.InstrumentedBehaviour
              .tryView(exec)
              .ifPresent(iexec => {
                iexec
                  .computationalRequirements()
                  .forEach((opName, opReqs) => {
                    if (!maps.contains(opName)) maps(opName) = mutable.Map[String, Long]()
                    opReqs.forEach((opKey, opVal) => {
                      if (!maps(opName).contains(opKey)) maps(opName)(opKey) = 0L
                      maps(opName)(opKey) += opVal
                    })
                  })
              })
          })
      })
    ForSyDeHierarchy.InstrumentedBehaviour
      .tryView(task)
      .ifPresent(itask => {
        itask
          .computationalRequirements()
          .forEach((opName, opReqs) => {
            if (!maps.contains(opName)) maps(opName) = mutable.Map[String, Long]()
            opReqs.forEach((opKey, opVal) => {
              if (!maps(opName).contains(opKey)) maps(opName)(opKey) = 0L
              maps(opName)(opKey) += opVal
            })
          })
      })
    maps.map((k, v) => k -> v.toMap).toMap
  }
}
