package idesyde.core;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Set;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record PlainIdentificationResult(
                DecisionModel[] identified,
                String[] messages) {
}
