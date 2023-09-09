package idesyde.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import idesyde.core.headers.DecisionModelHeader;

import java.util.Optional;

/** The trait/interface for a decision model in the design space identification methodology, as
 * defined in [1].
 *
 * A decision model holds information on how to build a design space that is explorable. In other
 * words, an object that implements this trait is assumed to provide parameters, and/or decision
 * variables, and/or analysis techniques for a certain design model. The trait itself is the bare
 * minimum so that the identification procedure can be performed to completion properly.
 *
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test in Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 */
public interface DecisionModel {

    DecisionModelHeader header();

    default Optional<String> bodyAsText() {
        try {
            return Optional.of(objectMapper.writeValueAsString(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    };

    default Optional<byte[]> bodyAsBinary() {
        try {
            return Optional.of(objectMapper.writeValueAsBytes(this));
        } catch (JsonProcessingException e) {
            return Optional.empty();
        }
    };

    static final ObjectMapper objectMapper = new ObjectMapper().registerModule(new Jdk8Module());
    static final ObjectMapper objectMapperCBOR = new ObjectMapper(new CBORFactory()).registerModule(new Jdk8Module());
}
