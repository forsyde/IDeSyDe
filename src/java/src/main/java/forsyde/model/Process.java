// Process.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Process {
    private ProcessConstructor constructor;
    private String id;
    private Map<String, Signal> inputSignals;
    private Map<String, Signal> internalSignals;
    private Map<String, Signal> outputSignals;
    private Map<String, Process> processes;

    @JsonProperty("constructor")
    public ProcessConstructor getConstructor() { return constructor; }
    @JsonProperty("constructor")
    public void setConstructor(ProcessConstructor value) { this.constructor = value; }

    @JsonProperty("id")
    public String getID() { return id; }
    @JsonProperty("id")
    public void setID(String value) { this.id = value; }

    @JsonProperty("inputSignals")
    public Map<String, Signal> getInputSignals() { return inputSignals; }
    @JsonProperty("inputSignals")
    public void setInputSignals(Map<String, Signal> value) { this.inputSignals = value; }

    @JsonProperty("internalSignals")
    public Map<String, Signal> getInternalSignals() { return internalSignals; }
    @JsonProperty("internalSignals")
    public void setInternalSignals(Map<String, Signal> value) { this.internalSignals = value; }

    @JsonProperty("outputSignals")
    public Map<String, Signal> getOutputSignals() { return outputSignals; }
    @JsonProperty("outputSignals")
    public void setOutputSignals(Map<String, Signal> value) { this.outputSignals = value; }

    @JsonProperty("processes")
    public Map<String, Process> getProcesses() { return processes; }
    @JsonProperty("processes")
    public void setProcesses(Map<String, Process> value) { this.processes = value; }
}

