package idesyde.core;

import com.fasterxml.jackson.core.JsonProcessingException;

/**
 * This interface enriches DecisionModel with the capability of saving the body of the decision model.
 */
public interface DecisionModelWithBody extends DecisionModel {

    String getBodyAsText() throws JsonProcessingException;

    byte[] getBodyAsBytes() throws JsonProcessingException;
}
