package idesyde.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;

/**
 * The trait/interface for a decision model in the design space identification
 * methodology, as
 * defined in [1].
 *
 * A decision model holds information on how to build a design space that is
 * explorable. In other
 * words, an object that implements this trait is assumed to provide parameters,
 * and/or decision
 * variables, and/or analysis techniques for a certain design model. The trait
 * itself is the bare
 * minimum so that the identification procedure can be performed to completion
 * properly.
 *
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space
 * Exploration Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test
 * in Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 */
public interface DecisionModel extends Comparable<DecisionModel> {

    /**
     * Used to represent a decision model in a exchangeable format. Now this is done
     * solely through opaque models.
     * 
     * @return the header/
     */
    // @Deprecated
    // default DecisionModelHeader header() {
    // return new DecisionModelHeader(
    // category(), part(), Optional.empty());
    // };

    /**
     * @return The set of identifiers for partially identified elements
     */
    default Set<String> part() {
        return Set.of();
    }

    default String[] partAsArray() {
        return part().toArray(new String[0]);
    }

    /**
     * @return The category that describes this decision model. Default value (and
     *         recommendation) is the class name.
     * 
     */
    default String category() {
        return getClass().getSimpleName();
    }

    /**
     * @return The "body" of the model as a string, when possible.
     */
    default Optional<String> asJsonString() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    };

    /**
     * @return The "body" of the model as a CBOR byte array, when possible.
     */
    default Optional<byte[]> asCBORBinary() {
        try {
            return Optional.of(objectMapper.writeValueAsBytes(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    };

    default Optional<byte[]> globalMD5Hash() {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(category().getBytes(StandardCharsets.UTF_8));
            part().stream().sorted().forEachOrdered(s -> md5.update(s.getBytes(StandardCharsets.UTF_8)));
            return Optional.of(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }

    default Optional<byte[]> globalSHA2Hash() {
        MessageDigest sha2;
        try {
            sha2 = MessageDigest.getInstance("SHA-512");
            sha2.update(category().getBytes(StandardCharsets.UTF_8));
            part().stream().sorted().forEachOrdered(s -> sha2.update(s.getBytes(StandardCharsets.UTF_8)));
            return Optional.of(sha2.digest());
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }

    @Override
    default int compareTo(DecisionModel o) {
        return globalSHA2Hash().flatMap(hash -> o.globalSHA2Hash().map(hash2 -> {
            for (int i = 0; i < hash.length; i++) {
                if (hash[i] != hash2[i]) {
                    return Byte.compare(hash[i], hash2[i]);
                }
            }
            return 0;
        })).orElse(0);
    }

    static <T extends DecisionModel> Optional<T> fromCBOR(byte[] bytes, Class<T> cls) {
        try {
            return Optional.of(objectMapperCBOR.readValue(bytes, cls));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    static <T extends DecisionModel> Optional<T> fromJsonString(String str, Class<T> cls) {
        try {
            return Optional.of(objectMapper.readValue(str, cls));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public static <T extends DecisionModel> Optional<T> fromOpaque(OpaqueDecisionModel opaqueDecisionModel,
            Class<T> cls) {
        if (opaqueDecisionModel.category().equals(cls.getName())
                || opaqueDecisionModel.category().equals(cls.getCanonicalName())) {
            return opaqueDecisionModel.asCBORBinary().flatMap(bs -> DecisionModel.fromCBOR(bs, cls)).or(
                    () -> opaqueDecisionModel.asJsonString().flatMap(str -> DecisionModel.fromJsonString(str, cls)));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    public static <T extends DecisionModel> Optional<T> cast(DecisionModel m, Class<T> cls) {
        if (m instanceof OpaqueDecisionModel opaqueDecisionModel) {
            return fromOpaque(opaqueDecisionModel, cls);
        } else if (cls.isAssignableFrom(m.getClass())) {
            return (Optional<T>) Optional.of(m);
        }
        return Optional.empty();
    }

    /**
     * The shared and static Jackson object mapper used for (de) serialization to
     * (from) JSON.
     */
    static ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
    /**
     * The shared and static Jackson object mapper used for (de) serialization to
     * (from) CBOR.
     */
    static ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
}
