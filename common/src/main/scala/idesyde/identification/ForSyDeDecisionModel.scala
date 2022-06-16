package idesyde.identification

import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.Vertex

trait ForSyDeDecisionModel extends DecisionModel {

    type VertexT = Vertex

    def dominates(other: DecisionModel): Boolean =
        other match {
            case o: ForSyDeDecisionModel => 
                o.coveredVertexes.toSet.subsetOf(coveredVertexes.toSet)
            case _ => false
        }
        
  
}
