package idesyde.identification.models.reactor

import idesyde.identification.interfaces.MiniZincDecisionModel
import scala.io.Source
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.MiniZincData
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import org.apache.commons.math3.util.ArithmeticUtils
import idesyde.identification.DecisionModel

final case class ReactorMinusJobsMapAndSchedMzn(val sourceModel: ReactorMinusJobsMapAndSched)
    extends MiniZincDecisionModel:

  val coveredVertexes = sourceModel.coveredVertexes

  // TODO: fix here, not found in jar
  def mznModel =
    // val input     = getClass.getResourceAsStream("/minizinc/reactorminus_jobs_to_networkedHW.mzn")
    // val src       = Source.fromResource(input)
    // val mznString = src.getLines.mkString
    // src.close
    Source.fromResource("minizinc/reactorminus_jobs_to_networkedHW.mzn").mkString

  def mznInputs =
    val multiplier = sourceModel.reactorMinusJobs.jobs.map(_.trigger).map(_.getDenominatorAsLong)
        .reduce((d1, d2) => ArithmeticUtils.lcm(d1, d2))
    Map(
      "hyperPeriod" -> MiniZincData(sourceModel.reactorMinusJobs.reactorMinusApp.hyperPeriod.multiply(multiplier).longValue)
    )

  def rebuildFromMznOutputs(output: Map[String, MiniZincData], originalModel: ForSyDeModel): ForSyDeModel =
    ForSyDeModel()

  override def dominates(other: DecisionModel): Boolean =
    super.dominates(other) && (other match {
      case mzn: ReactorMinusJobsMapAndSched => sourceModel == mzn
      case _ => true
    })

end ReactorMinusJobsMapAndSchedMzn
