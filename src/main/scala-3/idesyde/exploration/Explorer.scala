package idesyde.exploration

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel
import scala.concurrent.Future

trait Explorer[M <: DecisionModel] {

  def canExplore(decisionModel: M): Boolean

  def explore(decisionModel: M): Future[ForSyDeModel]

  def dominates(other: Explorer[M]): Seq[Boolean]

}
