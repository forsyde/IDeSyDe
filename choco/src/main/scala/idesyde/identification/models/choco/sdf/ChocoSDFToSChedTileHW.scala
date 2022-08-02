package idesyde.identification.models.choco.sdf

import idesyde.identification.models.mixed.SDFToSchedTiledHW
import idesyde.identification.interfaces.ChocoCPForSyDeDecisionModel
import org.chocosolver.solver.Model
import forsyde.io.java.core.Vertex
import org.chocosolver.solver.Solution
import forsyde.io.java.core.ForSyDeSystemGraph

final case class ChocoSDFToSChedTileHW(
    val dse: SDFToSchedTiledHW
) extends ChocoCPForSyDeDecisionModel {

  def chocoModel: Model = Model()

  def rebuildFromChocoOutput(output: Solution): ForSyDeSystemGraph = ???
  
  // Members declared in idesyde.identification.DecisionModel
  def uniqueIdentifier: String = "ChocoSDFToSChedTileHW"
  
  // Members declared in idesyde.identification.ForSyDeDecisionModel
  def coveredVertexes: Iterable[Vertex] = dse.coveredVertexes

}
