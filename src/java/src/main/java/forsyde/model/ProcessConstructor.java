// ProcessConstructor.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

public class ProcessConstructor {
    private Map<String, Function> functionArguments;
    private String name;
    private Map<String, String> parameters;
    private Map<String, String> valueArguments;

    @JsonProperty("functionArguments")
    public Map<String, Function> getFunctionArguments() { return functionArguments; }
    @JsonProperty("functionArguments")
    public void setFunctionArguments(Map<String, Function> value) { this.functionArguments = value; }

    @JsonProperty("name")
    public String getName() { return name; }
    @JsonProperty("name")
    public void setName(String value) { this.name = value; }

    @JsonProperty("parameters")
    public Map<String, String> getParameters() { return parameters; }
    @JsonProperty("parameters")
    public void setParameters(Map<String, String> value) { this.parameters = value; }

    @JsonProperty("valueArguments")
    public Map<String, String> getValueArguments() { return valueArguments; }
    @JsonProperty("valueArguments")
    public void setValueArguments(Map<String, String> value) { this.valueArguments = value; }
}

