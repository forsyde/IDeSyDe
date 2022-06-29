package idesyde.identification.rules.workload

import idesyde.identification.IdentificationRule
import idesyde.identification.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import idesyde.identification.ForSyDeDecisionModel
import forsyde.io.java.typed.viewers.impl.Executable

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import idesyde.identification.models.workload.ForSyDePeriodicWorkload
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.execution.Task
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.impl.DataBlock
import forsyde.io.java.typed.viewers.impl.CommunicatingExecutable
import forsyde.io.java.typed.viewers.impl.TokenizableDataBlock
import forsyde.io.java.typed.viewers.execution.Downsample
import forsyde.io.java.typed.viewers.execution.Upsample
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import java.util.stream.Collectors
import org.jgrapht.graph.AsSubgraph
import forsyde.io.java.typed.viewers.execution.CommunicatingTask
import org.jgrapht.alg.connectivity.ConnectivityInspector
import forsyde.io.java.typed.viewers.execution.LoopingTask
import idesyde.utils.MultipliableFractional

final class PeriodicWorkloadIdentificationRule(using MultipliableFractional[BigFraction])
    extends ForSyDeIdentificationRule[ForSyDePeriodicWorkload] {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[ForSyDePeriodicWorkload]) =
    var tasks: Array[Task]                        = Array.empty
    var dataBlocks: Array[DataBlock]              = Array.empty
    var periodicStimulus: Array[PeriodicStimulus] = Array.empty
    lazy val taskStimulusGraph =
      SimpleDirectedGraph[Task | PeriodicStimulus | Upsample | Downsample, DefaultEdge](
        () => tasks.head,
        () => DefaultEdge(),
        false
      )
    lazy val taskCommunicationGraph =
      SimpleDirectedGraph[CommunicatingTask | DataBlock, Long](
        () => CommunicatingTask.enforce(tasks.head),
        () => 0L,
        false
      )
    model.vertexSet.forEach(v =>
      Task
        .safeCast(v)
        .ifPresent(task =>
          tasks :+= task
          taskStimulusGraph.addVertex(task)
          CommunicatingTask.safeCast(task).ifPresent(ct => taskCommunicationGraph.addVertex(ct))
        )
      DataBlock
        .safeCast(v)
        .ifPresent(channel =>
          dataBlocks :+= channel
          taskCommunicationGraph.addVertex(channel)
        )
      PeriodicStimulus
        .safeCast(v)
        .ifPresent(stim =>
          periodicStimulus :+= stim
          taskStimulusGraph.addVertex(stim)
        )
      Upsample
        .safeCast(v)
        .ifPresent(upsample => taskStimulusGraph.addVertex(upsample))
      Downsample
        .safeCast(v)
        .ifPresent(downsample => taskStimulusGraph.addVertex(downsample))
    )
    // nothing can be done if there are no tasks
    // so we terminate early to avoid undefined analysis results
    if (tasks.isEmpty)
      (true, Option.empty)
    // now take a look which of the relevant vertexes are connected
    taskStimulusGraph.vertexSet.forEach(src =>
      taskStimulusGraph.vertexSet.forEach(dst =>
        if (model.hasConnection(src, dst)) then taskStimulusGraph.addEdge(src, dst)
      )
    )
    // do the task communication calculations
    taskCommunicationGraph.vertexSet.forEach(src =>
      taskCommunicationGraph.vertexSet.forEach(dst =>
        src match {
          case datablock: DataBlock =>
            dst match {
              case task: CommunicatingTask =>
                val dataRead = model
                  .getAllEdges(datablock.getViewedVertex, task.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getTargetPort
                      .map(inPort =>
                        task.getPortDataReadSize.getOrDefault(inPort, datablock.getMaxSizeInBits)
                      )
                      .orElse(0L)
                  )
                  .sum
                taskCommunicationGraph.addEdge(datablock, task, dataRead)

            }
        }
        src match {
          case task: CommunicatingTask =>
            dst match {
              case datablock: DataBlock =>
                val dataRead = model
                  .getAllEdges(task.getViewedVertex, datablock.getViewedVertex)
                  .stream
                  .mapToLong(e =>
                    e.getSourcePort
                      .map(outPort =>
                        task.getPortDataWrittenSize
                          .getOrDefault(outPort, datablock.getMaxSizeInBits)
                      )
                      .orElse(0L)
                  )
                  .sum
                taskCommunicationGraph.addEdge(task, datablock, dataRead)

            }
        }
      )
    )
    // check if every task has a periodic stimulus
    val connectivityInspector = ConnectivityInspector(taskStimulusGraph)
    val allTasksAreStimulated = tasks.forall(task =>
      periodicStimulus.exists(stim => connectivityInspector.pathExists(stim, task))
    )
    if (!tasks.isEmpty && allTasksAreStimulated)
      (
        true,
        Option(
          ForSyDePeriodicWorkload(
            tasks,
            periodicStimulus,
            dataBlocks,
            tasks.map(task =>
              LoopingTask
                .safeCast(task)
                .map(lt => lt.getLoopSequencePort(model).asScala.toArray)
                .orElse(Array.empty)
            ),
            taskStimulusGraph,
            taskCommunicationGraph
          )
        )
      )
    else
      (false, Option.empty)

    // // convenience
    // lazy val tasks = periodicTasks
    // // build the task-to-executable relationship
    // lazy val executables = tasks.map(_.getInitSequencePort(model).asScala.toArray)
    //  ++ tasks.map(_.getLoopSequencePort(model).asScala.toArray)
    // // build the task-to-stimulus relation ship
    // var predecessors: Array[Array[Int]] = Array.empty
    // var successors: Array[Int]          = Array.emptyIntArray
    // // build the precedence arrays
    // // it is a bit verbose due to how the comparison is done. THrough IDs it is sure-fire.
    // periodicStimulus.foreach(stimulus => {
    //   successors :+= tasks.indexWhere(t =>
    //     stimulus.getSuccessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
    //   )
    //   SimplePeriodicStimulus
    //     .safeCast(stimulus)
    //     .ifPresent(simple => {
    //       predecessors :+= Array(
    //         tasks.indexWhere(t =>
    //           simple.getPredecessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
    //         )
    //       )
    //     })
    //   MultiANDPeriodicStimulus
    //     .safeCast(stimulus)
    //     .ifPresent(andStimulus => {
    //       predecessors :+= andStimulus
    //         .getPredecessorsPort(model)
    //         .stream
    //         .mapToInt(predecessor => {
    //           tasks.indexWhere(t => predecessor.getIdentifier == t.getIdentifier)
    //         })
    //         .toArray
    //     })
    // })
    // // build the read and write arrays
    // lazy val taskChannelReads = executables.zipWithIndex
    //   .map((es, i) => {
    //     val t = tasks(i)
    //     dataBlocks.zipWithIndex
    //       .map((c, j) => {
    //         if (model.hasConnection(c, t)) then
    //           CommunicatingExecutable
    //             .safeCast(t)
    //             .map(commTask => {
    //               (c, commTask.getPortDataReadSize.getOrDefault(c.getIdentifier, 1).toInt)
    //             })
    //             .orElse((c, 1))
    //         else (c, 0)
    //       })
    //       .map((c, elems) =>
    //         TokenizableDataBlock
    //           .safeCast(c)
    //           .map(block => block.getTokenSizeInBits * elems)
    //           .orElse(c.getMaxSizeInBits)
    //           .toLong
    //       )
    //   })
    // lazy val taskChannelWrites = tasks
    //   .map(t => {
    //     dataBlocks.zipWithIndex
    //       .map((c, j) => {
    //         if (model.hasConnection(t, c)) then
    //           CommunicatingExecutable
    //             .safeCast(t)
    //             .map(commTask => {
    //               (c, commTask.getPortDataWrittenSize.getOrDefault(c.getIdentifier, 1).toInt)
    //             })
    //             .orElse((c, 1))
    //         else (c, 0)
    //       })
    //       .map((c, elems) =>
    //         TokenizableDataBlock
    //           .safeCast(c)
    //           .map(block => block.getTokenSizeInBits * elems)
    //           .orElse(c.getMaxSizeInBits)
    //           .toLong
    //       )
    //   })
    // if (periodicTasks.isEmpty)
    //   scribe.debug("No periodic workload model found.")
    //   (true, Option.empty)
    // else if (periodicTasks.exists(_.getPeriodicStimulusPort(model).isEmpty))
    //   scribe.debug("Some periodic tasks have no periodic stimulus. Skipping.")
    //   (true, Option.empty)
    // else
    //   val ForSyDePeriodicWorkload = ForSyDePeriodicWorkload(
    //     periodicTasks = periodicTasks,
    //     reactiveTasks = reactiveTasks,
    //     periodicStimulus = periodicTasks.map(_.getPeriodicStimulusPort(model).get),
    //     PeriodicStimulus = PeriodicStimulus,
    //     executables = executables,
    //     dataBlocks = dataBlocks,
    //     PeriodicStimulusSrcs = predecessors,
    //     PeriodicStimulusDst = successors,
    //     taskChannelReads = taskChannelReads,
    //     taskChannelWrites = taskChannelWrites
    //   )
    //   scribe.debug(
    //     s"Simple periodic task model found with ${periodicTasks.length} periodic tasks, " +
    //       s"${reactiveTasks.length} reactive tasks and ${dataBlocks.length} dataBlocks"
    //   )
    //   (true, Option(ForSyDePeriodicWorkload))
    (true, Option.empty)

}
