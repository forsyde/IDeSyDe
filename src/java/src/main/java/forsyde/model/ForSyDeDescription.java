// ForSyDeDescription.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

public class ForSyDeDescription {
    private long descriptionVersion;
    private Map<String, Signal> functions;
    private Map<String, Process> processes;
    private Map<String, Signal> signals;

    @JsonProperty("descriptionVersion")
    public long getDescriptionVersion() { return descriptionVersion; }
    @JsonProperty("descriptionVersion")
    public void setDescriptionVersion(long value) { this.descriptionVersion = value; }

    @JsonProperty("functions")
    public Map<String, Signal> getFunctions() { return functions; }
    @JsonProperty("functions")
    public void setFunctions(Map<String, Signal> value) { this.functions = value; }

    @JsonProperty("processes")
    public Map<String, Process> getProcesses() { return processes; }
    @JsonProperty("processes")
    public void setProcesses(Map<String, Process> value) { this.processes = value; }

    @JsonProperty("signals")
    public Map<String, Signal> getSignals() { return signals; }
    @JsonProperty("signals")
    public void setSignals(Map<String, Signal> value) { this.signals = value; }
}

