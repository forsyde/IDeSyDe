package idesyde.identification.minizinc.interfaces

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.forsyde.ForSyDeDecisionModel

trait MiniZincForSyDeDecisionModel extends ForSyDeDecisionModel:

  def mznModel: String

  def mznInputs: Map[String, MiniZincData]

  def rebuildFromMznOutputs(
      output: Map[String, MiniZincData],
      originalModel: ForSyDeSystemGraph
  ): ForSyDeSystemGraph
