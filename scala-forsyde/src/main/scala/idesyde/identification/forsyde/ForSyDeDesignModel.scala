package idesyde.identification.forsyde

import scala.jdk.CollectionConverters.*

import idesyde.identification.DesignModel
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.Vertex
import forsyde.io.java.core.EdgeInfo

final case class ForSyDeDesignModel(val systemGraph: ForSyDeSystemGraph) extends DesignModel {

  type ElementT         = Vertex
  type ElementRelationT = EdgeInfo

  def merge(other: DesignModel): Option[DesignModel] = {
    other match {
      case fOther: ForSyDeDesignModel =>
        Option(ForSyDeDesignModel(systemGraph.merge(fOther.systemGraph)))
      case _ => Option.empty
    }
  }

  def elementID(elem: Vertex): String = elem.getIdentifier()

  def elementRelationID(rel: EdgeInfo): String = rel.toIDString()

  val elements = systemGraph.vertexSet().asScala

  val elementRelations = systemGraph.edgeSet().asScala
}
