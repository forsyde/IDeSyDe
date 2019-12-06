// Function.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

public class Function {
    private Map<String, String> arguments;

    @JsonProperty("arguments")
    public Map<String, String> getArguments() { return arguments; }
    @JsonProperty("arguments")
    public void setArguments(Map<String, String> value) { this.arguments = value; }
}
