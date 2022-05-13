package idesyde.exploration

import idesyde.identification.models.reactor.ReactorMinusAppMapAndSched
import idesyde.identification.ForSyDeDecisionModel
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import java.time.Duration
import java.time.temporal.Temporal
import idesyde.exploration.interfaces.Explorer

final case class OrToolsCPExplorer() extends Explorer:

  def canExplore(ForSyDeDecisionModel: ForSyDeDecisionModel) = false

  def explore(ForSyDeDecisionModel: ForSyDeDecisionModel)(using
      ExecutionContext
  ) =
    LazyList.empty

  def estimateTimeUntilFeasibility(ForSyDeDecisionModel: ForSyDeDecisionModel): Duration =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSched =>
        Duration.ofSeconds(
          m.reactorMinus.jobGraph.jobs.size * m.reactorMinus.jobGraph.channels.size
        )
      case _ => Duration.ZERO

  def estimateTimeUntilOptimality(ForSyDeDecisionModel: ForSyDeDecisionModel): Duration =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSched =>
        Duration.ofMinutes(
          m.reactorMinus.jobGraph.jobs.size * m.reactorMinus.jobGraph.channels.size * m.platform.coveredVertexes.size
        )
      case _ => Duration.ZERO

  def estimateMemoryUntilFeasibility(ForSyDeDecisionModel: ForSyDeDecisionModel): Long =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSched =>
        256 * m.reactorMinus.jobGraph.jobs.size * m.reactorMinus.jobGraph.channels.size
      case _ => 0

  def estimateMemoryUntilOptimality(ForSyDeDecisionModel: ForSyDeDecisionModel): Long =
    ForSyDeDecisionModel match
      case m: ReactorMinusAppMapAndSched =>
        100 * estimateMemoryUntilFeasibility(ForSyDeDecisionModel)
      case _ => 0
