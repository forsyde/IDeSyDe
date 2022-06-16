package idesyde.utils

import org.apache.commons.math3.fraction.BigFraction

class BigFractionIsNumeric extends Numeric[BigFraction] {

    def fromInt(x: Int): BigFraction = BigFraction(x, 1)
    def minus(x: BigFraction, y: BigFraction): BigFraction = x.subtract(y)
    def negate(x: BigFraction): BigFraction = BigFraction.ZERO.subtract(x)
    def parseString(str: String): Option[BigFraction] = Option.empty
    def plus(x: BigFraction, y: BigFraction): BigFraction = x.add(y)
    def times(x: BigFraction, y: BigFraction): BigFraction = x.multiply(y)
    def toDouble(x: BigFraction): Double = x.toDouble
    def toFloat(x: BigFraction): Float = x.toFloat
    def toInt(x: BigFraction): Int = x.toInt
    def toLong(x: BigFraction): Long = x.toLong
    def compare(x: BigFraction, y: BigFraction): Int = x.compareTo(y)
}