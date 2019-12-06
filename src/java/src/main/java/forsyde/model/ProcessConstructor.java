// ProcessConstructor.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class ProcessConstructor {
    private Map<String, Function> functionArguments;
    private String id;
    private String name;
    private Map<String, Object> parameters;
    private Map<String, String> valueArguments;

    @JsonProperty("functionArguments")
    public Map<String, Function> getFunctionArguments() { return functionArguments; }
    @JsonProperty("functionArguments")
    public void setFunctionArguments(Map<String, Function> value) { this.functionArguments = value; }

    @JsonProperty("id")
    public String getID() { return id; }
    @JsonProperty("id")
    public void setID(String value) { this.id = value; }

    @JsonProperty("name")
    public String getName() { return name; }
    @JsonProperty("name")
    public void setName(String value) { this.name = value; }

    @JsonProperty("parameters")
    public Map<String, Object> getParameters() { return parameters; }
    @JsonProperty("parameters")
    public void setParameters(Map<String, Object> value) { this.parameters = value; }

    @JsonProperty("valueArguments")
    public Map<String, String> getValueArguments() { return valueArguments; }
    @JsonProperty("valueArguments")
    public void setValueArguments(Map<String, String> value) { this.valueArguments = value; }
}

