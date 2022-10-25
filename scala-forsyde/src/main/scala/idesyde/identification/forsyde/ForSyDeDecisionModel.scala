package idesyde.identification.forsyde

import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.Vertex
import idesyde.identification.DecisionModel

trait ForSyDeDecisionModel extends DecisionModel {

  type VertexT = Vertex

}
