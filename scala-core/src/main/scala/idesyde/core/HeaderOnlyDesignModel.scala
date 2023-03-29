package idesyde.core

import idesyde.core.headers.DesignModelHeader
import idesyde.core.DesignModel
import idesyde.core.headers.LabelledArcWithPorts

final case class HeaderOnlyDesignModel(override val header: DesignModelHeader) extends DesignModel {

  type ElementT = String

  type ElementRelationT = LabelledArcWithPorts

  override def merge(other: DesignModel): Option[DesignModel] = other match {
    case HeaderOnlyDesignModel(DesignModelHeader(header.category, elems, models, rels)) =>
      Some(
        HeaderOnlyDesignModel(
          DesignModelHeader(
            header.category,
            header.model_paths ++ models,
            header.elements ++ elems.filterNot(e => header.elements.contains(e)),
            header.relations ++ rels.filterNot(e => header.relations.contains(e))
          )
        )
      )
    case _ => None
  }

  lazy val elements = header.elements.toSet

  lazy val elementRelations = header.relations.toSet

  def elementID(elem: String): String = elem

  def elementRelationID(rel: LabelledArcWithPorts): LabelledArcWithPorts = rel

  def uniqueIdentifier = header.category

}
