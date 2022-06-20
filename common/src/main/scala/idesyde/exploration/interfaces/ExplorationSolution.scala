package idesyde.exploration.interfaces

enum ExplorationSolution[A]:
  case Infeasible extends ExplorationSolution
  case LocalOptimal(val solution: A) extends ExplorationSolution[A]
  case GlobalOptimal(val solution: A) extends ExplorationSolution[A]
end ExplorationSolution
