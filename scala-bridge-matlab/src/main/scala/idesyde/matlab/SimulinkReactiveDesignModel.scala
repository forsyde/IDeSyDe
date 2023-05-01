package idesyde.matlab

import idesyde.core.DesignModel
import upickle.default.*
import idesyde.core.headers.LabelledArcWithPorts

/** A design model for a subset of all possible simulink models.
  *
  * This [[DesignModel]] builds provides a bridge to a subset of simulink models for discrete
  * systems.
  */
final case class SimulinkReactiveDesignModel(
    val processes: Set[String],
    val processes_sizes: Map[String, Long],
    val delays: Set[String],
    val delays_sizes: Map[String, Long],
    val sources: Set[String],
    val sources_sizes: Map[String, Long],
    val sources_periods: Map[String, Double],
    val constants: Set[String],
    val sinks: Set[String],
    val sinks_sizes: Map[String, Long],
    val sinks_deadlines: Map[String, Double],
    val processes_operations: Map[String, Map[String, Map[String, Long]]],
    val delays_operations: Map[String, Map[String, Map[String, Long]]],
    val links_src: Vector[String],
    val links_dst: Vector[String],
    val links_src_port: Vector[String],
    val links_dst_port: Vector[String],
    val links_size: Vector[Long]
) extends DesignModel
    derives ReadWriter {

  type ElementT         = String
  type ElementRelationT = (String, String, String, String)

  lazy val links = (for (i <- 0 until links_src.size)
    yield (links_src(i), links_dst(i), links_src_port(i), links_dst_port(i), links_size(i))).toSet

  def elementID(elem: String): String = elem
  def elementRelationID(rel: (String, String, String, String)): LabelledArcWithPorts =
    LabelledArcWithPorts(rel._1, Some(rel._2), None, rel._3, Some(rel._4))
  val elementRelations: Set[(String, String, String, String)] =
    (for (i <- 0 until links_src.size)
      yield (links_src(i), links_dst(i), links_src_port(i), links_dst_port(i))).toSet
  val elements: Set[String] =
    (processes ++ delays ++ sources ++ constants ++ sinks).toSet
  def merge(other: idesyde.core.DesignModel): Option[idesyde.core.DesignModel] =
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
            olinks_src,
            olinks_dst,
            olinks_sports,
            olinks_dports,
            olinks_sizes
          ) =>
        Some(
          SimulinkReactiveDesignModel(
            processes ++ oprocesses,
            processes_sizes ++ oprocessesSizes,
            delays ++ odelays,
            delays_sizes ++ odelaysSizes,
            sources ++ osources,
            sources_sizes ++ osourcesSizes,
            sources_periods ++ osourcesPeriods,
            constants ++ oconstants,
            sinks ++ osinks,
            sinks_sizes ++ osinksSizes,
            sinks_deadlines ++ osinksDeadlines,
            processes_operations ++ oprocessesOperations,
            delays_operations ++ odelaysOperations,
            links_src ++ olinks_src,
            links_dst ++ olinks_dst,
            links_src_port ++ olinks_sports,
            links_dst_port ++ olinks_dports,
            links_size ++ olinks_sizes
          )
        )
      case _ => None
    }

  def uniqueIdentifier: String = "SimulinkReactiveDesignModel"

}
