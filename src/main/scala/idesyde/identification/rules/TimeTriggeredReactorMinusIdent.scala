package idesyde.identification.rules

import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.models.TimeTriggeredReactorMinus
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.DecisionModel
import idesyde.identification.models.ReactorMinusApplication
import idesyde.identification.models.TimeTriggeredPlatform
import scala.jdk.CollectionConverters._

final case class TimeTriggeredReactorMinusIdent()
    extends IdentificationRule[TimeTriggeredReactorMinus] {

  def identify(model: ForSyDeModel, identified: Set[DecisionModel]) = {
    val existsReactor =
      identified.exists(_.isInstanceOf[ReactorMinusApplication])
    val existsTTPlatorm =
      identified.exists(_.isInstanceOf[TimeTriggeredPlatform])
    if (existsReactor && existsTTPlatorm) {
      val dominantReactorMinusApp = identified
        .filter(_.isInstanceOf[ReactorMinusApplication])
        .map(_.asInstanceOf[ReactorMinusApplication])
        .reduce((m1, m2) => if (m1.dominates(m2)) m1 else m2)
      val dominanTTPlatform = identified
        .filter(_.isInstanceOf[TimeTriggeredPlatform])
        .map(_.asInstanceOf[TimeTriggeredPlatform])
        .reduce((m1, m2) => if (m1.dominates(m2)) m1 else m2)
      val jobs = dominantReactorMinusApp.periods
        .flatMap((r, t) =>
          for (
            i <- 1 until dominantReactorMinusApp.hyperPeriod
              .divide(t)
              .getNumerator
          ) yield (r, i)
        )
        .toSet
      // do the product and keep only those that do have a channels in then
      val extendedChannels =
        (for (
          (a, i) <- jobs; (aa, ii) <- jobs;
          c = dominantReactorMinusApp.signals.get((a, aa))
          if !c.isEmpty
        )
          yield (((a, i), (aa, ii)), c.get)).toMap
      val found = TimeTriggeredReactorMinus(
        jobs,
        extendedChannels,
        dominantReactorMinusApp,
        dominanTTPlatform
      )
      // if this model has already been identified, then it is a fix point
      if (identified.contains(found)) (true, Option.empty)
      // otherwise we move on
      else (false, Option(found))
    } else if (
      model.vertexSet.asScala.subsetOf(
        identified.flatMap(_.coveredVertexes()).toSet
      )
    ) {
      (true, Option.empty)
    } else {
      (false, Option.empty)
    }
  }

}
