package idesyde.choco;

class ChocoJavaExplorer extends Explorer {

    @Override
    public String uniqueIdentifier() {
        return "ChocoJavaExplorer";
    }

  @Override
    public ExplorationBidding bid(Set<Explorer> explorers, DecisionModel decisionModel) {
        if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) {
            var objs = new HashSet<String>();
            objs.add("nUsedPEs");
            for (var app : aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore
                    .aperiodicAsynchronousDataflows()) {
                for (var actor : app.processes()) {
                    if (!app.processMinimumThroughput().containsKey(actor)) {
                        objs.add("invThroughput(%s)".formatted(actor));
                    }
                }
            }
            return new ExplorationBidding(uniqueIdentifier(), true, true, 1.0, objs, Map.of());
        } else if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedTiledMulticore aperiodicAsynchronousDataflowToPartitionedTiledMulticore) {
            var objs = new HashSet<String>();
            objs.add("nUsedPEs");
            for (var app : aperiodicAsynchronousDataflowToPartitionedTiledMulticore
                    .aperiodicAsynchronousDataflows()) {
                for (var actor : app.processes()) {
                    if (!app.processMinimumThroughput().containsKey(actor)) {
                        objs.add("invThroughput(%s)".formatted(actor));
                    }
                }
            }
            return new ExplorationBidding(uniqueIdentifier(), true, true, 1.0, objs, Map.of());
        }
        return Explorer.super.bid(explorers, decisionModel);
    }

    @Override
    public Stream<? extends ExplorationSolution> explore(DecisionModel decisionModel,
            Set<ExplorationSolution> previousSolutions, Configuration configuration) {
        if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore) {
            return exploreAADPMMM(aperiodicAsynchronousDataflowToPartitionedMemoryMappableMulticore, previousSolutions,
                    configuration);
        } else if (decisionModel instanceof AperiodicAsynchronousDataflowToPartitionedTiledMulticore aperiodicAsynchronousDataflowToPartitionedTiledMulticore) {
            return exploreAADPTM(aperiodicAsynchronousDataflowToPartitionedTiledMulticore, previousSolutions,
                    configuration);
        }
        return Explorer.super.explore(decisionModel, previousSolutions, configuration);
    }

  // def exploreChocoExplorable[T <: DecisionModel](
  //     m: T,
  //     previousSolutions: Set[ExplorationSolution],
  //     configuration: Explorer.Configuration
  // )(using ChocoExplorable[T]): LazyList[ExplorationSolution] = {
  //   println("exploring with " + configuration.toString())
  //   var (model, objs) = m.chocoModel(
  //     previousSolutions,
  //     configuration
  //   )
  //   var solver = model.getSolver()
  //   if (configuration.improvementTimeOutInSecs > 0L) {
  //     solver.limitTime(configuration.improvementTimeOutInSecs * 1000L)
  //   }
  //   if (configuration.improvementTimeOutInSecs > 0L) {
  //     solver.limitFail(configuration.improvementIterations)
  //     solver.limitBacktrack(configuration.improvementIterations)
  //   }
  //   val oneFeasible = solver.solve()
  //   if (oneFeasible) {
  //     val solution =
  //       m.mergeSolution(
  //         solver.defaultSolution().record(),
  //         configuration
  //       )
  //     solution #::
  //       ({
  //         val potentialDominant = exploreChocoExplorable(
  //           m,
  //           previousSolutions + solution,
  //           configuration
  //         )
  //         if (potentialDominant.isEmpty) {
  //           LazyList
  //             .from(0)
  //             // .takeWhile(i => (configuration.max_sols <= 0 || i <= configuration.max_sols - 1))
  //             .map(i => (solver.solve(), i))
  //             .takeWhile((feasible, i) => feasible)
  //             .map((_, i) =>
  //               m.mergeSolution(
  //                 solver.defaultSolution().record(),
  //                 configuration
  //               )
  //             )
  //         } else {
  //           potentialDominant
  //         }
  //         // try to push the pareto frontier more
  //         //  val newTimeOut = (explorationTotalTimeOutInSecs - solver.getTimeCount().toLong) * 1000L
  //       })
  //     // val objsMap              = objs.map(v => v.getName() -> v.getValue().toInt).toMap
  //     // val oneLvlDown           = exploreChocoExplorable(m, objectivesUpperLimits + obj)
  //   } else {
  //     LazyList.empty
  //   }
  //   // LazyList
  //   //   .from(0)
  //   //   .takeWhile(i => (maximumSolutions <= 0 || i <= maximumSolutions))
  //   //   .map(i => (solver.solve(), i))
  //   //   .takeWhile((feasible, i) => feasible)
  //   //   .flatMap((feasible, _) => {
  //   //     if (feasible && maxLvlReached) {
  //   //       // println("same lvl")
  //   //       Some(solver.defaultSolution().record())
  //   //     } else if (feasible && !maxLvlReached) {
  //   //       // println("advance lvl from " + objs.mkString(", "))
  //   //       prevLvlSolver = solver
  //   //       prevModel = model
  //   //       prevOjbs = objs
  //   //       frontier += objs.map(_.getValue().toInt)
  //   //       val newChocoAndObjs =
  //   //         m.chocoModel(timeResolution, memoryResolution, frontier.toVector)
  //   //       model = newChocoAndObjs._1
  //   //       objs = newChocoAndObjs._2
  //   //       solver = model.getSolver()
  //   //       elapsedTimeInSecs += prevLvlSolver.getTimeCount().toLong
  //   //       if (explorationTotalTimeOutInSecs > 0L) {
  //   //         solver.limitTime((explorationTotalTimeOutInSecs - elapsedTimeInSecs) * 1000L)
  //   //       }
  //   //       Some(prevLvlSolver.defaultSolution().record())
  //   //     } else if (!feasible && !maxLvlReached) {
  //   //       // println("go back a lvl")
  //   //       solver = prevLvlSolver
  //   //       model = prevModel
  //   //       objs = prevOjbs
  //   //       maxLvlReached = true
  //   //       None
  //   //     } else None
  //   //   })
  //   //   .map(paretoSolution => m.mergeSolution(paretoSolution, timeResolution, memoryResolution))
  // }

  // def explore(
  //     decisionModel: DecisionModel,
  //     previousSolutions: Set[ExplorationSolution],
  //     configuration: Explorer.Configuration
  // ): LazyList[ExplorationSolution] = {
  //   decisionModel match
  //     case sdf: SDFToTiledMultiCore =>
  //       exploreChocoExplorable(
  //         sdf,
  //         previousSolutions
  //           .filter(sol => sol.solved().isInstanceOf[SDFToTiledMultiCore])
  //           .map(sol =>
  //             ExplorationSolution(sol.objectives(), sol.solved().asInstanceOf[SDFToTiledMultiCore])
  //           ),
  //         configuration
  //       )(using CanSolveSDFToTiledMultiCore())
  //     case workload: PeriodicWorkloadToPartitionedSharedMultiCore =>
  //       exploreChocoExplorable(
  //         workload,
  //         previousSolutions
  //           .filter(sol => sol.solved().isInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore])
  //           .map(sol =>
  //             ExplorationSolution(
  //               sol.objectives(),
  //               sol.solved().asInstanceOf[PeriodicWorkloadToPartitionedSharedMultiCore]
  //             )
  //           ),
  //         configuration
  //       )(using CanSolveDepTasksToPartitionedMultiCore())
  //     case workloadAndSDF: PeriodicWorkloadAndSDFServerToMultiCore =>
  //       exploreChocoExplorable(
  //         workloadAndSDF,
  //         previousSolutions
  //           .filter(sol => sol.solved().isInstanceOf[PeriodicWorkloadAndSDFServerToMultiCore])
  //           .map(sol =>
  //             ExplorationSolution(
  //               sol.objectives(),
  //               sol.solved().asInstanceOf[PeriodicWorkloadAndSDFServerToMultiCore]
  //             )
  //           ),
  //         configuration
  //       )(using CanSolvePeriodicWorkloadAndSDFServersToMulticore())
  //     // case solvable: ChocoDecisionModel =>
  //     //   val solver          = solvable.chocoModel.getSolver
  //     //   val isOptimization  = solvable.modelMinimizationObjectives.size > 0
  //     //   val paretoMinimizer = ParetoMinimizationBrancher(solvable.modelMinimizationObjectives)
  //     //   // lazy val paretoMaximizer = ParetoMaximizer(
  //     //   //   solvable.modelMinimizationObjectives.map(o => solvable.chocoModel.intMinusView(o))
  //     //   // )
  //     //   // var lastParetoFrontValues = solvable.modelMinimizationObjectives.map(_.getUB())
  //     //   // var lastParetoFrontSize = 0
  //     //   if (isOptimization) {
  //     //     if (solvable.modelMinimizationObjectives.size == 1) {
  //     //       solvable.chocoModel.setObjective(
  //     //         false,
  //     //         solvable.modelMinimizationObjectives.head
  //     //       )
  //     //     }
  //     //     solver.plugMonitor(paretoMinimizer)
  //     //     solvable.chocoModel.post(new Constraint("paretoOptConstraint", paretoMinimizer))
  //     //     // val objFunc = getLinearizedObj(solvable)
  //     //     // solvable.chocoModel.setObjective(false, objFunc)
  //     //     // strategies +:= Search.bestBound(Search.minDomLBSearch(objFunc))
  //     //   }
  //     //   // solver.addStopCriterion(SolutionCounter(solvable.chocoModel, 2L))
  //     //   if (!solvable.strategies.isEmpty) {
  //     //     solver.setSearch(solvable.strategies: _*)
  //     //   }
  //     //   if (solvable.shouldLearnSignedClauses) {
  //     //     solver.setLearningSignedClauses
  //     //   }
  //     //   if (solvable.shouldRestartOnSolution) {
  //     //     solver.setNoGoodRecordingFromRestarts
  //     //     solver.setRestartOnSolutions
  //     //   }
  //     //   if (explorationTotalTimeOutInSecs > 0L) {
  //     //     logger.debug(
  //     //       s"setting total exploration timeout to ${explorationTotalTimeOutInSecs} seconds"
  //     //     )
  //     //     solver.limitTime(explorationTotalTimeOutInSecs * 1000L)
  //     //   }
  //     //   LazyList
  //     //     .continually(solver.solve())
  //     //     .takeWhile(feasible => feasible)
  //     //     .map(_ => {
  //     //       solver.defaultSolution()
  //     //     })
  //     //     .flatMap(paretoSolution => {
  //     //       solvable.rebuildFromChocoOutput(paretoSolution)
  //     //     })
  //     case _ => LazyList.empty
  // }


}
