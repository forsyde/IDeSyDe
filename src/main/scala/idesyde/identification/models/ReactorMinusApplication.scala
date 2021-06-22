package idesyde.identification.models

import idesyde.identification.interfaces.DecisionModel
import forsyde.io.java.typed.interfaces.ReactorActor
import forsyde.io.java.typed.interfaces.ReactorTimer
import forsyde.io.java.typed.interfaces.ReactorElement
import forsyde.io.java.typed.interfaces.Signal
import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.util.ArithmeticUtils

final case class ReactorMinusApplication(
    val timers: Set[ReactorTimer],
    val periodicReactors: Set[ReactorActor],
    val dateReactiveReactors: Set[ReactorActor],
    val signals: Map[(ReactorActor, ReactorActor), Signal],
    val periods: Map[ReactorActor, Fraction],
    val reactorSize: Map[ReactorActor, Int],
    val signalSize: Map[Signal, Int]
) extends DecisionModel {

  lazy val hyperPeriod: Fraction = {
    periods
      .map(_._2)
      // the LCM of a nunch of fractions n1/d1, n2/d2... is lcm(n1, n2,...)/gcd(d1, d2,...). You can check.
      .reduce((frac1, frac2) =>
        Fraction(
          ArithmeticUtils.lcm(frac1.getNumerator, frac2.getNumerator),
          ArithmeticUtils.gcd(frac1.getDenominator, frac2.getDenominator)
        )
      )
  }

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
