package idesyde.identification.models.workload

import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.{ArithmeticUtils, MathUtils}
import forsyde.io.java.typed.viewers.execution.ExtendedPrecedenceConstraint

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import forsyde.io.java.typed.viewers.execution.PrecedenceConstraint
import forsyde.io.java.typed.viewers.execution.Channel
import forsyde.io.java.typed.viewers.impl.Executable
import forsyde.io.java.typed.viewers.impl.InstrumentedExecutable
import forsyde.io.java.typed.viewers.execution.Stimulus
import forsyde.io.java.typed.viewers.execution.PeriodicStimulus
import forsyde.io.java.typed.viewers.execution.ReactiveTask
import forsyde.io.java.typed.viewers.execution.ReactiveStimulus
import forsyde.io.java.typed.viewers.execution.Task
import org.jgrapht.graph.builder.GraphBuilder
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.DefaultEdge
import org.jgrapht.opt.graph.sparse.SparseIntDirectedGraph
import org.jgrapht.alg.util.Pair
import org.jgrapht.opt.graph.sparse.IncomingEdgesSupport
import org.jgrapht.traverse.BreadthFirstIterator
import forsyde.io.java.typed.viewers.execution.DownsampleReactiveStimulus
import forsyde.io.java.typed.viewers.execution.ConstrainedTask
import forsyde.io.java.typed.viewers.execution.UpsampleReactiveStimulus

/** Simplest periodic task set concerned in the literature. The periods, offsets and relative
  * deadlines are all fixed at a task level. The only additional complexity are precedence are
  * possible precedence constraints between instances of each task.
  *
  * The event horizon for analysis takes into consideration offsets and comes from Baruah,S.,L.
  * Rosier,andR. Howell:1990b, `Algorithms and Complexity Concerning thePreemptive Schedulingof
  * PeriodicReal-Time Tasks on One Processor'
  *
  * @param periodicTasks
  *   @param periods
  * @param offsets
  *   @param relativeDeadlines
  * @param precendences
  */
case class SimplePeriodicWorkload(
    val periodicTasks: Array[PeriodicTask],
    val reactiveTasks: Array[ReactiveTask],
    val periodicStimulus: Array[PeriodicStimulus],
    val reactiveStimulus: Array[ReactiveStimulus],
    val executables: Array[Array[Executable]],
    val channels: Array[Channel],
    val reactiveStimulusSrc: Array[Int],
    val reactiveStimulusDst: Array[Int]
)(using Numeric[BigFraction]):
  //extends PeriodicWorkload[Task, BigFraction]():

  override val coveredVertexes: Iterable[Vertex] =
    periodicTasks.map(_.getViewedVertex()) ++
      periodicStimulus.map(_.getViewedVertex) ++
      executables.flatten.map(_.getViewedVertex) ++
      channels.map(_.getViewedVertex) ++
      reactiveStimulus.map(_.getViewedVertex)

  val tasks = periodicTasks ++ reactiveTasks

  val hyperPeriod: BigFraction = periodicStimulus
    .map(s => BigFraction(s.getPeriodNumerator, s.getPeriodDenominator))
    .reduce((frac1, frac2) =>
      // the LCM of a nunch of BigFractions n1/d1, n2/d2... is lcm(n1, n2,...)/gcd(d1, d2,...). You can check.
      BigFraction(
        ArithmeticUtils.lcm(frac1.getNumerator.longValue, frac2.getNumerator.longValue),
        ArithmeticUtils.gcd(frac1.getDenominator.longValue, frac2.getDenominator.longValue)
      )
    )

  // build the graph of reactions to enable periodic reductions
  val reactiveGraph = SparseIntDirectedGraph(
    tasks.length,
    reactiveStimulus.zipWithIndex
      .map((r, i) =>
        Pair(
          reactiveStimulusSrc(i).asInstanceOf[Integer],
          reactiveStimulusDst(i).asInstanceOf[Integer]
        )
      )
      .toList
      .asJava,
    IncomingEdgesSupport.LAZY_INCOMING_EDGES
  )

  // do the computation by traversing the graph
  val (periods, offsets) = {
    var periodsMut = tasks.map(_ => BigFraction(Double.PositiveInfinity))
    var offsetsMut = tasks.map(_ => BigFraction(Double.PositiveInfinity))
    val iter = BreadthFirstIterator(
      reactiveGraph,
      periodicTasks.map(tasks.indexOf(_).asInstanceOf[Integer]).toList.asJava
    )
    while (iter.hasNext) {
      val idxTask = iter.next
      val curTask = tasks(idxTask)
      curTask match {
        case perTask: PeriodicTask =>
          val stimulus = periodicStimulus(periodicTasks.indexOf(perTask))
          periodsMut(idxTask) =
            BigFraction(stimulus.getPeriodNumerator, stimulus.getPeriodDenominator)
          offsetsMut(idxTask) =
            BigFraction(stimulus.getOffsetNumerator, stimulus.getOffsetDenominator)
        case reactiveTask: ReactiveTask =>
          val stimulus = reactiveStimulus(reactiveStimulusDst.indexOf(idxTask))
          if (iter.getParent(idxTask) != null) {
            val idxParent = iter.getParent(idxTask)
            // change the candidate period depending on the type of stimulus
            val candidatePeriod = DownsampleReactiveStimulus.safeCast(stimulus)
              .map(downsample => periodsMut(idxParent).multiply(downsample.getRepetitivePredecessorSkips))
              .or(() => UpsampleReactiveStimulus.safeCast(stimulus).map(upsample => periodsMut(idxParent).divide(upsample.getRepetitivePredecessorHolds)))
              .orElse(periodsMut(idxParent))
            // same for offset
            // val candidateOffset = DownsampleReactiveStimulus.safeCast(stimulus)
            //   .map(downsample => offsetsMut(idxParent).add(periodsMut(idxParent).add(downsample.getInitialPredecessorSkips)))
            //   .or(UpsampleReactiveStimulus.safeCast(stimulus).map(upsample => offsetsMut(idxParent).add(periodsMut(idxParent).add(upsample.getInitialPredecessorSkips)))
            //   .orElse(offsetsMut(idxParent))
            if (candidatePeriod.compareTo(periodsMut(idxTask)) > 0) then
              periodsMut(idxTask) = candidatePeriod
            if (offsetsMut(idxParent).compareTo(offsetsMut(idxTask)) < 0) then
              offsetsMut(idxTask) = offsetsMut(idxParent)
          }
      }
    }
    (periodsMut, offsetsMut)
  }

  //val periods = periodicStimulus.map(s => BigFraction(s.getPeriodNumerator, s.getPeriodDenominator))
  //val offsets = periodicStimulus.map(s => BigFraction(s.getOffsetNumerator, s.getOffsetDenominator))
  val relativeDeadlines =
    periodicTasks.zipWithIndex.map((task, i) =>
      ConstrainedTask
        .safeCast(task)
        .map(conTask =>
          BigFraction(conTask.getRelativeDeadlineNumerator, conTask.getRelativeDeadlineDenominator)
        )
        .orElse(periods(i))
    )

  val largestOffset: BigFraction = offsets.max

  val eventHorizon: BigFraction =
    if (largestOffset.equals(BigFraction.ZERO)) then hyperPeriod.multiply(2).add(largestOffset)
    else hyperPeriod

  val tasksNumInstancesArray: Array[Int] =
    periodicTasks.zipWithIndex.map((task, i) => eventHorizon.divide(periods(i)).getNumeratorAsInt)

  /*
  def tasksNumInstances(task: Task): Int = tasksNumInstancesArray(
    periodicTasks.indexOf(task)
  )

  def instancesReleases(task: Task)(instance: Int): BigFraction =
    val idx = periodicTasks.indexOf(task)
    if (instance >= tasksNumInstancesArray(idx))
      periods(idx).multiply(tasksNumInstancesArray(idx)).add(offsets(idx))
    else if (instance < 0)
      offsets(idx)
    else
      periods(idx).multiply(instance).add(offsets(idx))

  def instancesDeadlines(task: Task)(instance: Int): BigFraction =
    val idx = periodicTasks.indexOf(task)
    if (instance >= tasksNumInstancesArray(idx))
      periods(idx)
        .multiply(tasksNumInstancesArray(idx))
        .add(offsets(idx))
        .add(relativeDeadlines(idx))
    else if (instance < 0)
      offsets(idx).add(relativeDeadlines(idx))
    else
      periods(idx).multiply(instance).add(offsets(idx)).add(relativeDeadlines(idx))

  def instancePreceeds(srcTask: Task)(dstTask: Task)(srcInstance: Int)(
      dstInstance: Int
  ): Boolean =
    val srcIdx      = periodicTasks.indexOf(srcTask)
    val dstIdx      = periodicTasks.indexOf(dstTask)
    val constraints = precendences(srcIdx)(dstIdx)
    constraints
      .map(m => {
        ExtendedPrecedenceConstraint
          .safeCast(m)
          .map(extendedPred =>
            extendedPred.getPredecessorInstance.asScala
              .map(_.toInt)
              .zip(extendedPred.getSucessorInstance.asScala.map(_.toInt))
              .contains((srcInstance, dstInstance))
          )
          .orElse(true) // true because it is precedence already
      })
      .getOrElse(false)

  def taskSize(t: PeriodicTask) =
    val tIdx = periodicTasks.indexOf(t)
    executables(tIdx).map(e => {
      e match {
        case eIns: InstrumentedExecutable => eIns.getSizeInBits.toLong
        case _ => 0L
      }
    }).sum
   */

  lazy val taskSizes = tasks.zipWithIndex.map((task, i) => {
    executables(i)
      .map(e => {
        e match {
          case eIns: InstrumentedExecutable => eIns.getSizeInBits.toLong
          case _                            => 0L
        }
      })
      .sum
  })

  def channelSize(c: Channel) = c.getElemSizeInBits.toLong

  lazy val channelSizes         = channels.map(channelSize(_))
  override val uniqueIdentifier = "SimplePeriodicWorkload"

end SimplePeriodicWorkload
