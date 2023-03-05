package idesyde.matlab.identification

import idesyde.identification.DesignModel

/** A design model for a subset of all possible simulink models.
  *
  * This [[DesignModel]] builds provides a bridge to a subset of simulink models for discrete
  * systems.
  */
final case class SimulinkReactiveDesignModel(
    val processes: Vector[String],
    val processesSizes: Vector[Long],
    val delays: Vector[String],
    val delaysSizes: Vector[Long],
    val sources: Vector[String],
    val sourcesSizes: Vector[Long],
    val sourcesPeriodsNumerator: Vector[Long],
    val sourcesPeriodsDenominator: Vector[Long],
    val constants: Vector[String],
    val sinks: Vector[String],
    val sinksSizes: Vector[Long],
    val sinksDeadlinesNumerator: Vector[Long],
    val sinksDeadlinesDenominator: Vector[Long],
    val processesOperations: Vector[Map[String, Map[String, Long]]],
    val delaysOperations: Vector[Map[String, Map[String, Long]]],
    val linksSrcs: Vector[String],
    val linksDsts: Vector[String],
    val linksDataSizes: Vector[Long]
) extends DesignModel {

  type ElementT         = String
  type ElementRelationT = (String, String)

  def elementID(elem: String): String                    = elem
  def elementRelationID(rel: (String, String)): String   = rel.toString()
  val elementRelations: collection.Set[(String, String)] = linksSrcs.zip(linksDsts).toSet
  val elements: collection.Set[String] =
    (processes ++ delays ++ sources ++ constants ++ sinks).toSet
  def merge(other: idesyde.identification.DesignModel): Option[idesyde.identification.DesignModel] =
    ???

}
