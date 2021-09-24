package idesyde.exploration.interfaces

import idesyde.identification.interfaces.MiniZincDecisionModel
import idesyde.exploration.Explorer
import forsyde.io.java.core.ForSyDeModel
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scala.sys.process._
import idesyde.identification.DecisionModel

trait SimpleMiniZincCPExplorer extends Explorer:

  def canExplore(decisionModel: DecisionModel): Boolean =
    decisionModel match
      case m: MiniZincDecisionModel => "minizinc".! == 1
      case _ => false
    

  
