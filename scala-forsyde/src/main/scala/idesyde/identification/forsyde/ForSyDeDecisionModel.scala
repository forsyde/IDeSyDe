package idesyde.identification.forsyde

import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.Vertex
import idesyde.identification.DecisionModel

trait ForSyDeDecisionModel extends DecisionModel {

    def coveredVertexes: scala.collection.Iterable[Vertex]

    def dominates[DesignModel](other: DecisionModel, designModel: DesignModel): Boolean =
        other match {
            case o: ForSyDeDecisionModel => 
                o.coveredVertexes.toSet.subsetOf(coveredVertexes.toSet)
            case _ => false
        }
        
  
}
