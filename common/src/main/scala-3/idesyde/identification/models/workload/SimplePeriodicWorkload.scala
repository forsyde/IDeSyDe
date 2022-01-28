package idesyde.identification.models.workload

import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.viewers.execution.PeriodicTask
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.util.{ArithmeticUtils, MathUtils}
import forsyde.io.java.typed.viewers.execution.ExtendedPrecedenceConstraint


import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*

/**
 * Simplest periodic task set concerned in the literature.
 * The periods, offsets and relative deadlines are all fixed at a task level.
 * The only additional complexity are precedence are possible precedence constraints
 * between instances of each task.
 *
 * The instances start count at 1! Not at 0. This is to agree with usual mathemtical
 * analysis.
 * 
 * The event horizon for analysis takes into consideration offsets and comes from
 *  Baruah,S.,L. Rosier,andR. Howell:1990b,
 *  `Algorithms and Complexity Concerning thePreemptive Schedulingof PeriodicReal-Time Tasks on One Processor'
 *
 * @param periodicTasks
 * @param periods
 * @param offsets
 * @param relativeDeadlines
 * @param extendedPrecedencesVertexes
 */
case class SimplePeriodicWorkload(
    val periodicTasks: Array[PeriodicTask],
    val periods: Array[BigFraction],
    val offsets: Array[BigFraction],
    val relativeDeadlines: Array[BigFraction],
    val extendedPrecedencesVertexes: Array[Array[Option[ExtendedPrecedenceConstraint]]]
) extends PeriodicWorkload[PeriodicTask, BigFraction]():

  override val coveredVertexes: Iterable[Vertex] =
    periodicTasks.map(_.getViewedVertex()) ++ 
    extendedPrecedencesVertexes.flatMap(inner => inner).filter(i => i.isDefined).map(_.get.getViewedVertex)

  val hyperPeriod: BigFraction = periods.reduce((frac1, frac2) =>
    // the LCM of a nunch of BigFractions n1/d1, n2/d2... is lcm(n1, n2,...)/gcd(d1, d2,...). You can check.
    BigFraction(
      ArithmeticUtils.lcm(frac1.getNumerator.longValue, frac2.getNumerator.longValue),
      ArithmeticUtils.gcd(frac1.getDenominator.longValue, frac2.getDenominator.longValue)
    )
  )

  val largestOffset: BigFraction = offsets.max

  val eventHorizon: BigFraction = 
    if (largestOffset.equals(BigFraction.ZERO)) then
      hyperPeriod.multiply(2).add(largestOffset)
    else
      hyperPeriod

  val tasksNumInstancesArray: Array[Int] = periodicTasks.zipWithIndex.map((task, i) => 
    eventHorizon.divide(periods(i)).getNumeratorAsInt
    ) 

  def tasksNumInstances(task: PeriodicTask): Int = tasksNumInstancesArray(periodicTasks.indexOf(task))

  def instancesReleases(task: PeriodicTask)(instance: Int): BigFraction =
    val idx = periodicTasks.indexOf(task)
    if (instance > tasksNumInstancesArray(idx))
      periods(idx).multiply(tasksNumInstancesArray(idx)).add(offsets(idx))
    else if (instance < 1)
      offsets(idx)
    else
      periods(idx).multiply(instance-1).add(offsets(idx))

  def instancesDeadlines(task: PeriodicTask)(instance: Int): BigFraction =
    val idx = periodicTasks.indexOf(task)
    if (instance > tasksNumInstancesArray(idx))
      periods(idx).multiply(tasksNumInstancesArray(idx)).add(offsets(idx)).add(relativeDeadlines(idx))
    else if (instance < 1)
      offsets(idx).add(relativeDeadlines(idx))
    else
      periods(idx).multiply(instance-1).add(offsets(idx)).add(relativeDeadlines(idx))

  def instancePreceeds(srcTask: PeriodicTask)(dstTask: PeriodicTask)(srcInstance: Int)(dstInstance: Int): Boolean =
    val srcIdx = periodicTasks.indexOf(srcTask)
    val dstIdx = periodicTasks.indexOf(dstTask)
    val constraints = extendedPrecedencesVertexes(srcIdx)(dstIdx)
    constraints.map(m => m.getPredecessorInstance.asScala.map(_.toInt)
          .zip(m.getSucessorInstance.asScala.map(_.toInt)).contains((srcInstance, dstInstance)))
          .getOrElse(false)
    

  override val uniqueIdentifier = "SimplePeriodicWorkload"

end SimplePeriodicWorkload
