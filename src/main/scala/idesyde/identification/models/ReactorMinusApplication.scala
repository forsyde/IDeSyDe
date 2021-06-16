package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.core.Vertex
import forsyde.io.java.typed.prototypes.ReactorActor
import forsyde.io.java.typed.prototypes.ReactorTimer
import forsyde.io.java.typed.prototypes.ReactorElement
import forsyde.io.java.typed.prototypes.Signal
import org.apache.commons.math3.fraction.Fraction

final case class ReactorMinusApplication(
    val timers: Set[ReactorTimer],
    val periodicReactors: Set[ReactorActor],
    val dateReactiveReactors: Set[ReactorActor],
    val signals: Map[(ReactorActor, ReactorActor), Signal],
    val periods: Map[ReactorActor, Fraction],
    val reactorSize: Map[ReactorActor, Int],
    val signalSize: Map[Signal, Int]
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
      case _                          => true
    })

  def dominatesReactorMinus(o: ReactorMinusApplication): Boolean =
    reactorSize.size >= o.reactorSize.size &&
      reactorSize.exists((k, v) =>
        v != 0 && reactorSize.getOrElse(k, 0) == 0
      ) &&
      signalSize.size >= o.signalSize.size &&
      signalSize.exists((k, v) => v != 0 && signalSize.getOrElse(k, 0) == 0)
}
