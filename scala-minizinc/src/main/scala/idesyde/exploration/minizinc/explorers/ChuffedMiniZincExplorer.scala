package idesyde.exploration.minizinc.explorers

import idesyde.identification.minizinc.interfaces.MiniZincForSyDeDecisionModel

import scala.sys.process._
import java.time.Duration
import idesyde.exploration.minizinc.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeSystemGraph
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import idesyde.exploration.minizinc.explorers.ChuffedMiniZincExplorer
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDecisionModel

final case class ChuffedMiniZincExplorer()
    extends SimpleMiniZincCPExplorer:

  override def canExploreForSyDe(decisionModel: ForSyDeDecisionModel): Boolean =
    "minizinc --solvers".!!.contains("org.chuffed.chuffed")

  def exploreForSyDe(decisionModel: ForSyDeDecisionModel, explorationTimeOutInSecs: Long = 0L) =
    decisionModel match
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
