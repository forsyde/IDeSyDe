package idesyde.forsydeio.legacy

import scala.jdk.OptionConverters._
import scala.jdk.CollectionConverters._

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.legacy.ForSyDeDesignModel
import forsyde.io.core.SystemGraph
import idesyde.core.OpaqueDesignModel
import idesyde.forsydeio.legacy.ForSyDeDesignModel.modelHandler
import idesyde.core.OpaqueDecisionModel

object ForSyDeIdentificationUtils {

  inline def toForSyDe[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (SystemGraph) => (Set[M], Set[String])
  ): (Set[M], Set[String]) = {
    var messages = scala.collection.mutable.Set[String]()
    models
      .flatMap(_ match {
        case ForSyDeDesignModel(systemGraph) => Some(systemGraph)
        case m: OpaqueDesignModel =>
          if (modelHandler.canLoadModel(m.format())) {
            try {
              Some(modelHandler.readModel(m.body(), m.format()))
            } catch {
              case e: Exception => {
                messages += e.getMessage()
                None
              }
            }
          } else None
        case _ => None
      })
      .reduceOption(_.merge(_))
      .map(body(_))
      .map((a, b) => (a, b ++ messages.toSet))
      .getOrElse((Set(), messages.toSet ++ Set("No ForSyDe IO compliant model present")))
  }

}
