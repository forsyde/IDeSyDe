package idesyde.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import idesyde.core.headers.DesignModelHeader;

/** The trait/interface for a design model in the design space identification methodology, as
 * defined in [1].
 *
 * In essence, the [[DesignModel]] is the system model, or pragmatically, a wrapper around the
 * original system model data types. It can be thought of conceptually as the "database" with all
 * the information about the system we need. The only requirement that is imposed on concrete
 * [[DesignModel]] s is that they have a notion of "identifiers" so that two elements of type
 * [[ElementT]] can always be compared for equality and uniqueness. The ID of an element does not
 * have to be anything pretty, it could very well be integers, as long as they are _unique_ for
 * _unique_ elements.
 *
 * [1] R. Jordão, I. Sander and M. Becker, "Formulation of Design Space Exploration Problems by
 * Composable Design Space Identification," 2021 Design, Automation &amp; Test in Europe Conference &amp;
 * Exhibition (DATE), 2021, pp. 1204-1207, doi: 10.23919/DATE51398.2021.9474082.
 *
 */
public interface DesignModel {

    DesignModelHeader header();

    static final ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
}
