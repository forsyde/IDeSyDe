package idesyde.utils

import org.apache.commons.math3.fraction.BigFraction

class BigFractionIsMultipliableFractional
    extends BigFractionIsNumeric
    with MultipliableFractional[BigFraction] {
  def div(x: BigFraction, y: BigFraction): BigFraction = x.divide(y)
  def times(a: BigFraction, b: Int): BigFraction       = a.multiply(b)
  def times(a: BigFraction, b: Long): BigFraction      = a.multiply(b)
}
