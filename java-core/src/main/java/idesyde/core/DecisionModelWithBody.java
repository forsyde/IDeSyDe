package idesyde.core;

import com.fasterxml.jackson.core.JsonProcessingException;

public interface DecisionModelWithBody extends DecisionModel {

    String getBodyAsText() throws JsonProcessingException;

    byte[] getBodyAsBytes() throws JsonProcessingException;
}
