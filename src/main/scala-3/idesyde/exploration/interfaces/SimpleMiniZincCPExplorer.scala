package idesyde.exploration.interfaces

import idesyde.identification.interfaces.MiniZincDecisionModel
import idesyde.exploration.Explorer
import forsyde.io.java.core.ForSyDeModel
import scala.concurrent.ExecutionContext
import scala.concurrent.Future

import scala.sys.process._
import idesyde.identification.DecisionModel

trait SimpleMiniZincCPExplorer[M <: MiniZincDecisionModel] extends Explorer:

  def canExplore(decisionModel: DecisionModel): Boolean =
    val res = "minizinc".!
    scribe.debug(res.toString)
    res == 0

  
