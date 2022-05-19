package idesyde.utils

import scala.math.Fractional

trait MultipliableFractional[T] extends Fractional[T] {

  def times(a: T, b: Int): T
  def times(a: T, b: Long): T

}

object MultipliableFractional {

  class MultipliableFractionalOps[T](a: T)(using frac: MultipliableFractional[T]) {
    def *(b: Int): T  = frac.times(a, b)
    def *(b: Long): T = frac.times(a, b)
    def +(b: T)       = frac.plus(a, b)
    def -(b: T)       = frac.minus(a, b)
  }

  implicit def infixMultipliableFractionalOps[T](a: T)(using
      MultipliableFractional[T]
  ): MultipliableFractionalOps[T] =
    new MultipliableFractionalOps(a)
}
