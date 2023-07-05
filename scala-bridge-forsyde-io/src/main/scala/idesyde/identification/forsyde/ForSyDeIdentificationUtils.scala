package idesyde.identification.forsyde

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.core.DesignModel
import idesyde.core.DecisionModel
import idesyde.forsydeio.ForSyDeDesignModel

object ForSyDeIdentificationUtils {

  inline def toForSyDe[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (ForSyDeSystemGraph) => Set[M]
  ): Set[M] = {
    models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
      .map(body(_))
      .getOrElse(Set())
  }
}
