package idesyde.exploration.explorers

import idesyde.identification.forsyde.ForSyDeDecisionModel
import idesyde.identification.choco.interfaces.ChocoCPForSyDeDecisionModel
import java.time.Duration
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import idesyde.exploration.Explorer
import org.chocosolver.solver.objective.ParetoMaximizer

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import org.chocosolver.solver.search.limits.SolutionCounter
import org.chocosolver.solver.Solution
import idesyde.identification.DecisionModel
import idesyde.exploration.forsyde.interfaces.ForSyDeIOExplorer
import scala.collection.mutable.Buffer
import org.chocosolver.solver.constraints.Constraint

class ChocoExplorer() extends ForSyDeIOExplorer:

  def canExploreForSyDe(decisionModel: ForSyDeDecisionModel): Boolean =
    decisionModel match
      case chocoForSyDeDecisionModel: ChocoCPForSyDeDecisionModel => true
      case _                                                      => false

  def estimateMemoryUntilFeasibility(forSyDeDecisionModel: DecisionModel): Long =
    forSyDeDecisionModel match
      case chocoForSyDeDecisionModel: ChocoCPForSyDeDecisionModel =>
        chocoForSyDeDecisionModel.chocoModel.getVars.size * 10
      case _ => Long.MaxValue

  def estimateMemoryUntilOptimality(forSyDeDecisionModel: DecisionModel): Long =
    forSyDeDecisionModel match
      case chocoForSyDeDecisionModel: ChocoCPForSyDeDecisionModel =>
        chocoForSyDeDecisionModel.chocoModel.getVars.size * 1000
      case _ => Long.MaxValue

  def estimateTimeUntilFeasibility(
      forSyDeDecisionModel: DecisionModel
  ): java.time.Duration = forSyDeDecisionModel match
    case chocoForSyDeDecisionModel: ChocoCPForSyDeDecisionModel =>
      Duration.ofMinutes(chocoForSyDeDecisionModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  def estimateTimeUntilOptimality(
      forSyDeDecisionModel: DecisionModel
  ): java.time.Duration = forSyDeDecisionModel match
    case chocoForSyDeDecisionModel: ChocoCPForSyDeDecisionModel =>
      Duration.ofHours(chocoForSyDeDecisionModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  def exploreForSyDe(forSyDeDecisionModel: ForSyDeDecisionModel)(using
      ExecutionContext
  ): LazyList[ForSyDeSystemGraph] = forSyDeDecisionModel match
    case chocoCpModel: ChocoCPForSyDeDecisionModel =>
      val model                = chocoCpModel.chocoModel
      val solver               = model.getSolver
      val isOptimization       = chocoCpModel.modelObjectives.size > 0
      lazy val paretoMaximizer = ParetoMaximizer(chocoCpModel.modelObjectives)
      var lastParetoFront = Buffer[Solution]()
      var paretoRuns = 0
      if (isOptimization) {
        solver.plugMonitor(paretoMaximizer)
        // model.post(new Constraint("paretoOptConstraint", paretoMaximizer))
      }
      solver.setLearningSignedClauses
      solver.setNoGoodRecordingFromRestarts
      solver.setRestartOnSolutions
      solver.addStopCriterion(SolutionCounter(model, 10L))
      if (!chocoCpModel.strategies.isEmpty) then solver.setSearch(chocoCpModel.strategies: _*)
      LazyList
        .continually(solver.solve)
        .takeWhile(feasible =>
          // scribe.debug(s"a solution has been found with feasibility: ${feasible}")
          if (isOptimization) 
            feasible &&  paretoRuns < 3 
          else 
            feasible
        )
        // .takeWhile(feasible => )
        .filter(feasible => feasible)
        .flatMap(feasible => {
          // scribe.debug(s"pareto size: ${paretoMaximizer.getParetoFront.size}")
          if (isOptimization) {
            val curFront = paretoMaximizer.getParetoFront().asScala
            if (lastParetoFront != curFront) {
              lastParetoFront = curFront
              paretoRuns = 0
            } else {
              paretoRuns += 1
            }
            curFront.map(_.record())
          }
          else Seq(solver.defaultSolution().record())
        })
        .map(paretoSolutions => {
          chocoCpModel.rebuildFromChocoOutput(paretoSolutions)
        })
    case _ => LazyList.empty

end ChocoExplorer
