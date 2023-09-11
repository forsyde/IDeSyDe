package idesyde.forsydeio

import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.ForSyDeDesignModel
import forsyde.io.core.SystemGraph

object ForSyDeIdentificationUtils {

  inline def toForSyDe[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (SystemGraph) => (Set[M], Set[String])
  ): (Set[M], Set[String]) = {
    models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
      .map(body(_))
      .getOrElse((Set(), Set("No ForSyDe model present")))
  }
}
