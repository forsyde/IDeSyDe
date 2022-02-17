package idesyde.identification.rules.workload

import idesyde.identification.IdentificationRule
import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import forsyde.io.java.typed.viewers.execution.Channel
import forsyde.io.java.typed.viewers.impl.Executable

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.execution.Stimulus
import idesyde.identification.models.workload.SimplePeriodicWorkload
import org.apache.commons.math3.fraction.BigFraction
import forsyde.io.java.typed.viewers.execution.ReactiveStimulus
import forsyde.io.java.typed.viewers.execution.ReactiveTask
import forsyde.io.java.typed.viewers.execution.Task

final class PeriodicTaskIdentificationRule(using Numeric[BigFraction]) extends IdentificationRule:

  def identify(
      model: ForSyDeSystemGraph,
      identified: Set[DecisionModel]
  ): (Boolean, Option[DecisionModel]) =
    var periodicTasks: Array[PeriodicTask] = Array.empty
    var reactiveTasks: Array[ReactiveTask] = Array.empty
    var channels: Array[Channel]           = Array.empty
    model.vertexSet.stream.forEach(v => {
      PeriodicTask.safeCast(v).ifPresent(task => periodicTasks = periodicTasks.appended(task))
      ReactiveTask.safeCast(v).ifPresent(task => reactiveTasks = reactiveTasks.appended(task))
      Channel.safeCast(v).ifPresent(channel => channels = channels.appended(channel))
    })
    // convenience
    val tasks = periodicTasks ++ reactiveTasks
    // build the task-to-executable relationship
    val executables = tasks.map(_.getCallSequencePort(model).asScala.toArray)
    // build the task-to-stimulus relation ship
    val stimulusOpt      = periodicTasks.map(_.getPeriodicStimulusPort(model))
    val reactiveStimulus = reactiveTasks.flatMap(_.getReactiveStimulusPort(model).asScala).toArray
    // build the precedence arrays
    // it is a bit verbose due to how the comparison is done. THrough IDs it is sure-fire.
    val (precedencesSrc, precedencesDst) = reactiveStimulus
      .map(s => {
        (
          tasks.indexWhere(t =>
            s.getPredecessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
          ),
          tasks.indexWhere(t =>
            s.getSucessorPort(model).map(_.getIdentifier == t.getIdentifier).orElse(false)
          )
        )
      })
      .unzip
    // build the read and write arrays
    val (taskChannelRead, taskChannelWrite) = tasks
      .map(t => {
        (
          channels.zipWithIndex.filter((c, j) => {
            model.hasConnection(c, t)
          }).map((c, j) => j),
          channels.zipWithIndex.filter((c, j) => {
            model.hasConnection(t, c)
          }).map((c, j) => j)
        )
      }).unzip
    if (stimulusOpt.exists(_.isEmpty))
      scribe.debug("Some tasks have no periodic stimulus. Skipping.")
      (true, Option.empty)
    else
      val stimulus = stimulusOpt.map(_.get)
      val decisionModel = SimplePeriodicWorkload(
        periodicTasks = periodicTasks,
        reactiveTasks = reactiveTasks,
        periodicStimulus = stimulus,
        reactiveStimulus = reactiveStimulus,
        executables = executables,
        channels = channels,
        reactiveStimulusSrc = precedencesSrc,
        reactiveStimulusDst = precedencesDst,
        taskChannelRead = taskChannelRead, 
        taskChannelWrite = taskChannelWrite
      )
      scribe.debug(
        s"Simple periodic task model found with ${periodicTasks.length} periodic tasks, " +
          s"${reactiveTasks.length} reactive tasks and ${channels.length} channels"
      )
      (true, Option(decisionModel))

end PeriodicTaskIdentificationRule
