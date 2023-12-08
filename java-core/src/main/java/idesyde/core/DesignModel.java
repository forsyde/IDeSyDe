package idesyde.core;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Optional;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

/**
 * The trait/interface for a design model in the design space identification
 * methodology, as
 * defined in [1].
 *
 * In essence, the [[DesignModel]] is the system model, or pragmatically, a
 * wrapper around the
 * original system model data types. It can be thought of conceptually as the
 * "database" with all
 * the information about the system we need. The only requirement that is
 * imposed on concrete
 * [[DesignModel]] s is that they have a notion of "identifiers" so that two
 * elements of type
 * [[ElementT]] can always be compared for equality and uniqueness. The ID of an
 * element does not
 * have to be anything pretty, it could very well be integers, as long as they
 * are _unique_ for
 * _unique_ elements.
 *
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space
 * Exploration Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test
 * in Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 *
 */
public interface DesignModel extends Comparable<DesignModel> {

    // default DesignModelHeader header() {
    // return new DesignModelHeader(category(), elements(), new HashSet<>());
    // }

    /**
     * @return The set of identifiers for partially identifiable elements
     */
    default Set<String> elements() {
        return Set.of();
    }

    /**
     * @return The category that describes this design model. Default value (and
     *         recommendation) is the class name.
     * 
     */
    default String category() {
        return getClass().getSimpleName();
    }

    /**
     * @return The format associated with this decision model. E.g. `fiodl` for
     *         ForSyDe IO
     *         files.
     */
    default String format() {
        return "";
    }

    /**
     * @return this design model as a string, when possible.
     */
    default Optional<String> asString() {
        return Optional.empty();
    }

    default Optional<byte[]> globalMD5Hash() {
        MessageDigest md5;
        try {
            md5 = MessageDigest.getInstance("MD5");
            md5.update(format().getBytes());
            md5.update(category().getBytes());
            elements().stream().sorted().forEachOrdered(s -> md5.update(s.getBytes()));
            return Optional.of(md5.digest());
        } catch (NoSuchAlgorithmException e) {
            return Optional.empty();
        }
    }

    

    @Override
    default int compareTo(DesignModel o) {
        return globalMD5Hash().flatMap(hash -> o.globalMD5Hash().map(hash2 -> {
            for (int i = 0; i < hash.length; i++) {
                if (hash[i] != hash2[i]) {
                    return Byte.compare(hash[i], hash2[i]);
                }
            }
            return 0;
        })).orElse(0);
    }

    /**
     * The shared and static Jackson object mapper used for (de) serialization to
     * (from) JSON.
     */
    static final ObjectMapper objectMapper = new ObjectMapper();
    /**
     * The shared and static Jackson object mapper used for (de) serialization to
     * (from) CBOR.
     */
    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory());
}
