// Function.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Function {
    private String id;
    private Map<String, String> inputs;
    private Map<String, String> outputs;

    @JsonProperty("id")
    public String getID() { return id; }
    @JsonProperty("id")
    public void setID(String value) { this.id = value; }

    @JsonProperty("inputs")
    public Map<String, String> getInputs() { return inputs; }
    @JsonProperty("inputs")
    public void setInputs(Map<String, String> value) { this.inputs = value; }

    @JsonProperty("outputs")
    public Map<String, String> getOutputs() { return outputs; }
    @JsonProperty("outputs")
    public void setOutputs(Map<String, String> value) { this.outputs = value; }
}

