package idesyde.utils

import org.apache.commons.math3.linear.FieldMatrix
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.fraction.BigFractionField


object SDFUtils {

  def getRepetitionVector(
      topology: Seq[Seq[Int]],
      initialTokens: Seq[Int]
  ): Seq[Int] = {
    // convert the common Scala matrix in to a field for commons math
    val topologyTransposed =
      MatrixUtils
        .createFieldMatrix(topology.map(_.map(BigFraction(_)).toArray).toArray)
        .transpose
    // make an identity for the kernel algorithm
    val identity = MatrixUtils.createFieldIdentityMatrix(
      BigFractionField.getInstance,
      topologyTransposed.getColumnDimension
    )
    for (i <- 0 until topologyTransposed.getColumnDimension) {
      // do the pivoting
      // calculate the modification
      // effect on both matrices
    }
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
