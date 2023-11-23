package idesyde.core;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The trait/interface for a module that holds all the key components to perform
 * design space identification and design space exploration according to [1].
 *
 * <p>
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space
 * Exploration Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test
 * in Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 * </p>
 */

public interface Module {

    /**
     * Unique string used to identify this module during orchetration. Ideally it
     * matches the name of
     * the implementing class (or is the implemeting class name, ditto).
     */
    String uniqueIdentifier();

    /**
     * Perform reverse identification with the input models.
     * 
     * @param solvedDecisionModels the models that have been explored.
     * @param designModels         the design models used as input.
     * @return a set of reversed identified design models.
     */
    default Set<DesignModel> reverseIdentification(
            Set<DecisionModel> solvedDecisionModels,
            Set<DesignModel> designModels) {
        return Set.of();
    }

    /**
     * Perform a step of the identification procedure.
     * 
     * @param designModels   the input design models
     * @param decisionModels the input decision models _and_ identified decision
     *                       models.
     * @return the decision models identified in one step.
     */
    default IdentificationResult identification(
            Set<DesignModel> designModels,
            Set<DecisionModel> decisionModels) {
        return new IdentificationResult(Set.of(), Set.of());
    }

    /**
     * Return the explorers that are contained in this module
     * 
     */
    default Set<Explorer> explorers() {
        return Set.of();
    }

}
