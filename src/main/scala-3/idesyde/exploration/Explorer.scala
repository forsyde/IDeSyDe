package idesyde.exploration

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel
import scala.concurrent.Future
import scala.concurrent.ExecutionContext

trait Explorer[M <: DecisionModel] {

  def explore(decisionModel: M)(using ExecutionContext): Future[ForSyDeModel]

  def dominates(other: Explorer[M], decisionModel: M): Boolean

}
