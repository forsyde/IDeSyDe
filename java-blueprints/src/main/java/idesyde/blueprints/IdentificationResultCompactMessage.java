package idesyde.blueprints;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import idesyde.core.DecisionModel;
import idesyde.core.IdentificationResult;

import java.util.Base64;
import java.util.Optional;
import java.util.Set;

@JsonSerialize
public record IdentificationResultCompactMessage(
        Set<String> identified,
        Set<String> messages) {

    public Optional<String> toJsonString() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    }

    public static final ObjectMapper objectMapperCBOR = DecisionModel.objectMapperCBOR;
    public static final ObjectMapper objectMapper = DecisionModel.objectMapper;

    public static IdentificationResultCompactMessage from(IdentificationResultMessage irm) {
        return new IdentificationResultCompactMessage(
                irm.identified().stream().flatMap(x -> x.globalSHA2Hash().stream())
                        .map(hash -> Base64.getEncoder().withoutPadding().encodeToString(hash))
                        .collect(java.util.stream.Collectors.toSet()),
                irm.messages());
    }

    public static IdentificationResultCompactMessage from(IdentificationResult ir) {
        return new IdentificationResultCompactMessage(
                ir.identified().stream().flatMap(x -> x.globalSHA2Hash().stream())
                        .map(hash -> Base64.getEncoder().withoutPadding().encodeToString(hash))
                        .collect(java.util.stream.Collectors.toSet()),
                ir.messages());
    }
}
