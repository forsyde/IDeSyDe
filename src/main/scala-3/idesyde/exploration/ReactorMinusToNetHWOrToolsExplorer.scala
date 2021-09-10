package idesyde.exploration

import idesyde.identification.models.reactor.ReactorMinusJobsMapAndSched
import idesyde.identification.DecisionModel
import scala.concurrent.Future
import forsyde.io.java.core.ForSyDeModel
import scala.concurrent.ExecutionContext

final case class ReactorMinusToNetHWOrToolsExplorer() extends Explorer[ReactorMinusJobsMapAndSched]:

    def dominates(other: Explorer[ReactorMinusJobsMapAndSched], decisionModel: ReactorMinusJobsMapAndSched): Boolean = true

    def explore(decisionModel: ReactorMinusJobsMapAndSched)(using ExecutionContext): Future[ForSyDeModel] =
        Future(ForSyDeModel())
