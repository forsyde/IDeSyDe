package idesyde.identification.models.mixed

import idesyde.identification.DecisionModel
import idesyde.identification.models.workload.SimplePeriodicWorkload
import idesyde.identification.models.platform.SchedulableNetworkedDigHW
import forsyde.io.java.core.Vertex
import org.apache.commons.math3.fraction.BigFraction

final case class PeriodicTaskToSchedHW(
    val taskModel: SimplePeriodicWorkload,
    val schedHwModel: SchedulableNetworkedDigHW,
    val worstCaseExecution: Array[Array[Option[BigFraction]]],
    val worstCaseLinkTraversal: Array[Array[BigFraction]],
    val mapped: Array[Int]
) extends DecisionModel:

  val coveredVertexes: Iterable[Vertex] = taskModel.coveredVertexes ++ schedHwModel.coveredVertexes
  val uniqueIdentifier: String          = "PeriodicTaskToSchedHW"

end PeriodicTaskToSchedHW
