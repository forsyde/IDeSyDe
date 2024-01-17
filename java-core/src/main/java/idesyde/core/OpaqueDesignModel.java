package idesyde.core;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.io.IOException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

/**
 * An opaque model to exchange fundamental data about a design model between
 * different models in different languages.
 *
 * <p>
 * This data record captures which elements of the target design models that can
 * be partially identified.
 * It provides a `category` to distinguish what type of design model this is, so
 * that different languages
 * can know which of their own data structures they should deserialize the
 * design model into.
 * </p>
 *
 * <p>
 * Check the following paper for more in-depth definitions:
 * </p>
 *
 * <p>
 * R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration
 * Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test in
 * Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 * </p>
 */
@JsonSerialize
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public record OpaqueDesignModel(
        String category,
        Set<String> elements,
        String format,
        String body
) implements DesignModel {

    @Override
    public String category() {
        return category;
    }

    @Override
    public String format() {
        return format;
    }

    @Override
    public Set<String> elements() {
        return elements;
    }

    public OpaqueDesignModel(String category) {
        this(category, new HashSet<>(), "", null);
    }

    public OpaqueDesignModel(String category, Set<String> elements, String format) {
        this(category, elements, format, null);
    }

    @Override
    public Optional<String> asString() {
        return Optional.ofNullable(body);
    }

    public Optional<String> toJsonString() {
        try {
            return Optional.of(DesignModel.objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> toCBORBytes() {
        try {
            return Optional.of(DesignModel.objectMapperCBOR.writeValueAsBytes(this));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public static OpaqueDesignModel from(DesignModel m) {
        return m.asString().map(body -> new OpaqueDesignModel(m.category(), m.elements(), m.format(), body))
                .orElse(new OpaqueDesignModel(m.category(), m.elements(), m.format()));
    }

    public static Optional<OpaqueDesignModel> fromJsonString(String s) {
        try {
            return Optional.of(DesignModel.objectMapper.readValue(s, OpaqueDesignModel.class));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        }
    }

    public static Optional<OpaqueDesignModel> fromCBORBytes(byte[] b) {
        try {
            return Optional.of(DesignModel.objectMapperCBOR.readValue(b, OpaqueDesignModel.class));
        } catch (JsonProcessingException ignored) {
            return Optional.empty();
        } catch (IOException ignored) {
            return Optional.empty();
        }
    }


}
