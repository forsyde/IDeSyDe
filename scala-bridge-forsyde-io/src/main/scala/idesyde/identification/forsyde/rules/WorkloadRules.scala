package idesyde.identification.forsyde.rules

import scala.jdk.StreamConverters._
import scala.jdk.CollectionConverters._
import scala.jdk.OptionConverters._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.utils.Logger
import idesyde.common.CommunicatingAndTriggeredReactiveWorkload
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
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

trait WorkloadRules {

  def identPeriodicDependentWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Set[CommunicatingAndTriggeredReactiveWorkload] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var tasks                   = Buffer[Task]()
      var registers               = Buffer[RegisterLike]()
      var periodicStimulus        = Buffer[PeriodicStimulator]()
      var upsamples               = Buffer[Upsample]()
      var downsamples             = Buffer[Downsample]()
      var communicationGraphEdges = Buffer[(String, String, Long)]()
      model.vertexSet.forEach(v =>
        ForSyDeHierarchy.Task
          .tryView(v)
          .ifPresent(task => tasks :+= task)
        ForSyDeHierarchy.RegisterLike
          .tryView(v)
          .ifPresent(channel => RegisterLikes :+= channel)
        ForSyDeHierarchy.PeriodicStimulator
          .tryView(v)
          .ifPresent(stim => periodicStimulus :+= stim)
        ForSyDeHierarchy.Upsample
          .tryView(v)
          .ifPresent(upsample => {
            upsamples :+= upsample
          })
        ForSyDeHierarchy.Downsample
          .tryView(v)
          .ifPresent(downsample => {
            downsamples :+= downsample
          })
      )
      // nothing can be done if there are no tasks
      // so we terminate early to avoid undefined analysis results
      logger.debug(s"Num of tasks found in model: ${tasks.size}")
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
                .safeCast(reg)
                .ifPresentOrElse(
                  tokenDB => {
                    val dataWritten = model
                      .getAllEdges(commTask.getViewedVertex, reg.getViewedVertex)
                      .stream
                      .mapToLong(e =>
                        e.getSourcePort
                          .map(outPort =>
                            commTask
                              .getPortDataWrittenSize()
                              .getOrDefault(outPort, tokenDB.getTokenSizeInBits())
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
                              .getPortDataWrittenSize()
                              .getOrDefault(outPort, reg.getMaxSizeInBits)
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
                .safeCast(reg)
                .ifPresentOrElse(
                  tokenDB => {
                    val dataRead = model
                      .getAllEdges(reg.getViewedVertex, commTask.getViewedVertex)
                      .stream
                      .mapToLong(e =>
                        e.getTargetPort
                          .map(inPort =>
                            commTask
                              .getPortDataReadSize()
                              .getOrDefault(inPort, tokenDB.getTokenSizeInBits())
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
                              .getPortDataReadSize()
                              .getOrDefault(inPort, reg.getMaxSizeInBits)
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
        task         <- tasks;
        ctask        <- ForSyDeHierarchy.LoopingTask.tryView(task).toScala;
        executable   <- ctask.getLoopSequencePort(model).asScala;
        commexec     <- ForSyDeHierarchy.CommunicatingExecutable.tryView(executable).toScala;
        RegisterLike <- RegisterLikes
      ) {
        if (model.hasConnection(commexec, RegisterLike)) {
          ForSyDeHierarchy.RegisterArrayLike
            .safeCast(RegisterLike)
            .ifPresentOrElse(
              tokenDB => {
                val dataWritten = model
                  .getAllEdges(commexec.getViewedVertex, RegisterLike.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getSourcePort
                      .map(outPort =>
                        commexec
                          .getPortDataWrittenSize()
                          .getOrDefault(outPort, tokenDB.getTokenSizeInBits())
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (ctask.getIdentifier(), RegisterLike
                  .getIdentifier(), dataWritten)
              },
              () => {
                val dataWritten = model
                  .getAllEdges(commexec.getViewedVertex, RegisterLike.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getSourcePort
                      .map(outPort =>
                        commexec
                          .getPortDataWrittenSize()
                          .getOrDefault(outPort, RegisterLike.getMaxSizeInBits)
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (ctask.getIdentifier(), RegisterLike
                  .getIdentifier(), dataWritten)
              }
            )
        }
        if (model.hasConnection(RegisterLike, commexec)) {
          ForSyDeHierarchy.RegisterArrayLike
            .safeCast(RegisterLike)
            .ifPresentOrElse(
              tokenDB => {
                val dataRead = model
                  .getAllEdges(RegisterLike.getViewedVertex, commexec.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getTargetPort
                      .map(inPort =>
                        commexec
                          .getPortDataReadSize()
                          .getOrDefault(inPort, tokenDB.getTokenSizeInBits())
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (RegisterLike.getIdentifier(), ctask
                  .getIdentifier(), dataRead)
              },
              () => {
                val dataRead = model
                  .getAllEdges(RegisterLike.getViewedVertex, commexec.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getTargetPort
                      .map(inPort =>
                        commexec
                          .getPortDataReadSize()
                          .getOrDefault(inPort, RegisterLike.getMaxSizeInBits)
                      )
                      .orElse(0L)
                  )
                  .sum
                communicationGraphEdges :+= (RegisterLike.getIdentifier(), ctask
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
      logger.debug(s"Are all tasks reachable by a periodic stimulus? ${allTasksAreStimulated}")
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
                  .map(_.getSizeInBits().toLong)
                  .orElse(0L) +
                  LoopingTask
                    .safeCast(t)
                    .map(lt =>
                      lt.getInitSequencePort(model)
                        .stream()
                        .mapToLong(r =>
                          InstrumentedExecutable
                            .safeCast(r)
                            .map(_.getSizeInBits().toLong)
                            .orElse(0L)
                        )
                        .sum() + lt
                        .getLoopSequencePort(model)
                        .stream()
                        .mapToLong(r =>
                          InstrumentedExecutable
                            .safeCast(r)
                            .map(_.getSizeInBits().toLong)
                            .orElse(0L)
                        )
                        .sum()
                    )
                    .orElse(0L)
              )
              .toVector,
            tasks.map(t => taskComputationNeeds(t, model)).toVector,
            registers.map(_.getIdentifier()).toVector,
            registers.map(_.getMaxSizeInBits().toLong).toVector,
            communicationGraphEdges.toVector.map((s, t, m) => s),
            communicationGraphEdges.toVector.map((s, t, m) => t),
            communicationGraphEdges.toVector.map((s, t, m) => m),
            periodicStimulus.map(_.getIdentifier()).toVector,
            periodicStimulus.map(_.getPeriodNumerator().toLong).toVector,
            periodicStimulus.map(_.getPeriodDenominator().toLong).toVector,
            periodicStimulus.map(_.getOffsetNumerator().toLong).toVector,
            periodicStimulus.map(_.getOffsetDenominator().toLong).toVector,
            upsamples.map(_.getIdentifier()).toVector,
            upsamples.map(_.getRepetitivePredecessorHolds().toLong).toVector,
            upsamples.map(_.getInitialPredecessorHolds().toLong).toVector,
            downsamples.map(_.getIdentifier()).toVector,
            downsamples.map(_.getRepetitivePredecessorSkips().toLong).toVector,
            downsamples.map(_.getInitialPredecessorSkips().toLong).toVector,
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
            tasks.filter(_.getHasORSemantics()).map(_.getIdentifier()).toSet ++ upsamples
              .filter(_.getHasORSemantics())
              .map(_.getIdentifier())
              .toSet ++ downsamples
              .filter(_.getHasORSemantics())
              .map(_.getIdentifier())
              .toSet
          )
        )
    }
  }

  private def taskComputationNeeds(
      task: Task,
      model: ForSyDeSystemGraph
  ): Map[String, Map[String, Long]] = {
    var maps = mutable.Map[String, mutable.Map[String, Long]]()
    LoopingTask
      .safeCast(task)
      .ifPresent(lt => {
        java.util.stream.Stream
          .concat(lt.getInitSequencePort(model).stream(), lt.getLoopSequencePort(model).stream())
          .forEach(exec => {
            InstrumentedExecutable
              .safeCast(exec)
              .ifPresent(iexec => {
                iexec
                  .getOperationRequirements()
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
    InstrumentedExecutable
      .safeCast(task)
      .ifPresent(itask => {
        itask
          .getOperationRequirements()
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
