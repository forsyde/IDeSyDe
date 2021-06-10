package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex

final case class ReactorMinusApplication(
    val periodicReactors: Set[Vertex],
    val dateReactiveReactors: Set[Vertex],
    val signals: Map[(Vertex, Vertex), Vertex],
    val periods: Map[Vertex, Double],
    val reactorSize: Map[Vertex, Int],
    val signalSize: Map[Vertex, Int]
) extends DecisionModel {

    def coveredVertexes() = {
        for (v <- periodicReactors) yield v
        for (v <- dateReactiveReactors) yield v
        for ((_, c) <- signals) yield c
    }

    def coveredEdges() = Seq()

    override def dominates(o: DecisionModel) =
        super.dominates(o) && (o match {
            case o: ReactorMinusApplication => dominatesReactorMinus(o)
            case _ => true
        })

    def dominatesReactorMinus(o: ReactorMinusApplication): Boolean =
        reactorSize.size >= o.reactorSize.size &&
        reactorSize.exists((k, v) => v != 0 && reactorSize.getOrElse(k, 0) == 0) &&
        signalSize.size >= o.signalSize.size &&
        signalSize.exists((k, v) => v != 0 && signalSize.getOrElse(k, 0) == 0)
}
