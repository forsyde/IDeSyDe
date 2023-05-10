package idesyde.forsydeio

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import idesyde.core.DesignModel
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.Vertex
import forsyde.io.java.core.EdgeInfo
import idesyde.core.headers.LabelledArcWithPorts

final case class ForSyDeDesignModel(val systemGraph: ForSyDeSystemGraph) extends DesignModel {

  type ElementT = Vertex | EdgeInfo

  def merge(other: DesignModel): Option[DesignModel] = {
    other match {
      case fOther: ForSyDeDesignModel =>
        Option(ForSyDeDesignModel(systemGraph.merge(fOther.systemGraph)))
      case _ => Option.empty
    }
  }

  def elementID(elem: Vertex | EdgeInfo): String =
    elem match {
      case v: Vertex   => v.getIdentifier()
      case e: EdgeInfo => e.toIDString()
    }

  // def elementRelationID(rel: EdgeInfo): LabelledArcWithPorts =
  //   LabelledArcWithPorts(
  //     rel.sourceId,
  //     rel.getSourcePort().toScala,
  //     rel.edgeTraits.asScala.map(_.getName()).reduceLeftOption((l, s) => l + "," + s),
  //     rel.getTarget(),
  //     rel.getTargetPort().toScala
  //   )

  val elements = systemGraph.vertexSet().asScala.toSet ++ systemGraph.edgeSet().asScala.toSet

  def uniqueIdentifier: String = "YyyYyYyDesignModel"
}
