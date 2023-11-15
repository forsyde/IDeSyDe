package idesyde.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

/**
 * An opaque model to exchange fundamental data about a decision model between
 * different models in different languages.
 *
 * <p>
 * This data record captures which elements of the target design models have
 * been partially identified.
 * It provides a `category` to distinguish what type of decision model this is,
 * so that different languages
 * can know which of their own data structures they should deserialize the
 * decision model into.
 * </p>
 *
 * <p>
 * Check the following paper for more in-depth definitions:
 * </p>
 *
 * <p>
 * R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration
 * Problems by
 * Composable Design Space Identification," 2021 Design, Automation & Test in
 * Europe Conference &
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 * </p>
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record OpaqueDecisionModel(
        String category,
        Set<String> part,
        @JsonProperty("body_json") Optional<String> bodyJson,
        @JsonProperty("body_msgpack") Optional<byte[]> bodyMsgPack,
        @JsonProperty("body_cbor") Optional<byte[]> bodyCBOR) implements DecisionModel {

    public Optional<String> bodyAsJsonString() {
        return bodyJson;
    }

    public Optional<byte[]> bodyAsCBORBinary() {
        return bodyCBOR;
    }

    public Optional<String> toJsonString() {
        try {
            return Optional.of(DecisionModel.objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> toCBORBytes() {
        try {
            return Optional.of(DecisionModel.objectMapperCBOR.writeValueAsBytes(this));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public <T extends DecisionModel> Optional<T> to(Class<T> cls) {
        return bodyCBOR.flatMap(body -> {
            try {
                return Optional.of(DecisionModel.objectMapper.readValue(body, cls));
            } catch (JsonProcessingException ignored) {
                return Optional.empty();
            } catch (IOException ignored) {
                return Optional.empty();
            }
        }).or(() -> bodyJson.flatMap(body -> {
            try {
                return Optional.of(DecisionModel.objectMapper.readValue(body, cls));
            } catch (JsonProcessingException ignored) {
                return Optional.empty();
            }
        }));
    }

    public static OpaqueDecisionModel from(DecisionModel m) {
        return new OpaqueDecisionModel(m.category(), m.part(), m.bodyAsJsonString(), Optional.empty(),
                m.bodyAsCBORBinary());
    }

    public static Optional<OpaqueDecisionModel> fromJsonString(String s) {
        try {
            return Optional.of(DecisionModel.objectMapper.readValue(s, OpaqueDecisionModel.class));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<OpaqueDecisionModel> fromCBORBytes(byte[] b) {
        try {
            return Optional.of(DecisionModel.objectMapperCBOR.readValue(b, OpaqueDecisionModel.class));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }

}
