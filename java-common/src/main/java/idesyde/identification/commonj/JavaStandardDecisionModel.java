package idesyde.identification.commonj;

import idesyde.identification.common.StandardDecisionModel;

import java.util.List;
import java.util.Set;

/**
 * This interface refines the original `StandardDecisionModel` so that only idiomatic java methods
 * have to be re-implemented, without about scala-specifics of the original. Since Java lacks a default
 * tuple type, the relations pair are encoded as a list.
 *
 * @see StandardDecisionModel
 */
public interface JavaStandardDecisionModel extends StandardDecisionModel {

    /**
     * @return the set of covered elements.
     */
    Set<String> getCoveredElements();

    /**
     * The list is expected to always have two elements, as if they were pairs.
     * @return the set of _pairs_ describing element relations.
     */
    Set<List<String>> getCoveredElementRelations();

    @Override
    default scala.collection.immutable.Set<Object> coveredElements() {
        final scala.collection.mutable.HashSet<String> s = new scala.collection.mutable.HashSet<>();
        getCoveredElements().forEach(s::add);
        return s.toSet();
    }

    @Override
    default scala.collection.immutable.Set<Object> coveredElementRelations() {
        final scala.collection.mutable.HashSet<List<String>> s = new scala.collection.mutable.HashSet<>();
        getCoveredElementRelations().forEach(s::add);
        return s.toSet();
    }

    @Override
    default String elementID(Object elem) {
        return elem.toString();
    }

    @Override
    default String elementRelationID(Object rel) {
        return rel.toString();
    }
}
