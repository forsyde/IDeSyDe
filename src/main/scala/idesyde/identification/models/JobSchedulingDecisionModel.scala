package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel

trait JobSchedulingDecisionModel[J, C, Pe, Pc] extends DecisionModel {

  def jobs(): Set[J]

  def channels(): Set[C]

  def processingElems(): Set[Pe]

  def communicationElems(): Set[Pc]

}
