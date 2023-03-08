package idesyde.identification.minizinc

import idesyde.identification.common.StandardDecisionModel

trait MiniZincDecisionModel extends StandardDecisionModel:

  def mznModel: String

  def mznInputs: Map[String, MiniZincData]

  // def rebuildFromMznOutputs(
  //     output: Map[String, MiniZincData],
  //     originalModel: ForSyDeSystemGraph
  // ): ForSyDeSystemGraph
