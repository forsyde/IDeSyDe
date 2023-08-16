package idesyde.core;

import java.util.List;

public record IdentificationResult(
        List<DecisionModel> identified,
        List<String> errors
) {
}
