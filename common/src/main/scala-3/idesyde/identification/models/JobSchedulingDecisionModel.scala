package idesyde.identification.models

import idesyde.identification.ForSyDeDecisionModel

trait JobSchedulingForSyDeDecisionModel[J, C, R] extends ForSyDeDecisionModel {

  def jobs(): Set[J]

  def channels(): Set[C]

  def resources(): Set[R]

}
