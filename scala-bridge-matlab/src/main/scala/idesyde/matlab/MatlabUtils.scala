package idesyde.matlab

import idesyde.core.DecisionModel
import idesyde.core.DesignModel

trait MatlabUtils {

  inline def toSimulinkReactiveDesignModel[M <: DecisionModel](models: Set[DesignModel])(
      inline body: (SimulinkReactiveDesignModel) => (Set[M], Set[String])
  ): (Set[M], Set[String]) = {
    val ms = models
      .filter(_.isInstanceOf[SimulinkReactiveDesignModel])
      .map(_.asInstanceOf[SimulinkReactiveDesignModel])
    if (!ms.isEmpty) {
      var m       = ms.head
      var defined = true
      for (
        mOther <- ms.drop(1); if defined;
        merged = m
          .merge(mOther)
          .filter(_.isInstanceOf[SimulinkReactiveDesignModel])
          .map(_.asInstanceOf[SimulinkReactiveDesignModel])
      ) {
        if (merged.isDefined) then m = merged.get else defined = false
      }
      if (defined) {
        body(m)
      } else {
        (Set(), Set())
      }
    } else {
      (Set(), Set())
    }
  }
}
