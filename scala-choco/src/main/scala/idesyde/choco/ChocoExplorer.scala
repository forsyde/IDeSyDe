package idesyde.choco

import idesyde.identification.choco.ChocoDecisionModel
import java.time.Duration
import scala.concurrent.ExecutionContext
import idesyde.core.Explorer

import scala.jdk.OptionConverters.*
import scala.jdk.CollectionConverters.*
import scala.jdk.StreamConverters.*
import org.chocosolver.solver.search.limits.SolutionCounter
import org.chocosolver.solver.Solution
import idesyde.core.DecisionModel
import scala.collection.mutable.Buffer
import org.chocosolver.solver.constraints.Constraint
import org.chocosolver.solver.variables.IntVar
import org.chocosolver.solver.search.strategy.Search
import org.chocosolver.solver.search.strategy.strategy.AbstractStrategy
import org.chocosolver.solver.variables.Variable
import org.chocosolver.solver.search.loop.monitors.IMonitorSolution
import idesyde.exploration.choco.explorers.ParetoMinimizationBrancher
import idesyde.core.ExplorationCriteria
import idesyde.utils.Logger
import spire.math.Rational
import idesyde.common.SDFToTiledMultiCore
import idesyde.choco.ChocoExplorableOps._
import idesyde.core.ExplorationCombinationDescription
import idesyde.common.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.PeriodicWorkloadAndSDFServerToMultiCore
import idesyde.core.ExplorerConfiguration

class ChocoExplorer(using logger: Logger) extends Explorer:

  override def bid(decisionModel: DecisionModel): ExplorationCombinationDescription = {
    val canExplore = decisionModel match
      case sdf: SDFToTiledMultiCore                                => true
      case workload: PeriodicWorkloadToPartitionedSharedMultiCore  => true
      case workloadAndSDF: PeriodicWorkloadAndSDFServerToMultiCore => true
      case c: ChocoDecisionModel                                   => true
      case _                                                       => false
    val objectives: Set[String] = decisionModel match {
      case sdf: SDFToTiledMultiCore =>
        sdf.sdfApplications.minimumActorThroughputs.zipWithIndex
          .filter((th, i) => th > 0.0)
          .map((th, i) => "invThroughput(" + sdf.sdfApplications.actorsIdentifiers(i) + ")")
          .toSet + "nUsedPEs"
      case workload: PeriodicWorkloadToPartitionedSharedMultiCore => Set("nUsedPEs")
      case workloadAndSDF: PeriodicWorkloadAndSDFServerToMultiCore =>
        workloadAndSDF.tasksAndSDFs.sdfApplications.minimumActorThroughputs.zipWithIndex
          .filter((th, i) => th > 0.0)
          .map((th, i) =>
            "invThroughput(" + workloadAndSDF.tasksAndSDFs.sdfApplications
              .actorsIdentifiers(i) + ")"
          )
          .toSet + "nUsedPEs"
      case _ => Set()
    }
    ExplorationCombinationDescription(
      uniqueIdentifier,
      canExplore,
      true,
      1.0,
      objectives,
      availableCriterias(decisionModel)
        .map(c => c.identifier -> criteriaValue(decisionModel, c))
        .toMap
    )
  }

  override def availableCriterias(decisionModel: DecisionModel): Set[ExplorationCriteria] =
    decisionModel match {
      case cp: ChocoDecisionModel =>
        Set(
          ExplorationCriteria.TimeUntilFeasibility,
          ExplorationCriteria.TimeUntilOptimality,
          ExplorationCriteria.MemoryUntilFeasibility,
          ExplorationCriteria.MemoryUntilOptimality
        )
      case _ => Set()
    }

  override def criteriaValue(
      decisionModel: DecisionModel,
      criteria: ExplorationCriteria
  ): Double = {
    decisionModel match {
      case cp: ChocoDecisionModel => {
        criteria match {
          case ExplorationCriteria.TimeUntilFeasibility =>
            cp.chocoModel.getVars.size * 60
          case ExplorationCriteria.TimeUntilOptimality =>
            cp.chocoModel.getVars.size * 3600
          case ExplorationCriteria.MemoryUntilFeasibility =>
            cp.chocoModel.getVars.size * 10
          case ExplorationCriteria.MemoryUntilOptimality =>
            cp.chocoModel.getVars.size * 1000
          case _ => 0.0
        }
      }
      case _ => 0.0
    }
  }

  private def getLinearizedObj(cpModel: ChocoDecisionModel): IntVar = {
    val normalizedObjs = cpModel.modelMinimizationObjectives.map(o =>
      val scale  = o.getUB() - o.getLB()
      val scaled = cpModel.chocoModel.intVar(s"scaled(${o.getName()})", 0, 100, true)
      scaled.eq(o.sub(o.getLB()).div(scale)).post()
      scaled
    )
    val scalarizedObj = cpModel.chocoModel.intVar("scalarObj", 0, 10000, true)
    cpModel.chocoModel
      .scalar(
        normalizedObjs,
        normalizedObjs.map(o => 100 / normalizedObjs.size),
        "=",
        scalarizedObj
      )
      .post()
    scalarizedObj
  }

  def exploreChocoExplorable[T <: DecisionModel](
      m: T,
      previousSolutions: Set[(T, Map[String, Double])],
      configuration: ExplorerConfiguration
  )(using ChocoExplorable[T]): LazyList[(T, Map[String, Double])] = {
    println("exploring with " + configuration.toString())
    var (model, objs) = m.chocoModel(
      previousSolutions,
      configuration.time_resolution,
      configuration.memory_resolution
    )
    var solver = model.getSolver()
    if (configuration.improvement_timeout > 0L) {
      solver.limitTime(configuration.improvement_timeout * 1000L)
    }
    if (configuration.improvement_iterations > 0L) {
      solver.limitFail(configuration.improvement_iterations)
      solver.limitBacktrack(configuration.improvement_iterations)
    }
    val oneFeasible = solver.solve()
    if (oneFeasible) {
      val solution =
        m.mergeSolution(
          solver.defaultSolution().record(),
          configuration.time_resolution,
          configuration.memory_resolution
        )
      solution #::
        ({
          val potentialDominant = exploreChocoExplorable(
            m,
            previousSolutions + solution,
            configuration
          )
          if (potentialDominant.isEmpty) {
            LazyList
              .from(0)
              // .takeWhile(i => (configuration.max_sols <= 0 || i <= configuration.max_sols - 1))
              .map(i => (solver.solve(), i))
              .takeWhile((feasible, i) => feasible)
              .map((_, i) =>
                m.mergeSolution(
                  solver.defaultSolution().record(),
                  configuration.time_resolution,
                  configuration.memory_resolution
                )
              )
          } else {
            potentialDominant
          }
          // try to push the pareto frontier more
          //  val newTimeOut = (explorationTotalTimeOutInSecs - solver.getTimeCount().toLong) * 1000L
        })
      // val objsMap              = objs.map(v => v.getName() -> v.getValue().toInt).toMap
      // val oneLvlDown           = exploreChocoExplorable(m, objectivesUpperLimits + obj)
    } else {
      LazyList.empty
    }
    // LazyList
    //   .from(0)
    //   .takeWhile(i => (maximumSolutions <= 0 || i <= maximumSolutions))
    //   .map(i => (solver.solve(), i))
    //   .takeWhile((feasible, i) => feasible)
    //   .flatMap((feasible, _) => {
    //     if (feasible && maxLvlReached) {
    //       // println("same lvl")
    //       Some(solver.defaultSolution().record())
    //     } else if (feasible && !maxLvlReached) {
    //       // println("advance lvl from " + objs.mkString(", "))
    //       prevLvlSolver = solver
    //       prevModel = model
    //       prevOjbs = objs
    //       frontier += objs.map(_.getValue().toInt)
    //       val newChocoAndObjs =
    //         m.chocoModel(timeResolution, memoryResolution, frontier.toVector)
    //       model = newChocoAndObjs._1
    //       objs = newChocoAndObjs._2
    //       solver = model.getSolver()
    //       elapsedTimeInSecs += prevLvlSolver.getTimeCount().toLong
    //       if (explorationTotalTimeOutInSecs > 0L) {
    //         solver.limitTime((explorationTotalTimeOutInSecs - elapsedTimeInSecs) * 1000L)
    //       }
    //       Some(prevLvlSolver.defaultSolution().record())
    //     } else if (!feasible && !maxLvlReached) {
    //       // println("go back a lvl")
    //       solver = prevLvlSolver
    //       model = prevModel
    //       objs = prevOjbs
    //       maxLvlReached = true
    //       None
    //     } else None
    //   })
    //   .map(paretoSolution => m.mergeSolution(paretoSolution, timeResolution, memoryResolution))
  }

  def explore(
      decisionModel: DecisionModel,
      previousSolutions: Set[ExplorationSolution],
      configuration: ExplorerConfiguration
  ): LazyList[ExplorationSolution] = {
    decisionModel match
      case sdf: SDFToTiledMultiCore =>
        exploreChocoExplorable(
          sdf,
          previousSolutions
            .filter((m, s) => m.isInstanceOf[SDFToTiledMultiCore])
            .map((m, s) => (m.asInstanceOf[SDFToTiledMultiCore], s)),
          configuration
        )(using CanSolveSDFToTiledMultiCore())
      case workload: PeriodicWorkloadToPartitionedSharedMultiCore =>
        exploreChocoExplorable(
          workload,
          previousSolutions
            .filter((m, s) => m.isInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore])
            .map((m, s) => (m.asInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore], s)),
          configuration
        )(using CanSolveDepTasksToPartitionedMultiCore())
      case workloadAndSDF: PeriodicWorkloadAndSDFServerToMultiCore =>
        exploreChocoExplorable(
          workloadAndSDF,
          previousSolutions
            .filter((m, s) => m.isInstanceOf[PeriodicWorkloadAndSDFServerToMultiCore])
            .map((m, s) => (m.asInstanceOf[PeriodicWorkloadAndSDFServerToMultiCore], s)),
          configuration
        )(using CanSolvePeriodicWorkloadAndSDFServersToMulticore())
      // case solvable: ChocoDecisionModel =>
      //   val solver          = solvable.chocoModel.getSolver
      //   val isOptimization  = solvable.modelMinimizationObjectives.size > 0
      //   val paretoMinimizer = ParetoMinimizationBrancher(solvable.modelMinimizationObjectives)
      //   // lazy val paretoMaximizer = ParetoMaximizer(
      //   //   solvable.modelMinimizationObjectives.map(o => solvable.chocoModel.intMinusView(o))
      //   // )
      //   // var lastParetoFrontValues = solvable.modelMinimizationObjectives.map(_.getUB())
      //   // var lastParetoFrontSize = 0
      //   if (isOptimization) {
      //     if (solvable.modelMinimizationObjectives.size == 1) {
      //       solvable.chocoModel.setObjective(
      //         false,
      //         solvable.modelMinimizationObjectives.head
      //       )
      //     }
      //     solver.plugMonitor(paretoMinimizer)
      //     solvable.chocoModel.post(new Constraint("paretoOptConstraint", paretoMinimizer))
      //     // val objFunc = getLinearizedObj(solvable)
      //     // solvable.chocoModel.setObjective(false, objFunc)
      //     // strategies +:= Search.bestBound(Search.minDomLBSearch(objFunc))
      //   }
      //   // solver.addStopCriterion(SolutionCounter(solvable.chocoModel, 2L))
      //   if (!solvable.strategies.isEmpty) {
      //     solver.setSearch(solvable.strategies: _*)
      //   }
      //   if (solvable.shouldLearnSignedClauses) {
      //     solver.setLearningSignedClauses
      //   }
      //   if (solvable.shouldRestartOnSolution) {
      //     solver.setNoGoodRecordingFromRestarts
      //     solver.setRestartOnSolutions
      //   }
      //   if (explorationTotalTimeOutInSecs > 0L) {
      //     logger.debug(
      //       s"setting total exploration timeout to ${explorationTotalTimeOutInSecs} seconds"
      //     )
      //     solver.limitTime(explorationTotalTimeOutInSecs * 1000L)
      //   }
      //   LazyList
      //     .continually(solver.solve())
      //     .takeWhile(feasible => feasible)
      //     .map(_ => {
      //       solver.defaultSolution()
      //     })
      //     .flatMap(paretoSolution => {
      //       solvable.rebuildFromChocoOutput(paretoSolution)
      //     })
      case _ => LazyList.empty
  }

  def uniqueIdentifier: String = "ChocoExplorer"

end ChocoExplorer
