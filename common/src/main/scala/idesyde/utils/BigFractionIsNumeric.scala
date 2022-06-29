package idesyde.utils

import org.apache.commons.math3.fraction.BigFraction

class BigFractionIsNumeric extends Numeric[BigFraction] {

  def fromInt(x: Int): BigFraction                       = BigFraction(x, 1)
  def minus(x: BigFraction, y: BigFraction): BigFraction = x.subtract(y)
  def negate(x: BigFraction): BigFraction                = BigFraction.ZERO.subtract(x)
  def parseString(str: String): Option[BigFraction]      = Option.empty
  def plus(x: BigFraction, y: BigFraction): BigFraction  = x.add(y)
  def times(x: BigFraction, y: BigFraction): BigFraction = x.multiply(y)
  def toDouble(x: BigFraction): Double                   = x.doubleValue
  def toFloat(x: BigFraction): Float                     = x.floatValue
  def toInt(x: BigFraction): Int                         = x.intValue
  def toLong(x: BigFraction): Long                       = x.longValue
  def compare(x: BigFraction, y: BigFraction): Int       = x.compareTo(y)
}
