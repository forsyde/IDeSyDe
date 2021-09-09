package idesyde.identification.rules

import idesyde.identification.IdentificationRule
import idesyde.identification.models.reactor.ReactorMinusJobsMapAndSched
import forsyde.io.java.core.ForSyDeModel
import idesyde.identification.DecisionModel
import idesyde.identification.models.reactor.ReactionJob
import forsyde.io.java.typed.viewers.GenericProcessingModule
import org.apache.commons.math3.fraction.Fraction

final case class ReactorMinusJobsDSEIdentRule() extends IdentificationRule[ReactorMinusJobsMapAndSched]:

    override def identify(model: ForSyDeModel, identified: Set[DecisionModel]): (Boolean, Option[ReactorMinusJobsMapAndSched]) =
        (true, Option.empty)
    end identify


    def computeWCETFunction(model: ForSyDeModel): Map[(ReactionJob, GenericProcessingModule), Fraction] = Map.empty

end ReactorMinusJobsDSEIdentRule