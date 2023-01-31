package idesyde.identification.common.models.mixed

import idesyde.identification.common.StandardDecisionModel
import spire.math.Rational

final case class SDFandPeriodicTask(val sdf: SDFApplication,
                              val sdfperiod: Vector[Rational],
                              val totalperiod: Vector[Rational],
                              val processes: Vector[String],
                              val periods: Vector[Rational],
                              val offsets: Vector[Rational],
                              val relativeDeadlines: Vector[Rational],
                              val processSizes: Vector[Long],
                              val processComputationalNeeds: Vector[Map[String, Map[String, Long]]],
                              val channels: Vector[String],
                              val channelSizes: Vector[Long],


                             )
  extends StandardDecisionModel{
  val hyperPeriod: Rational = periods.reduce((t1, t2) => t1.lcm(t2))
  val tasksNumInstances: Vector[Int] =
    periods
      .map(p => hyperPeriod / p)
      .map(_.toInt)
  val uniqueIdentifier: String = "SDFandPeriodicTask"
}
