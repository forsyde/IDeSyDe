package idesyde.identification.commonj;

import java.util.List;
import java.util.Set;

/**
 * This is a placeholder class that may eventually become the decision model
 * that brings together information from SDF and Tasks in a certain way.
 */
public class SDFAndTasksMock implements JavaStandardDecisionModel {

    @Override
    public String uniqueIdentifier() {
        return "SDFAndTasksMock";
    }

    @Override
    public java.util.Set<String> getCoveredElements() {
        return null;
    }

    @Override
    public Set<List<String>> getCoveredElementRelations() {
        return null;
    }
}
