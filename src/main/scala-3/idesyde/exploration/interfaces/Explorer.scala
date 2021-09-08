package idesyde.exploration.interfaces

import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeModel

trait Explorer[M <: DecisionModel] {

  def canExplore(decisionModel: M): Boolean

  def explore(decisionModel: M): ForSyDeModel

  def dominates(other: Explorer[M]): Seq[Boolean]

}
