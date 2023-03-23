package idesyde.matlab.identification

import idesyde.identification.DesignModel
import upickle.default.*

/** A design model for a subset of all possible simulink models.
  *
  * This [[DesignModel]] builds provides a bridge to a subset of simulink models for discrete
  * systems.
  */
final case class SimulinkReactiveDesignModel(
    val processes: Set[String],
    val processesSizes: Map[String, Long],
    val delays: Set[String],
    val delaysSizes: Map[String, Long],
    val sources: Set[String],
    val sourcesSizes: Map[String, Long],
    val sourcesPeriods: Map[String, Double],
    val constants: Set[String],
    val sinks: Set[String],
    val sinksSizes: Map[String, Long],
    val sinksDeadlines: Map[String, Double],
    val processesOperations: Map[String, Map[String, Map[String, Long]]],
    val delaysOperations: Map[String, Map[String, Map[String, Long]]],
    val links: Set[(String, String, String, String, Long)]
) extends DesignModel
    derives ReadWriter {

  type ElementT         = String
  type ElementRelationT = (String, String, String, String)

  def elementID(elem: String): String                                  = elem
  def elementRelationID(rel: (String, String, String, String)): String = rel.toString()
  val elementRelations: collection.Set[(String, String, String, String)] =
    links.map((s, t, sp, tp, _) => (s, t, sp, tp))
  val elements: collection.Set[String] =
    (processes ++ delays ++ sources ++ constants ++ sinks).toSet
  def merge(other: idesyde.identification.DesignModel): Option[idesyde.identification.DesignModel] =
    other match {
      case SimulinkReactiveDesignModel(
            oprocesses,
            oprocessesSizes,
            odelays,
            odelaysSizes,
            osources,
            osourcesSizes,
            osourcesPeriods,
            oconstants,
            osinks,
            osinksSizes,
            osinksDeadlines,
            oprocessesOperations,
            odelaysOperations,
            olinks
          ) =>
        Some(
          SimulinkReactiveDesignModel(
            processes ++ oprocesses,
            processesSizes ++ oprocessesSizes,
            delays ++ odelays,
            delaysSizes ++ odelaysSizes,
            sources ++ osources,
            sourcesSizes ++ osourcesSizes,
            sourcesPeriods ++ osourcesPeriods,
            constants ++ oconstants,
            sinks ++ osinks,
            sinksSizes ++ osinksSizes,
            sinksDeadlines ++ osinksDeadlines,
            processesOperations ++ oprocessesOperations,
            delaysOperations ++ odelaysOperations,
            links ++ olinks
          )
        )
      case _ => None
    }

}
