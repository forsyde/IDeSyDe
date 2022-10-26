package idesyde.identification.forsyde.models

import idesyde.identification.DesignModel
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.Vertex
import forsyde.io.java.core.EdgeInfo

class ForSyDeDesignModel(val systemGraph: ForSyDeSystemGraph) extends DesignModel {

  type GraphT  = ForSyDeSystemGraph
  type VertexT = Vertex
  type EdgeT   = EdgeInfo
}
