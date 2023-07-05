package idesyde.exploration.minizinc.explorers

import scala.sys.process._
import java.time.Duration
import idesyde.exploration.minizinc.interfaces.SimpleMiniZincCPExplorer
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import idesyde.exploration.minizinc.explorers.ChuffedMiniZincExplorer
import idesyde.core.DecisionModel
import idesyde.core.ExplorationCombinationDescription

final case class ChuffedMiniZincExplorer() extends SimpleMiniZincCPExplorer {

  override def explore(
      decisionModel: DecisionModel,
      totalExplorationTimeOutInSecs: Long,
      maximumSolutions: Long,
      timeDiscretizationFactor: Long,
      memoryDiscretizationFactor: Long
  ): LazyList[DecisionModel] = LazyList.empty

  override def combination(decisionModel: DecisionModel): ExplorationCombinationDescription =
    ExplorationCombinationDescription.impossible(uniqueIdentifier, decisionModel.category)

  // override def explore(
  //     decisionModel: DecisionModel,
  //     explorationTimeOutInSecs: Long = 0L,
  //     maximumSolutions: Long = 0L
  // ): LazyList[DecisionModel] = LazyList.empty

  def uniqueIdentifier: String = "ChuffedMiniZincExplorer"

}

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
