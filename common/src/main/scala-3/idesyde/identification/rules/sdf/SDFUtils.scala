package idesyde.utils

import org.apache.commons.math3.linear.FieldMatrix
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.fraction.BigFractionField

import math.Integral.Implicits.infixIntegralOps

object SDFUtils {

  def getRepetitionVector(
      topology: Seq[Seq[Int]],
      initialTokens: Seq[Int]
  )(using Integral[BigFraction]): Seq[Int] = {
    // convert the common Scala matrix in to a field for commons math
    val topologyTransposed =
      MatrixUtils
        .createFieldMatrix(topology.map(_.map(BigFraction(_)).toArray).toArray)
        .transpose
    // make an identity for the kernel algorithm
    // val identity = MatrixUtils.createFieldIdentityMatrix(
    //   BigFractionField.getInstance,
    //   topologyTransposed.getColumnDimension
    // )
    for (i <- 0 until topologyTransposed.getRowDimension - 1) {
      // do the pivoting
      val pivotAndIdx = topologyTransposed
        .getColumn(i)
        .map(_.abs)
        .zipWithIndex
        .filter(_._1 != BigFraction.ZERO)
        .maxByOption(_._1)
      // calculate the modification
      pivotAndIdx.foreach((_, idx) => {
        val prevCol = topologyTransposed.getColumn(idx)
        topologyTransposed.setColumn(idx, topologyTransposed.getColumn(i))
        topologyTransposed.setColumn(i, prevCol)
      })
      // effect on both matrices
      pivotAndIdx.forall((p, _) => {
        for (j <- i + 1 until topologyTransposed.getRowDimension) {
          val factor = topologyTransposed.getEntry(j, i) / topologyTransposed.getColumn(i, i)
          for (k <- i until topologyTransposed.getColumnDimension) {
            topologyTransposed.setEntry(
              j,
              k,
              topologyTransposed.getEntry(j, k) - topologyTransposed.getEntry(
                i,
                k
              ) * factor
            )
          }
        }
      })
    }
    // check if rank is rows - 1.
    if (
      topologyTransposed
        .getRow(topologyTransposed.getRowDimension)
        .count(_ != BigFraction.ZERO) == 2
    ) {
      // rank is correct
    } else {}
    Seq()
  }

  def getPASS(
      topology: Seq[Seq[Int]],
      initialTokens: Seq[Int]
  ): Seq[Int] = getPASS(
    topology,
    initialTokens,
    getRepetitionVector(topology, initialTokens)
  )

  def getPASS(
      topology: Seq[Seq[Int]],
      initialTokens: Seq[Int],
      repetitionVector: Seq[Int]
  ): Seq[Int] = {
    // val sdfModule = py.module("idesyde.sdf")
    Seq()
  }
}
