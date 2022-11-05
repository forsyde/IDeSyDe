package idesyde.identification.forsyde

import forsyde.io.java.core.ForSyDeSystemGraph
import idesyde.identification.DesignModel
import idesyde.identification.DecisionModel
import idesyde.identification.forsyde.ForSyDeDesignModel

object ForSyDeIdentificationUtils {

  inline def toForSyDe[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (ForSyDeSystemGraph) => Option[M]
  ): Option[M] = {
    models
      .filter(_.isInstanceOf[ForSyDeDesignModel])
      .map(_.asInstanceOf[ForSyDeDesignModel])
      .map(_.systemGraph)
      .reduceOption(_.merge(_))
      .flatMap(body(_))
  }
}
