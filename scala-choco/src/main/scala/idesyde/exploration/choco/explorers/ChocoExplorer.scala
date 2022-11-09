package idesyde.exploration.explorers

import idesyde.identification.forsyde.ForSyDeDecisionModel
import idesyde.identification.choco.interfaces.ChocoCPForSyDeDecisionModel
import java.time.Duration
import forsyde.io.java.core.ForSyDeSystemGraph
import scala.concurrent.ExecutionContext
import idesyde.exploration.Explorer

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import org.chocosolver.solver.search.limits.SolutionCounter
import org.chocosolver.solver.Solution
import idesyde.identification.DecisionModel
import idesyde.exploration.forsyde.interfaces.ForSyDeIOExplorer
import scala.collection.mutable.Buffer
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import idesyde.identification.choco.models.sdf.ChocoSDFToSChedTileHW
import org.chocosolver.solver.search.loop.monitors.IMonitorSolution
import idesyde.exploration.choco.explorers.ParetoMinimizationBrancher

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
    case sDFToSchedTiledHW: ChocoSDFToSChedTileHW =>
      Duration.ofSeconds(sDFToSchedTiledHW.chocoModel.getVars.size)
    case chocoForSyDeDecisionModel: ChocoCPForSyDeDecisionModel =>
      Duration.ofMinutes(chocoForSyDeDecisionModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  def estimateTimeUntilOptimality(
      forSyDeDecisionModel: DecisionModel
  ): java.time.Duration = forSyDeDecisionModel match
    case sDFToSchedTiledHW: ChocoSDFToSChedTileHW =>
      Duration.ofMinutes(sDFToSchedTiledHW.chocoModel.getVars.size)
    case chocoForSyDeDecisionModel: ChocoCPForSyDeDecisionModel =>
      Duration.ofHours(chocoForSyDeDecisionModel.chocoModel.getVars.size)
    case _ => Duration.ofMinutes(Int.MaxValue)

  private def getLinearizedObj(forSyDeDecisionModel: ChocoCPForSyDeDecisionModel): IntVar = {
    val normalizedObjs = forSyDeDecisionModel.modelMinimizationObjectives.map(o =>
      val scale  = o.getUB() - o.getLB()
      val scaled = forSyDeDecisionModel.chocoModel.intVar(s"scaled(${o.getName()})", 0, 100, true)
      scaled.eq(o.sub(o.getLB()).div(scale)).post()
      scaled
    )
    val scalarizedObj = forSyDeDecisionModel.chocoModel.intVar("scalarObj", 0, 10000, true)
    forSyDeDecisionModel.chocoModel
      .scalar(
        normalizedObjs,
        normalizedObjs.map(o => 100 / normalizedObjs.size),
        "=",
        scalarizedObj
      )
      .post()
    scalarizedObj
  }

  def exploreForSyDe(forSyDeDecisionModel: ForSyDeDecisionModel, explorationTimeOutInSecs: Long = 0L)(using
      ExecutionContext
  ): LazyList[ForSyDeSystemGraph] = forSyDeDecisionModel match
    case chocoCpModel: ChocoCPForSyDeDecisionModel =>
      val solver         = chocoCpModel.chocoModel.getSolver
      val isOptimization = chocoCpModel.modelMinimizationObjectives.size > 0
      val paretoMinimizer = ParetoMinimizationBrancher(chocoCpModel.modelMinimizationObjectives)
      // lazy val paretoMaximizer = ParetoMaximizer(
      //   chocoCpModel.modelMinimizationObjectives.map(o => chocoCpModel.chocoModel.intMinusView(o))
      // )
      // var lastParetoFrontValues = chocoCpModel.modelMinimizationObjectives.map(_.getUB())
      // var lastParetoFrontSize = 0
      if (isOptimization) {
        if (chocoCpModel.modelMinimizationObjectives.size == 1) {
          chocoCpModel.chocoModel.setObjective(
            false,
            chocoCpModel.modelMinimizationObjectives.head
          )
        }
        solver.plugMonitor(paretoMinimizer)
        chocoCpModel.chocoModel.post(new Constraint("paretoOptConstraint", paretoMinimizer))
        // val objFunc = getLinearizedObj(chocoCpModel)
        // chocoCpModel.chocoModel.setObjective(false, objFunc)
        // strategies +:= Search.bestBound(Search.minDomLBSearch(objFunc))
      }
      // solver.addStopCriterion(SolutionCounter(chocoCpModel.chocoModel, 2L))
      if (!chocoCpModel.strategies.isEmpty) {
        solver.setSearch(chocoCpModel.strategies: _*)
      }
      if (chocoCpModel.shouldLearnSignedClauses) {
        solver.setLearningSignedClauses
      } 
      if (chocoCpModel.shouldRestartOnSolution) {
        solver.setNoGoodRecordingFromRestarts
        solver.setRestartOnSolutions
      }
      if (explorationTimeOutInSecs > 0L) {
        solver.limitTime(explorationTimeOutInSecs * 1000L)
      }
      LazyList
        .continually(solver.solve())
        // .scanLeft(
        //   (true, 0, chocoCpModel.modelMinimizationObjectives.map(_.getUB()))
        // )((accum, feasible) => {
        //   var (paretoFrontChanged, lastParetoFrontSize, lastParetoFrontValues) = accum
        //   if (isOptimization) {
        //     paretoFrontChanged = false
        //     if (lastParetoFrontSize != paretoMaximizer.getParetoFront().size()) {
        //       lastParetoFrontSize = paretoMaximizer.getParetoFront().size()
        //       paretoFrontChanged = true
        //     } else {
        //       paretoMaximizer
        //         .getParetoFront()
        //         .forEach(s => {
        //           val isDominator =
        //             chocoCpModel.modelMinimizationObjectives.zipWithIndex.forall((o, i) => {
        //               s.getIntVal(o) < lastParetoFrontValues(i)
        //             })
        //           if (isDominator) {
        //             lastParetoFrontValues =
        //               chocoCpModel.modelMinimizationObjectives.map(o => s.getIntVal(o))
        //             paretoFrontChanged = true
        //           }
        //         })
        //     }
        //   }
        //   // println("the values " + (feasible && paretoFrontChanged, lastParetoFrontSize, lastParetoFrontValues.mkString(", ")))
        //   (feasible && paretoFrontChanged, lastParetoFrontSize, lastParetoFrontValues)
        // })
        // .takeWhile((shouldContinue, _, _) => shouldContinue)
        .takeWhile(feasible => feasible)
        // .filter(feasible => feasible)
        .map(_ => {
          solver.defaultSolution()
        })
        .map(paretoSolution => {
          // println("obj " + chocoCpModel.modelMinimizationObjectives.map(o => paretoSolution.getIntVal(o)).mkString(", "))
          chocoCpModel.rebuildFromChocoOutput(paretoSolution)
        })
    case _ => LazyList.empty

end ChocoExplorer

object ChocoExplorer {
  object ParetoBrancher extends IMonitorSolution {
    def onSolution(): Unit = {

    }
  }
}