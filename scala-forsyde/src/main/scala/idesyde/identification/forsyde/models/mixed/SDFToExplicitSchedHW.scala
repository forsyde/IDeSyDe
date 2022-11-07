package idesyde.identification.forsyde.models.mixed

import idesyde.identification.forsyde.models.sdf.SDFApplication
import idesyde.identification.forsyde.models.platform.SchedulableNetworkedDigHW
import idesyde.identification.forsyde.ForSyDeDecisionModel
import forsyde.io.java.core.Vertex
import idesyde.identification.forsyde.ForSyDeIdentificationRule
import idesyde.identification.IdentificationResult
import idesyde.identification.DecisionModel
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.EdgeTrait

final case class SDFToExplicitSchedHW(
    val sdfApplications: SDFApplication,
    val platform: SchedulableNetworkedDigHW,
    val schedulingMatrix: Array[Array[Boolean]],
    val procMapMatrix: Array[Array[Boolean]],
    val channelMapMatrix: Array[Array[Boolean]]
) extends ForSyDeDecisionModel {

  // Members declared in idesyde.identification.forsyde.ForSyDeDecisionModel
  val coveredElements = sdfApplications.coveredElements ++ platform.coveredElements

  val coveredElementRelations =
    sdfApplications.coveredElementRelations ++ platform.coveredElementRelations

  def uniqueIdentifier: String = "SDFToExplicitSchedHW"
}

object SDFToExplicitSchedHW {

  def identifyFromForSyDe(
      model: ForSyDeSystemGraph,
      identified: scala.collection.Iterable[DecisionModel]
  ): IdentificationResult[SDFToExplicitSchedHW] = {
    var sdf  = Option.empty[SDFApplication]
    var plat = Option.empty[SchedulableNetworkedDigHW]
    identified.foreach(m => {
      m match {
        case s: SDFApplication            => sdf = Some(s)
        case p: SchedulableNetworkedDigHW => plat = Some(p)
        case _                            =>
      }
    })
    sdf
      .flatMap(s => {
        plat.map(p => {
          val schedulings = s.actors.map(a => {
            p.schedulers.map(sched => {
              model.hasConnection(a, sched, EdgeTrait.DECISION_ABSTRACTSCHEDULING)
            })
          })
          val appMappings = s.actors.map(a => {
            p.hardware.storageElems.map(m => {
              model.hasConnection(a, m, EdgeTrait.DECISION_ABSTRACTMAPPING) ||
              model.hasConnection(a, m, EdgeTrait.DECISION_ABSTRACTALLOCATION)
            })
          })
          val messageMappings = s.channels.map(a => {
            p.hardware.storageElems.map(m => {
              model.hasConnection(a, m, EdgeTrait.DECISION_ABSTRACTMAPPING) ||
              model.hasConnection(a, m, EdgeTrait.DECISION_ABSTRACTALLOCATION)
            })
          })
          IdentificationResult.fixed(
            SDFToExplicitSchedHW(s, p, schedulings, appMappings, messageMappings)
          )
        })
      })
      .getOrElse(IdentificationResult.unfixedEmpty())
  }
}
