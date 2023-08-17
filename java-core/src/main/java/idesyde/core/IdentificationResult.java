package idesyde.core;

import java.util.Set;

public record IdentificationResult(
        Set<DecisionModel> identified,
        Set<String> errors
) {
}
