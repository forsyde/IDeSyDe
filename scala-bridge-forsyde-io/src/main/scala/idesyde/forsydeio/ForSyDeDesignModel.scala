package idesyde.forsydeio

import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*

import idesyde.core.DesignModel
import forsyde.io.core.SystemGraph
import forsyde.io.core.EdgeInfo
import forsyde.io.core.Vertex
import forsyde.io.core.ModelHandler
import forsyde.io.lib.TraitNamesFrom0_6To0_7
import idesyde.forsydeio.ForSyDeDesignModel.modelHandler

final case class ForSyDeDesignModel(val systemGraph: SystemGraph) extends DesignModel {

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

  def category: String = "ForSyDeDesignModel"

  def bodyAsText: Option[String] = {
    Some(modelHandler.printModel(systemGraph, "fiodl"))
  }
}

object ForSyDeDesignModel {
  val modelHandler = ModelHandler().registerSystemGraphMigrator(TraitNamesFrom0_6To0_7())

  def fromText(s: String): Option[ForSyDeDesignModel] = {
    try {
      val sgraph = modelHandler.readModel(s, "fiodl")
      Some(ForSyDeDesignModel(sgraph))
    } catch {
      case e: Exception => None
    }
  }
}
