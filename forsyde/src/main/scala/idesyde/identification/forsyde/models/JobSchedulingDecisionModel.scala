package idesyde.identification.forsyde.models

import idesyde.identification.forsyde.ForSyDeDecisionModel

trait JobSchedulingForSyDeDecisionModel[J, C, R] extends ForSyDeDecisionModel {

  def jobs(): Set[J]

  def channels(): Set[C]

  def resources(): Set[R]

}
