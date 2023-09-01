package idesyde.core;

import java.util.Set;

public record IdentificationResult(
                Set<? extends DecisionModel> identified,
                Set<String> errors) {
}
