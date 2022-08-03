package idesyde.utils

import org.apache.commons.math3.linear.FieldMatrix
import org.apache.commons.math3.linear.MatrixUtils
import org.apache.commons.math3.fraction.BigFraction
import org.apache.commons.math3.fraction.BigFractionField

import math.Integral.Implicits.infixIntegralOps
import org.apache.commons.math3.linear.SingularValueDecomposition
import org.apache.commons.math3.util.ArithmeticUtils
import org.apache.commons.math3.linear.ArrayFieldVector
import org.apache.commons.math3.linear.RealMatrix

object SDFUtils {

  def getRepetitionVector(
      topologyMatrix: Array[Array[Int]],
      initialTokens: Array[Int],
      independentApplications: Int = 1
  )(using Integral[BigFraction]): Array[Int] =
    // convert the common Scala matrix in to a field for commons math
    getRepetitionVector(MatrixUtils
        .createRealMatrix(topologyMatrix.map(_.map(_.doubleValue).toArray).toArray), initialTokens, independentApplications)

  def getRepetitionVector(
      topologyMatrix: RealMatrix,
      initialTokens: Array[Int],
      independentApplications: Int
  )(using Integral[BigFraction]): Array[Int] = {
    val svd = SingularValueDecomposition(topologyMatrix)
    // scribe.debug(topologyMatrix.toString())
    // make an identity for the kernel algorithm
    // val identity = MatrixUtils.createFieldIdentityMatrix(
    //   BigFractionField.getInstance,
    //   topologyTransposed.getColumnDimension
    // )
    // for (i <- 0 until topologyTransposed.getRowDimension - 1) {
    //   // do the pivoting
    //   val pivotAndIdx = topologyTransposed
    //     .getColumn(i)
    //     .map(_.abs)
    //     .zipWithIndex
    //     .filter(_._1 != BigFraction.ZERO)
    //     .maxByOption(_._1)
    //   // calculate the modification
    //   pivotAndIdx.foreach((_, idx) => {
    //     val prevCol = topologyTransposed.getColumn(idx)
    //     topologyTransposed.setColumn(idx, topologyTransposed.getColumn(i))
    //     topologyTransposed.setColumn(i, prevCol)
    //   })
    //   // effect on both matrices
    //   pivotAndIdx.forall((p, _) => {
    //     for (j <- i + 1 until topologyTransposed.getRowDimension) {
    //       val factor = topologyTransposed.getEntry(j, i) / topologyTransposed.getColumn(i, i)
    //       for (k <- i until topologyTransposed.getColumnDimension) {
    //         topologyTransposed.setEntry(
    //           j,
    //           k,
    //           topologyTransposed.getEntry(j, k) - topologyTransposed.getEntry(
    //             i,
    //             k
    //           ) * factor
    //         )
    //       }
    //     }
    //   })
    // }
    // check if rank is rows - independentApplications.
    val svdZeroIdx = svd.getSingularValues.zipWithIndex.filter((v, _) => v.abs <= 1e-10)
    // scribe.debug(svdZeroIdx.mkString(", "))
    // scribe.debug(independentApplications.toString())
    if (svdZeroIdx.length == independentApplications) {
      // rank is correct
      // scribe.debug("VT " + svd.getVT().toString())
      // scribe.debug("U " + svd.getU().toString())
      val nullVec = svd.getVT().getRow(svdZeroIdx(0)._2) //.map(BigFraction(_))
      // scribe.debug(svd.getVT().getRow(svdZeroIdx(0)._2).mkString(", "))
      // scribe.debug(nullVec.mkString(", "))
      var roundFactor = 1
      while(nullVec.map(_ * roundFactor).exists(v => (v - v.round.toDouble).abs >= 1e-6)) {
        roundFactor += 1
      }
      val roundedNullVec = nullVec.map(_ * roundFactor).map(_.round.toInt)
      var gcd = roundedNullVec.reduce((i1, i2) => ArithmeticUtils.gcd(i1, i2))
      var integerNullVec = roundedNullVec.map(_ / gcd)
      // val gcd = nullVec.map(_.getNumeratorAsLong).reduce((i1, i2) => ArithmeticUtils.gcd(i1, i2))
      // val lcm = nullVec
      //   .map(_.getDenominatorAsLong)
      //   .reduce((i1, i2) => ArithmeticUtils.lcm(i1, i2))
      // nullVec.map(_.multiply(lcm).divide(gcd).getNumeratorAsInt())
      integerNullVec
    } else Array.emptyIntArray
  }

  def getPASS(
      topology: Array[Array[Int]]
  )(using Integral[BigFraction]): Array[Int] = getPASS(topology, Array.fill(topology(0).length)(0))

  def getPASS(
      topology: Array[Array[Int]],
      initialTokens: Array[Int]
  )(using Integral[BigFraction]): Array[Int] = getPASS(
    topology,
    initialTokens,
    getRepetitionVector(topology, initialTokens)
  )

  def getPASS(
      topology: Array[Array[Int]],
      initialTokens: Array[Int],
      repetitionVector: Array[Int]
  )(using Integral[BigFraction]): Array[Int] = {
    // val sdfModule = py.module("idesyde.sdf")
    val numActors = topology(0).length
    var buffer = MatrixUtils.createRealVector(
      repetitionVector.zipWithIndex.map((q, i) => q + initialTokens(i)).map(_.doubleValue).toArray
    )
    val topologyMatrix =
      MatrixUtils
        .createRealMatrix(topology.map(_.map(_.doubleValue).toArray).toArray)
    var firings  = repetitionVector.toArray
    var sequence = Array.emptyIntArray
    while (firings.exists(_ > 0)) {
      (0 until numActors)
        .filter(firings(_) > 0)
        .map(a => {
          (a, topologyMatrix.operate(buffer))
        })
        .find((a, b) => b.getMinValue >= 0)
        .foreach((a, b) => {
          firings(a) -= 1
          sequence :+= a
        })
    }
    sequence
  }
}
