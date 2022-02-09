package idesyde.exploration

import idesyde.identification.models.reactor.ReactorMinusAppMapAndSched
import idesyde.identification.DecisionModel
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import java.time.Duration
import java.time.temporal.Temporal
import idesyde.exploration.interfaces.Explorer

final case class OrToolsCPExplorer() extends Explorer:

  def canExplore(decisionModel: DecisionModel) = false

  def explore(decisionModel: DecisionModel)(using
      ExecutionContext
  ) =
    LazyList.empty

  def estimateTimeUntilFeasibility(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSched =>
        Duration.ofSeconds(
          m.reactorMinus.jobGraph.jobs.size * m.reactorMinus.jobGraph.channels.size
        )
      case _ => Duration.ZERO

  def estimateTimeUntilOptimality(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSched =>
        Duration.ofMinutes(
          m.reactorMinus.jobGraph.jobs.size * m.reactorMinus.jobGraph.channels.size * m.platform.coveredVertexes.size
        )
      case _ => Duration.ZERO

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSched =>
        256 * m.reactorMinus.jobGraph.jobs.size * m.reactorMinus.jobGraph.channels.size
      case _ => 0

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSched =>
        100 * estimateMemoryUntilFeasibility(decisionModel)
      case _ => 0
