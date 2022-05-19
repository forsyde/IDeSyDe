package idesyde.utils

import org.apache.commons.math3.fraction.BigFraction

class BigFractionIsMultipliableFractional extends MultipliableFractional[BigFraction] {
  def times(a: BigFraction, b: Int): BigFraction  = a.multiply(b)
  def times(a: BigFraction, b: Long): BigFraction = a.multiply(b)
}
