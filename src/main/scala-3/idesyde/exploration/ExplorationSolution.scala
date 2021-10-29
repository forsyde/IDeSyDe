package idesyde.exploration

import idesyde.exploration.ExplorationSolution
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeModel


enum ExplorationSolution[+A <: ForSyDeModel]:
  case Infeasible extends ExplorationSolution
  case LocalOptimal(val solution: A) extends ExplorationSolution[A]
  case GlobalOptimal(val solution: A) extends ExplorationSolution[A]
end ExplorationSolution
