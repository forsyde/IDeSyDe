// ForSyDeDescription.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class ForSyDeDescription {
    private long descriptionVersion;
    private Map<String, Function> functions;
    private String id;
    private Map<String, Process> processes;

    @JsonProperty("descriptionVersion")
    public long getDescriptionVersion() { return descriptionVersion; }
    @JsonProperty("descriptionVersion")
    public void setDescriptionVersion(long value) { this.descriptionVersion = value; }

    @JsonProperty("functions")
    public Map<String, Function> getFunctions() { return functions; }
    @JsonProperty("functions")
    public void setFunctions(Map<String, Function> value) { this.functions = value; }

    @JsonProperty("id")
    public String getID() { return id; }
    @JsonProperty("id")
    public void setID(String value) { this.id = value; }

    @JsonProperty("processes")
    public Map<String, Process> getProcesses() { return processes; }
    @JsonProperty("processes")
    public void setProcesses(Map<String, Process> value) { this.processes = value; }
}

