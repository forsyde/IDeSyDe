package idesyde.core;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record IdentificationResult(
        Set<? extends DecisionModel> identified,
        Set<String> errors) {
}
