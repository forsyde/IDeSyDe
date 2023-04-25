package idesyde.core;

public interface DecisionModelWithBody extends DecisionModel {

    String getBodyAsText();

    byte[] getBodyAsBytes();
}
