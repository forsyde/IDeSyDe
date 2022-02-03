package idesyde.identification.choco

import idesyde.identification.interfaces.ChocoCPDecisionModel

import idesyde.identification.models.mixed.PeriodicTaskToSchedHW
import forsyde.io.java.core.ForSyDeSystemGraph

final case class PeriodicTaskToSchedHWChoco(
    val sourceDecisionModel: PeriodicTaskToSchedHW
) extends ChocoCPDecisionModel:

  val coveredVertexes = sourceDecisionModel.coveredVertexes

  def chocoModel: Model = {
    val model = Model()

    model
  }

  def rebuildFromChocoOutput(output: Model, originalModel: ForSyDeSystemGraph): ForSyDeSystemGraph =
    ForSyDeSystemGraph()

  val uniqueIdentifier = "PeriodicTaskToSchedHWChoco"

end PeriodicTaskToSchedHWChoco
