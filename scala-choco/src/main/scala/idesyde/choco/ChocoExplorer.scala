package idesyde.choco

import java.util.stream.Stream

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
import org.chocosolver.solver.search.loop.monitors.IMonitorContradiction
import idesyde.exploration.choco.explorers.ParetoMinimizationBrancher
import spire.math.Rational
import idesyde.common.legacy.SDFToTiledMultiCore
import idesyde.choco.ChocoExplorableOps._
import idesyde.common.legacy.PeriodicWorkloadToPartitionedSharedMultiCore
import idesyde.common.legacy.PeriodicWorkloadAndSDFServerToMultiCoreOld
import idesyde.core.Explorer
import idesyde.core.ExplorationBidding
import idesyde.core.ExplorationSolution
import org.chocosolver.solver.exception.ContradictionException
import java.util.concurrent.CopyOnWriteArraySet
import idesyde.common.legacy.CommonModule.tryCast
import org.chocosolver.solver.search.loop.monitors.SearchMonitorList
import idesyde.core.OpaqueDecisionModel

class ChocoExplorer extends Explorer:

  override def bid(
      decisionModel: DecisionModel
  ): ExplorationBidding = {
    val bidding = decisionModel.category() match {
      case "SDFToTiledMultiCore" => {
        tryCast(decisionModel, classOf[SDFToTiledMultiCore]) { sdf =>
          ExplorationBidding(
            true,
            true,
            1.0,
            (sdf.sdfApplications.minimumActorThroughputs.zipWithIndex
              .filter((th, i) => th > 0.0)
              .map((th, i) => "invThroughput(" + sdf.sdfApplications.actorsIdentifiers(i) + ")")
              .toSet + "nUsedPEs").asJava,
            java.util.Map.of("time-to-first", 100.0)
          )
        }
      }
      case "PeriodicWorkloadToPartitionedSharedMultiCore" => {
        tryCast(decisionModel, classOf[PeriodicWorkloadToPartitionedSharedMultiCore]) { workload =>
          ExplorationBidding(
            true,
            true,
            1.0,
            Set("nUsedPEs").asJava,
            java.util.Map.of("time-to-first", 100.0)
          )
        }
      }
      case "PeriodicWorkloadAndSDFServerToMultiCoreOld" => {
        tryCast(decisionModel, classOf[PeriodicWorkloadAndSDFServerToMultiCoreOld]) {
          workloadAndSDF =>
            ExplorationBidding(
              true,
              true,
              1.0,
              (workloadAndSDF.tasksAndSDFs.sdfApplications.minimumActorThroughputs.zipWithIndex
                .filter((th, i) => th > 0.0)
                .map((th, i) =>
                  "invThroughput(" + workloadAndSDF.tasksAndSDFs.sdfApplications
                    .actorsIdentifiers(i) + ")"
                )
                .toSet + "nUsedPEs").asJava,
              java.util.Map.of("time-to-first", 100.0)
            )
        }
      }
      case _ => None
    }
    bidding.getOrElse(ExplorationBidding(false, false, 0.0, Set().asJava, java.util.Map.of()))
  }

  // override def availableCriterias(decisionModel: DecisionModel): Set[ExplorationCriteria] =
  //   decisionModel match {
  //     case cp: ChocoDecisionModel =>
  //       Set(
  //         ExplorationCriteria.TimeUntilFeasibility,
  //         ExplorationCriteria.TimeUntilOptimality,
  //         ExplorationCriteria.MemoryUntilFeasibility,
  //         ExplorationCriteria.MemoryUntilOptimality
  //       )
  //     case _ => Set()
  //   }

  // override def criteriaValue(
  //     decisionModel: DecisionModel,
  //     criteria: ExplorationCriteria
  // ): Double = {
  //   decisionModel match {
  //     case cp: ChocoDecisionModel => {
  //       criteria match {
  //         case ExplorationCriteria.TimeUntilFeasibility =>
  //           cp.chocoModel.getVars.size * 60
  //         case ExplorationCriteria.TimeUntilOptimality =>
  //           cp.chocoModel.getVars.size * 3600
  //         case ExplorationCriteria.MemoryUntilFeasibility =>
  //           cp.chocoModel.getVars.size * 10
  //         case ExplorationCriteria.MemoryUntilOptimality =>
  //           cp.chocoModel.getVars.size * 1000
  //         case _ => 0.0
  //       }
  //     }
  //     case _ => 0.0
  //   }
  // }

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
      previousSolutions: Set[ExplorationSolution],
      configuration: Explorer.Configuration
  )(using ChocoExplorable[T]): LazyList[ExplorationSolution] = {
    var (model, objs) = m.chocoModel(
      previousSolutions,
      configuration
    )
    var solver = model.getSolver()
    if (configuration.improvementIterations > 0L) {
      solver.limitFail(configuration.improvementIterations)
      solver.limitBacktrack(configuration.improvementIterations)
    }
    if (configuration.improvementTimeOutInSecs > 0L) {
      solver.limitTime(configuration.improvementTimeOutInSecs * 1000L)
    }

    val chocoSolution = solver.findSolution()
    if (chocoSolution != null) {
      val solution =
        m.mergeSolution(
          solver.defaultSolution().record(),
          configuration
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
                  configuration
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

  override def explore(
      decisionModel: DecisionModel,
      previousSolutions: java.util.Set[ExplorationSolution],
      configuration: Explorer.Configuration
  ): Stream[ExplorationSolution] = {
    var llist = decisionModel.category() match
      case "SDFToTiledMultiCore" =>
        tryCast(decisionModel, classOf[SDFToTiledMultiCore]) { sdf =>
          exploreChocoExplorable(
            sdf,
            previousSolutions.asScala
              .filter(sol =>
                configuration.targetObjectives
                  .stream()
                  .allMatch(s => sol.objectives().keySet().contains(s))
              )
              .toSet,
            configuration
          )(using CanSolveSDFToTiledMultiCore())
        }
      case "PeriodicWorkloadToPartitionedSharedMultiCore" =>
        tryCast(decisionModel, classOf[PeriodicWorkloadToPartitionedSharedMultiCore]) { workload =>
          exploreChocoExplorable(
            workload,
            previousSolutions.asScala
              .filter(sol =>
                configuration.targetObjectives
                  .stream()
                  .allMatch(s => sol.objectives().keySet().contains(s))
              )
              .toSet,
            configuration
          )(using CanSolveDepTasksToPartitionedMultiCore())
        }
      case "PeriodicWorkloadAndSDFServerToMultiCoreOld" =>
        tryCast(decisionModel, classOf[PeriodicWorkloadAndSDFServerToMultiCoreOld]) {
          workloadAndSDF =>
            exploreChocoExplorable(
              workloadAndSDF,
              previousSolutions.asScala
                .filter(sol =>
                  configuration.targetObjectives
                    .stream()
                    .allMatch(s => sol.objectives().keySet().contains(s))
                )
                .toSet,
              configuration
            )(using CanSolvePeriodicWorkloadAndSDFServersToMulticore())
        }
      case _ => None
    val iter = llist.map(_.iterator).getOrElse(Iterator.empty)
    // val foundObjectives = CopyOnWriteArraySet[java.util.Map[String, java.lang.Double]]()
    Stream
      .generate(() => {
        if (iter.hasNext) {
          Some(iter.next())
        } else {
          None
        }
      })
      .takeWhile(_.isDefined)
      //   .filter(_.map(sol => !foundObjectives.contains(sol.objectives())).getOrElse(false))
      //   .peek(_.map(sol => foundObjectives.add(sol.objectives())))
      .map(_.get)
  }

  override def uniqueIdentifier(): String = "ChocoExplorer"

end ChocoExplorer
