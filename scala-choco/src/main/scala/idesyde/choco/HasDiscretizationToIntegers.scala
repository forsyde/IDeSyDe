package idesyde.choco

import math.Ordering.Implicits.infixOrderingOps

import idesyde.utils.HasUtils

trait HasDiscretizationToIntegers extends HasUtils {

  def computeTimeMultiplierAndMemoryDivider[T, M](
      timeValues: Vector[T],
      memoryValues: Vector[M]
  )(using numT: Numeric[T])(using numM: Numeric[M]): (T, M) = {
    // section for time multiplier calculation
    // if there is a 1e3 scale difference between execution and communication, we consider only execution for scaling
    var timeMultiplier = numT.one
    val t10            = numT.fromInt(10)
    val maxValT        = numT.fromInt(Int.MaxValue / 100 - 1)
    var sumT           = numT.zero
    wfor(0, _ < timeValues.size, _ + 1) { i =>
      sumT = numT.plus(sumT, timeValues(i))
    }
    while (
      // ensure that the numbers magnitudes still stay sane
      Math.log10(numT.toDouble(numT.times(sumT, timeMultiplier))) <= -3.0
      && sumT < maxValT
    ) {
      timeMultiplier = numT.times(timeMultiplier, t10)
    }

    // do the same for memory numbers
    var memoryDivider = numM.one
    val m10           = numM.fromInt(10)
    val ub            = numM.fromInt(10000)
    while (
      memoryValues
        .forall(m => m <= numM.times(ub, memoryDivider)) && memoryDivider < numM
        .fromInt(Int.MaxValue / 10)
    ) {
      memoryDivider = numM.times(memoryDivider, m10)
    }
    (timeMultiplier, memoryDivider)
  }
}
