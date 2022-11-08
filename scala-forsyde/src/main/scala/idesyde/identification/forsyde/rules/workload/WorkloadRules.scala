package idesyde.identification.forsyde.rules.workload

import scala.jdk.StreamConverters._
import scala.jdk.CollectionConverters._

import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.utils.Logger
import idesyde.identification.common.models.workload.CommunicatingExtendedDepencyPeriodicWorkload
import idesyde.identification.forsyde.ForSyDeIdentificationUtils
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.impl.DataBlock
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.execution.Upsample
import forsyde.io.java.typed.viewers.execution.Downsample
import forsyde.io.java.typed.viewers.execution.CommunicatingTask
import scala.collection.mutable.Buffer
import org.jgrapht.graph.AsSubgraph
import org.jgrapht.alg.connectivity.ConnectivityInspector
import spire.math._
import scala.collection.mutable
import org.jgrapht.traverse.TopologicalOrderIterator
import forsyde.io.java.typed.viewers.execution.Stimulatable

object WorkloadRules {

  def identPeriodicDependentWorkload(
      models: Set[DesignModel],
      identified: Set[DecisionModel]
  )(using logger: Logger): Option[CommunicatingExtendedDepencyPeriodicWorkload] = {
    ForSyDeIdentificationUtils.toForSyDe(models) { model =>
      var tasks                   = Buffer[Task]()
      var dataBlocks              = Buffer[DataBlock]()
      var periodicStimulus        = Buffer[PeriodicStimulus]()
      var upsamples               = Buffer[Upsample]()
      var downsamples             = Buffer[Downsample]()
      var communicationGraphEdges = Buffer[(String, String, Long)]()
      model.vertexSet.forEach(v =>
        Task
          .safeCast(v)
          .ifPresent(task => tasks :+= task)
        DataBlock
          .safeCast(v)
          .ifPresent(channel => dataBlocks :+= channel)
        PeriodicStimulus
          .safeCast(v)
          .ifPresent(stim => periodicStimulus :+= stim)
        Upsample
          .safeCast(v)
          .ifPresent(upsample => {
            upsamples :+= upsample
          })
        Downsample
          .safeCast(v)
          .ifPresent(downsample => {
            downsamples :+= downsample
          })
      )
      // nothing can be done if there are no tasks
      // so we terminate early to avoid undefined analysis results
      logger.debug(s"Num of tasks found in model: ${tasks.size}")
      if (tasks.isEmpty)
        return Option.empty
      // now take a look which of the relevant vertexes are connected
      // taskStimulusGraph.vertexSet.forEach(src =>
      //   taskStimulusGraph.vertexSet.forEach(dst =>
      //     if (model.hasConnection(src, dst)) then taskStimulusGraph.addEdge(src, dst)
      //   )
      // )
      // do the task communication calculations
      for (
        task      <- tasks;
        dataBlock <- dataBlocks
      ) {
        CommunicatingTask
          .safeCast(task)
          .ifPresent(commTask => {
            if (model.hasConnection(commTask, dataBlock)) {
              val dataWritten = model
                .getAllEdges(commTask.getViewedVertex, dataBlock.getViewedVertex)
                .stream
                .mapToLong(e =>
                  e.getSourcePort
                    .map(outPort =>
                      commTask.getPortDataWrittenSize
                        .getOrDefault(outPort, dataBlock.getMaxSizeInBits)
                    )
                    .orElse(0L)
                )
                .sum
              communicationGraphEdges :+= (commTask.getIdentifier(), dataBlock
                .getIdentifier(), dataWritten)
            } else if (model.hasConnection(dataBlock, commTask)) {
              val dataRead = model
                .getAllEdges(dataBlock.getViewedVertex, commTask.getViewedVertex)
                .stream
                .mapToLong(e =>
                  e.getTargetPort
                    .map(inPort =>
                      commTask.getPortDataReadSize.getOrDefault(inPort, dataBlock.getMaxSizeInBits)
                    )
                    .orElse(0L)
                )
                .sum
              communicationGraphEdges :+= (dataBlock.getIdentifier(), commTask
                .getIdentifier(), dataRead)
            }
          })
      }
      // check if every task has a periodic stimulus
      val stimulusGraph =
        AsSubgraph(
          model,
          (tasks ++ periodicStimulus ++ upsamples ++ downsamples)
            .map(_.getViewedVertex())
            .toSet
            .asJava
        )
      val connectivityInspector = ConnectivityInspector(stimulusGraph)
      val allTasksAreStimulated = tasks.forall(task =>
        periodicStimulus.exists(stim =>
          connectivityInspector.pathExists(stim.getViewedVertex(), task.getViewedVertex())
        )
      )
      logger.debug(s"Are all tasks reachable by a periodic stimulus? ${allTasksAreStimulated}")
      if (!allTasksAreStimulated) return Option.empty
      // now building all the periodic workload elements ditto
      var propagatedEvents = mutable.Map[String, Set[(Rational, Rational, Rational)]]()
      val processes        = Buffer[(String, Rational, Rational, Rational)]()
      val stimulusIter     = TopologicalOrderIterator(stimulusGraph)
      while (stimulusIter.hasNext) {
        val next = stimulusIter.next
        // gather all incomin stimulus
        val incomingEvents = stimulusGraph
          .incomingEdgesOf(next)
          .stream
          .map(e => propagatedEvents(stimulusGraph.getEdgeSource(e).getIdentifier()))
          .reduce((s1, s2) => s1 | s2)
          .orElse(Set())
        val events = Stimulatable
          .safeCast(next)
          .filter(stimulatable => stimulatable.getHasORSemantics())
          .map(stimulatable => {
            val maxP = incomingEvents.map((p, o, d) => p).max
            val minO = incomingEvents.map((p, o, d) => o).min
            val minD = incomingEvents.map((p, o, d) => d).min
            Set((maxP, minO, minD))
          })
          .orElse(incomingEvents)
        // decide what to do next based on the vertex type and its event merge semantics
        PeriodicStimulus
          .safeCast(next)
          .ifPresent(periodicStimulus => {
            propagatedEvents(next.getIdentifier()) = Set(
              (
                Rational(
                  periodicStimulus.getPeriodNumerator,
                  periodicStimulus.getPeriodDenominator
                ), // period
                Rational(
                  periodicStimulus.getOffsetNumerator,
                  periodicStimulus.getOffsetDenominator
                ), // offset
                Rational(
                  periodicStimulus.getPeriodNumerator,
                  periodicStimulus.getPeriodDenominator
                ) // rel. deadline
              )
            )
          })
        Task
          .safeCast(next)
          .ifPresent(task => {
            propagatedEvents(next.getIdentifier()) = events
            processes ++= events.map((p, o, d) => (next.getIdentifier(), p, o, d))
          })
        Upsample
          .safeCast(next)
          .ifPresent(upsample => {
            propagatedEvents(next.getIdentifier()) = events.map(e => {
              (
                e._1 / Rational(upsample.getRepetitivePredecessorHolds),
                e._2 + (e._1 / Rational(upsample.getInitialPredecessorHolds)),
                e._3 / Rational(upsample.getRepetitivePredecessorHolds)
              )
            })
          })
        Downsample
          .safeCast(next)
          .ifPresent(downsample => {
            propagatedEvents(next.getIdentifier()) = events.map(e => {
              (
                e._1 * (Rational(downsample.getRepetitivePredecessorSkips)),
                e._2 + (e._1 * (Rational(downsample.getInitialPredecessorSkips))),
                e._3 * (Rational(downsample.getRepetitivePredecessorSkips))
              )
            })
          })
      }
      // first consider task-to-task connections
      var affineControlGraphEdges = Buffer[(String, String, Int, Int, Int, Int)]()
      for (srcTask <- tasks) {
        model
          .outgoingEdgesOf(srcTask.getViewedVertex())
          .stream()
          .map(edge => model.getEdgeTarget(edge))
          .flatMap(dst => Task.safeCast(dst).stream())
          .forEach(dstTask => {
            if (dstTask.getHasORSemantics()) {
              for (
                srcEvent <- propagatedEvents(srcTask.getIdentifier());
                dstEvent <- propagatedEvents(dstTask.getIdentifier())
                if srcEvent == dstEvent
              ) {
                affineControlGraphEdges :+= (srcTask.getIdentifier(), dstTask
                  .getIdentifier(), 1, 0, 1, 0)
              }
            } else {
              for (
                srcEvent <- propagatedEvents(srcTask.getIdentifier());
                dstEvent <- propagatedEvents(dstTask.getIdentifier())
              ) {
                affineControlGraphEdges :+= (srcTask.getIdentifier(), dstTask
                  .getIdentifier(), (dstEvent._1 / srcEvent._1).ceil.toInt, 0, 1, 0)
              }
            }
          })
      }
      // now consider upsampling connections
      for (upsample <- upsamples) {
        model
          .outgoingEdgesOf(upsample.getViewedVertex())
          .stream()
          .map(edge => model.getEdgeTarget(edge))
          .flatMap(dst => Task.safeCast(dst).stream())
          .forEach(dstTask => {
            model
              .incomingEdgesOf(upsample.getViewedVertex())
              .stream()
              .map(edge => model.getEdgeSource(edge))
              .flatMap(src => Task.safeCast(src).stream())
              .forEach(srcTask => {
                if (dstTask.getHasORSemantics) {
                  for (
                    srcEvent <- propagatedEvents(srcTask.getIdentifier());
                    dstEvent <- propagatedEvents(dstTask.getIdentifier());
                    if dstEvent._1 * Rational(
                      upsample.getRepetitivePredecessorHolds
                    ) == srcEvent._1 &&
                      dstEvent._2 - (dstEvent._1 * Rational(
                        upsample.getInitialPredecessorHolds
                      )) == srcEvent._2
                  ) {
                    affineControlGraphEdges :+= (srcTask.getIdentifier(), dstTask
                      .getIdentifier(), upsample.getRepetitivePredecessorHolds.toInt, upsample.getInitialPredecessorHolds.toInt, 1, 0)
                  }
                } else {
                  for (
                    srcEvent <- propagatedEvents(srcTask.getIdentifier());
                    dstEvent <- propagatedEvents(dstTask.getIdentifier());
                    pRatio = (dstEvent._1 / srcEvent._1);
                    offset = ((dstEvent._2 - srcEvent._2) / srcEvent._1)
                  ) {
                    affineControlGraphEdges :+= (srcTask.getIdentifier(), dstTask
                      .getIdentifier(), pRatio.ceil.toInt, offset.ceil.toInt, 1, 0)
                  }
                }
              })
          })
      }
      // // now finally consider downsample connections
      // stimulusGraph.vertexSet.stream
      //   .flatMap(v => Downsample.safeCast(v).stream())
      //   .forEach(downsample => {
      //     stimulusGraph
      //       .incomingEdgesOf(downsample)
      //       .stream
      //       .flatMap(e => Task.safeCast(stimulusGraph.getEdgeSource(e)).stream())
      //       .forEach(srcTask => {
      //         stimulusGraph
      //           .outgoingEdgesOf(downsample)
      //           .stream
      //           .flatMap(e => Task.safeCast(stimulusGraph.getEdgeTarget(e)).stream())
      //           .forEach(dstTask => {
      //             if (dstTask.getHasORSemantics)
      //               for (
      //                 (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //                 (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //                 if dst._2 / Rational(downsample.getRepetitivePredecessorSkips) == src._2 &&
      //                   dst._3 + (dst._2 / Rational(
      //                     downsample.getInitialPredecessorSkips
      //                   )) == src._3
      //               )
      //                 affineRelationsGraphBuilder.addEdge(
      //                   i,
      //                   j,
      //                   (
      //                     1,
      //                     0,
      //                     downsample.getRepetitivePredecessorSkips.toInt,
      //                     downsample.getInitialPredecessorSkips.toInt
      //                   )
      //                 )
      //             else
      //               for (
      //                 (dst, i) <- createdPerTasks.zipWithIndex.filter((d, i) => d._1 == dstTask);
      //                 (src, j) <- createdPerTasks.zipWithIndex.filter((s, j) => s._1 == srcTask);
      //                 pRatio = (src._2 / dst._2).toDouble.ceil.toInt;
      //                 offset = ((dst._3 - src._3) / dst._2).toDouble.toInt
      //               ) affineRelationsGraphBuilder.addEdge(i, j, (1, 0, pRatio, offset))
      //           })
      //       })
      //   })
      // affineRelationsGraphBuilder.buildAsUnmodifiable
      Option.empty
    }
  }
}
