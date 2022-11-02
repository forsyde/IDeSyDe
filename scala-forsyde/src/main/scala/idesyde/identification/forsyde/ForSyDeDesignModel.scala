package idesyde.identification.forsyde

import scala.jdk.CollectionConverters.*

import idesyde.identification.DesignModel
import forsyde.io.java.core.ForSyDeSystemGraph
import forsyde.io.java.core.Vertex
import forsyde.io.java.core.EdgeInfo

final case class ForSyDeDesignModel(val systemGraph: ForSyDeSystemGraph) extends DesignModel {

  type VertexT = Vertex
  type EdgeT   = EdgeInfo

  def merge(other: DesignModel): Option[DesignModel] = {
    other match {
      case fOther: ForSyDeDesignModel =>
        Option(ForSyDeDesignModel(systemGraph.merge(fOther.systemGraph)))
      case _ => Option.empty
    }
  }

  val elements: Iterable[Vertex] = systemGraph.vertexSet().asScala

  val elementRelations: Iterable[EdgeInfo] = systemGraph.edgeSet().asScala
}
