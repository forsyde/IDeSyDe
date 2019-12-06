// Signal.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

public class Signal {
    private String signalType;

    @JsonProperty("signalType")
    public String getSignalType() { return signalType; }
    @JsonProperty("signalType")
    public void setSignalType(String value) { this.signalType = value; }
}

