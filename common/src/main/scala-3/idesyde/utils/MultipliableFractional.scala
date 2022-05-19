package idesyde.utils

import scala.math.Fractional

trait MultipliableFractional[T] extends Fractional[T] {

  def times(a: T, b: Int): T
  def times(a: T, b: Long): T

  class MultipliableFractionalOps(a: T) {
    def *(b: Int): T  = times(a, b)
    def *(b: Long): T = times(a, b)
    def +(b: T)       = plus(a, b)
    def -(b: t)       = minus(a, b)
  }

}

object MultipliableFractionalImplicits {
  implicit def infixMultipliableFractionalOps[X](a: X)(using
      MultipliableFractional[X]
  ): MultipliableFractional[X]#MultipliableFractionalOps =
    new MultipliableFractional[X]#MultipliableFractionalOps(a)

}
