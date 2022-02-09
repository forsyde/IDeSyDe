package idesyde.identification.models.sdf

import forsyde.io.java.core.Vertex
import idesyde.identification.DecisionModel
import forsyde.io.java.typed.viewers.moc.sdf.SDFComb
import forsyde.io.java.typed.viewers.moc.sdf.SDFChannel
import forsyde.io.java.typed.viewers.moc.sdf.SDFDelay
import forsyde.io.java.typed.viewers.moc.sdf.SDFElem
import org.apache.commons.math3.linear.Array2DRowFieldMatrix
import org.apache.commons.math3.fraction.FractionField
import org.apache.commons.math3.fraction.Fraction
import org.apache.commons.math3.linear.Array2DRowRealMatrix
import org.apache.commons.math3.linear.SingularValueDecomposition

final case class SDFApplication(
    val actors: Array[SDFComb],
    val delays: Array[SDFDelay],
    val channels: Array[SDFChannel],
    val channelsLumping: Array[Array[SDFElem]],
    val topology: Array[Array[Int]],
    val preCalculatedRepetitionVector: Array[Int] = Array.emptyIntArray
) extends DecisionModel {

  // override def dominates(o: DecisionModel) = {
  //   val extra: Boolean = o match {
  //     case o: SDFApplication => dominatesSdf(o)
  //     case _                 => true
  //   }
  //   super.dominates(o) && extra
  // }

  // def dominatesSdf(other: SDFApplication) = repetitionVector.size >= other.repetitionVector.size
  val coveredVertexes =
    actors.map(_.getViewedVertex) ++
      delays.map(_.getViewedVertex) ++
      channels.map(_.getViewedVertex)

  lazy val initialTokens: Array[Int] =
    channelsLumping.map(lump =>
      lump
        .map(o => {
          o match {
            case delay: SDFDelay => delay.getDelayedTokens.toInt
            case _               => 0
          }
        })
        .max
    )

  lazy val repetitionVector: Array[Int] =
    if (preCalculatedRepetitionVector.length > 0) preCalculatedRepetitionVector
    else ???

  override val uniqueIdentifier = "SDFApplication"

}
