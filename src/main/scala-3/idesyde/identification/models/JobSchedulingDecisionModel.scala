package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel

trait JobSchedulingDecisionModel[J, C, R] extends DecisionModel {

  def jobs(): Set[J]

  def channels(): Set[C]

  def resources(): Set[R]

}
