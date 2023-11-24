package idesyde.devicetree.identification

import scala.jdk.CollectionConverters._

import org.virtuslab.yaml.*

import idesyde.devicetree.OSDescription
import idesyde.core.DecisionModel
import idesyde.core.DesignModel

final case class OSDescriptionDesignModel(
    val description: OSDescription
) extends DesignModel {

  type ElementT = String

  override def elements(): java.util.Set[String] =
    (description.oses.keySet ++ description.oses.values.map(_.host).toSet ++ description.oses.values
      .flatMap(_.affinity)
      .toSet ++ description.oses
      .flatMap((k, v) => v.affinity.map(o => k -> o))
      .toSet
      .map(_.toString)).asJava

  def merge(other: DesignModel): Option[DesignModel] = other match {
    case o: OSDescriptionDesignModel =>
      Some(OSDescriptionDesignModel(description.mergeLeft(o.description)))
    case _ => None
  }

  override def category(): String = "OSDescriptionDesignModel"

  
}
