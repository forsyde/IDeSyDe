package idesyde.devicetree.identification

import idesyde.devicetree.OSDescription
import idesyde.identification.DecisionModel
import idesyde.identification.DesignModel

final case class OSDescriptionDesignModel(
    val description: OSDescription
) extends DesignModel {

  type ElementT = String

  type ElementRelationT = (String, String)

  lazy val elementRelations: collection.Set[(String, String)] =
    description.oses.flatMap((k, v) => v.affinity.map(o => k -> o)).toSet

  lazy val elements: collection.Set[String] =
    description.oses.keySet ++ description.oses.values.map(_.host).toSet ++ description.oses.values
      .flatMap(_.affinity)
      .toSet

  override def merge(other: DesignModel): Option[DesignModel] = other match {
    case o: OSDescriptionDesignModel =>
      Some(OSDescriptionDesignModel(description.mergeLeft(o.description)))
    case _ => None
  }

  override def elementRelationID(rel: ElementRelationT): String = rel.toString()

  override def elementID(elem: ElementT): String = elem

  def uniqueIdentifier: String = "OSDescriptionDesignModel"
}
