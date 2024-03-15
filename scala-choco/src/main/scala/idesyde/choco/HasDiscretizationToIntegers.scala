package idesyde.choco

import math.Ordering.Implicits.infixOrderingOps
import math.Numeric.Implicits.infixNumericOps

trait HasDiscretizationToIntegers extends HasUtils {

  def discretized[T](resolution: Int, maxT: T)(t: T)(using
      numT: Numeric[T]
  )(using fracT: Fractional[T]): Int = {
    val step = fracT.div(maxT, numT.fromInt(resolution))
    // var r    = 0
    // while (numT.fromInt(r) * step < t) {
    //   r += 1
    //   println("asd " + t + " - " + r)
    // }
    fracT.div(t, step).toDouble.ceil.toInt
  }

  def undiscretized[T](resolution: Int, maxT: T)(td: Int)(using
      numT: Numeric[T]
  )(using fracT: Fractional[T]): T = {
    val step = fracT.div(maxT, numT.fromInt(resolution))
    numT.times(numT.fromInt(td), step)
  }
}

object HasDiscretizationToIntegers {

  val ceilingLongFractional = new Fractional[Long] {

    override def div(x: Long, y: Long): Long = {
      val v = Math.floorDiv(x, y)
      if (Math.floorMod(x, y) == 0) v
      else v + 1L
    }

    override def minus(x: Long, y: Long): Long = x - y

    override def compare(x: Long, y: Long): Int = x.compare(y)

    override def plus(x: Long, y: Long): Long = x + y

    override def fromInt(x: Int): Long = x.asInstanceOf[Long]

    override def toDouble(x: Long): Double = x.toDouble

    override def negate(x: Long): Long = -x

    override def toLong(x: Long): Long = x

    override def toFloat(x: Long): Float = x.toFloat

    override def parseString(str: String): Option[Long] = None

    override def times(x: Long, y: Long): Long = x * y

    override def toInt(x: Long): Int = x.toInt

  }
}
