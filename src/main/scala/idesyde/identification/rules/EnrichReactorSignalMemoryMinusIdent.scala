package idesyde.identification.rules

import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.models.ReactorMinusApplication
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.DecisionModel
import org.apache.commons.math3.fraction.Fraction
import forsyde.io.java.typed.interfaces.InstrumentedFunction
import forsyde.io.java.typed.interfaces.InstrumentedSignal

final case class EnrichReactorSignalMemoryMinusIdent()
    extends IdentificationRule[ReactorMinusApplication] {

  override def identify(
      model: ForSyDeModel,
      identified: Set[DecisionModel]
  ) = {
    val reactorDecisionModels =
      identified
        .filter(_.isInstanceOf[ReactorMinusApplication])
        .map(_.asInstanceOf[ReactorMinusApplication])
    if (!reactorDecisionModels.isEmpty) {
      // Check if at least one decision model has already periods greater than zero
      val Characterized =
        reactorDecisionModels.exists(m => m.signalSize.exists(_._2 > 0))
      if (!Characterized) {
        // get one totally empty model, it should be safe due to the fact that
        // there's at least one model in the set and at least one not characterized
        val oneEmpty =
          reactorDecisionModels.find(_.signalSize.values.forall(_ == 0)).get
        // use an applicative style to compute the periods inside the optional
        // and return it at the very last moment, with a default value of 0
        val newSignalSizes = oneEmpty.signalSize
          .map((s, v) =>
            s -> InstrumentedSignal
              .safeCast(s)
              .map(i => i.getMaxElemCount * i.getMaxElemSizeBytes)
              .orElse(0)
          )
        // Done, enriched one model and fix point
        (true, Option(oneEmpty.copy(signalSize = newSignalSizes)))
      } else {
        // nothing can be gained, stop.
        (true, Option.empty)
      }
    } else {
      // no base DM has been found yet, wait.
      (false, Option.empty)
    }
  }

}
