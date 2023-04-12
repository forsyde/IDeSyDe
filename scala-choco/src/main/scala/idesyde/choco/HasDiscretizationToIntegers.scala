package idesyde.choco

import math.Ordering.Implicits.infixOrderingOps

import idesyde.utils.HasUtils

trait HasDiscretizationToIntegers extends HasUtils {

  def computeTimeMultiplierAndMemoryDividerWithResolution[T, M](
      timeValues: Vector[T],
      memoryValues: Vector[M],
      timeResolution: Int = -1,
      memoryResolution: Int = -1
  )(using
      numT: Numeric[T]
  )(using numM: Numeric[M]): (Map[T, Int], Map[M, Int]) = {
    // section for time multiplier calculation
    // if there is a 1e3 scale difference between execution and communication, we consider only execution for scaling
    val maxTime = Int.MaxValue / timeValues.size - 1
    val timeStep =
      if (timeResolution > 0) maxTime / timeResolution else maxTime / timeValues.size / 100
    val discreteTimes = timeValues
      .map(t => {
        var r = -1
        for (
          td <- timeStep to maxTime by timeStep; if r == -1;
          if numT.fromInt(td - timeStep) < t && t <= numT.fromInt(td)
        ) {
          r = td
        }
        t -> r
      })
      .toMap

    // do the same for memory numbers
    val maxMemory = Int.MaxValue / memoryValues.size - 1
    val memStep =
      if (memoryResolution > 0) maxMemory / memoryResolution
      else maxMemory / memoryValues.size / 100
    val discreteMemory = memoryValues
      .map(m => {
        var r = -1
        for (
          md <- memStep to maxMemory by memStep; if r == -1;
          if numM.fromInt(md - memStep) < m && m <= numM.fromInt(md)
        ) {
          r = md
        }
        m -> r
      })
      .toMap
    (discreteTimes, discreteMemory)
  }
}
