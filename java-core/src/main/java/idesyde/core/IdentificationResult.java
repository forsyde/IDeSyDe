package idesyde.core;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record IdentificationResult(
        Set<? extends DecisionModel> identified,
        Set<String> messages) {

    public DecisionModel[] identifiedAsArray() {
        return identified().toArray(new DecisionModel[0]);
    }

    public String[] messagesAsArray() {
        return messages().toArray(new String[0]);
    }

    public int part() {
        return identified.stream().map(DecisionModel::part).map(Set::size).reduce(0, Integer::sum);
    }
}
