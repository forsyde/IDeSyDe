package idesyde.identification.forsyde.rules.workload

import idesyde.identification.IdentificationRule
import idesyde.identification.forsyde.ForSyDeIdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.typed.viewers.impl.Executable

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*

import spire.math._
import idesyde.identification.forsyde.models.workload.ForSyDePeriodicWorkload
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
import idesyde.identification.DecisionModel
import idesyde.identification.IdentificationResult

final case class PeriodicWorkloadIdentificationRule()(using scala.math.Fractional[Rational])(using
    Conversion[Int, Rational]
) {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ) =
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
    scribe.debug(s"Num of tasks found in model: ${tasks.size}")
    if (tasks.isEmpty)
      return IdentificationResult(true, Option.empty)
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
              case _ =>
            }
          case _ =>
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
              case _ =>
            }
          case _ =>
        }
      )
    )
    // check if every task has a periodic stimulus
    val connectivityInspector = ConnectivityInspector(taskStimulusGraph)
    val allTasksAreStimulated = tasks.forall(task =>
      periodicStimulus.exists(stim => connectivityInspector.pathExists(stim, task))
    )
    scribe.debug(s"Are all tasks reachable by a periodic stimulus? ${allTasksAreStimulated}")
    if (allTasksAreStimulated) then
      new IdentificationResult(
        true,
        Some(
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
    else new IdentificationResult(true, Option.empty)

}
