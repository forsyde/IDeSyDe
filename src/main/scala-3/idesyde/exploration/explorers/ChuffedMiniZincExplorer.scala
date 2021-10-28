package idesyde.exploration.explorers

import idesyde.identification.interfaces.MiniZincDecisionModel

import scala.sys.process._
import idesyde.identification.models.reactor.ReactorMinusAppMapAndSchedMzn
import java.time.Duration
import idesyde.exploration.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeModel
import java.nio.file.Files
import idesyde.identification.DecisionModel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import idesyde.exploration.explorers.ChuffedMiniZincExplorer

final case class ChuffedMiniZincExplorer() extends SimpleMiniZincCPExplorer:

  override def canExplore(decisionModel: DecisionModel): Boolean =
    super.canExplore(decisionModel) &&
      "minizinc --solvers".!!.contains("org.chuffed.chuffed")

  def estimateTimeUntilFeasibility(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        Duration.ofSeconds(
          nonMznDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznDecisionModel.reactorMinus.jobGraph.channels.size * 10
        )
      case _ => Duration.ZERO

  def estimateTimeUntilOptimality(decisionModel: DecisionModel): Duration =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        Duration.ofMinutes(
          nonMznDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznDecisionModel.reactorMinus.jobGraph.channels.size * nonMznDecisionModel.platform.coveredVertexes.size * 3
        )
      case _ => Duration.ZERO

  def estimateMemoryUntilFeasibility(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m.sourceModel
        128 * nonMznDecisionModel.reactorMinus.jobGraph.jobs.size * nonMznDecisionModel.reactorMinus.jobGraph.channels.size
      case _ => 0

  def estimateMemoryUntilOptimality(decisionModel: DecisionModel): Long =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val nonMznDecisionModel = m
        50 * estimateMemoryUntilFeasibility(decisionModel)
      case _ => 0

  def explore(decisionModel: DecisionModel)(using ExecutionContext) =
    decisionModel match
      case m: ReactorMinusAppMapAndSchedMzn =>
        val resString = explorationSolve(m, 
        "chuffed", 
        callExtraFlags = List("-f"),
        extraHeader = ChuffedMiniZincExplorer.extraHeaderReactorMinusAppMapAndSchedMzn,
        extraInstruction = ChuffedMiniZincExplorer.extraInstReactorMinusAppMapAndSchedMzn
        )
        LazyList.empty
      case _ => LazyList.empty

end ChuffedMiniZincExplorer


object ChuffedMiniZincExplorer:

  val extraHeaderReactorMinusAppMapAndSchedMzn: String = "include \"chuffed.mzn\";\n"

  val extraInstReactorMinusAppMapAndSchedMzn: String =
    """
    solve
    :: restart_luby(length(Reactions) * length(ProcessingElems))
    minimize goal;
    """

end ChuffedMiniZincExplorer