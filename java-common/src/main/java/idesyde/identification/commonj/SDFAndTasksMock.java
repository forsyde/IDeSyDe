package idesyde.identification.commonj;

import idesyde.identification.common.StandardDecisionModel;
import scala.Tuple2;
import scala.collection.immutable.Set;

/**
 * This is a placeholder class that may eventually become the decision model
 * that brings together information from SDF and Tasks in a certain way.
 */
public class SDFAndTasksMock implements StandardDecisionModel {

    @Override
    public String uniqueIdentifier() {
        return "SDFAndTasksMock";
    }

    @Override
    public Set<Object> coveredElements() {
        return null;
    }

    @Override
    public Set<Object> coveredElementRelations() {
        return null;
    }

    @Override
    public String elementID(Object elem) {
        return null;
    }

    @Override
    public String elementRelationID(Object rel) {
        return null;
    }
}
