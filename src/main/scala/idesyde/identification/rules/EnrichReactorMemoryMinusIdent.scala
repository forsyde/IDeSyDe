package idesyde.identification.rules

import idesyde.identification.interfaces.IdentificationRule
import idesyde.identification.models.ReactorMinusApplication
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.interfaces.DecisionModel
import org.apache.commons.math3.fraction.Fraction
import forsyde.io.java.typed.viewers.InstrumentedFunction

final case class EnrichReactorMemoryMinusIdent()
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
      println("One reactor found!")
      val characterized =
        reactorDecisionModels.exists(m => m.reactorSize.exists(_._2 > 0))
      if (!characterized) {
        // get one totally empty model, it should be safe due to the fact that
        // there's at least one model in the set and at least one not characterized
        val oneEmpty =
          reactorDecisionModels.find(_.reactorSize.values.forall(_ == 0)).get
        println(oneEmpty)
        // use an applicative style to compute the periods inside the optional
        // and return it at the very last moment, with a default value of 0
        val newReactorSizes = oneEmpty.reactorSize
          .map((r, v) => (r, r.getReactionImplementationPort(model)))
          .filter((r, f) =>
            f.isPresent && InstrumentedFunction.conforms(f.get.getViewedVertex)
          )
          .map((r, f) =>
            r -> InstrumentedFunction
              .safeCast(f.get().getViewedVertex)
              .map(_.getMaxMemorySizeInBytes.toInt)
              .orElse(0)
          )
        // Done, enriched one model and fix point
        (true, Option(oneEmpty.copy(reactorSize = newReactorSizes)))
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
