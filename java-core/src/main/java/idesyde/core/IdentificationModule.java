package idesyde.core;

import idesyde.core.headers.DecisionModelHeader;
import idesyde.core.headers.DesignModelHeader;

import java.nio.file.Path;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/** The trait/interface for an identification module that provides the identification and
 * integration rules required to power the design space identification process [1].
 *
 * This trait extends [[idesyde.core.IdentificationLibrary]] to push further the modularization of
 * the DSI methodology. In essence, this trait transforms an [[idesyde.core.IdentificationLibrary]]
 * into an independent callable library, which can be orchestrated externally. This enables modules
 * in different languages to cooperate seamlessly.
 *
 */
public interface IdentificationModule {


    /** Unique string used to identify this module during orchetration. Ideally it matches the name of
     * the implementing class (or is the implemeting class name, ditto).
     */
    String uniqueIdentifier();

    default Optional<DesignModel> inputsToDesignModel(Path p) { return Optional.empty(); }

    /** decoders used to reconstruct decision models from headers.
     *
     * Ideally, these functions are able to produce a decision model from the headers read during a
     * call of this module.
     *
     * @return
     *   the registered decoders
     */
    Optional<DecisionModel> decisionHeaderToModel(DecisionModelHeader header);

    default Optional<Path> designModelToOutput(DesignModel m, Path p) {return Optional.empty(); }

    Set<ReverseIdentificationRule> reverseIdentificationRules();

    Set<IdentificationRule> identificationRules();

    default Set<DesignModel> reverseIdentification(
            Set<DecisionModel> solvedDecisionModels,
            Set<DesignModel> designModels
    ) {
        return reverseIdentificationRules().stream().flatMap(irrule ->
                irrule.apply(solvedDecisionModels, designModels).stream()
        ).collect(Collectors.toSet());
    }

    default Set<IdentificationResult> identificationStep(
            long stepNumber,
            Set<DesignModel> designModels,
            Set<DecisionModel> decisionModels
    ) {
        return identificationRules().stream()
                .map(identificationRule -> identificationRule.apply(designModels, decisionModels))
                .filter(m -> !decisionModels.containsAll(m.identified()))
                .collect(Collectors.toSet());
//        if (stepNumber == 0L) {
//        } else {
//            return identificationRules().stream().filter(x -> !x.usesDesignModels())
//                    .flatMap(identificationRule -> identificationRule.apply(designModels, decisionModels).stream())
//                    .filter(m -> !decisionModels.contains(m))
//                    .collect(Collectors.toSet());
//        }
    }

}
