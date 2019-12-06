// Signal.java

package forsyde.model;

import java.util.*;
import com.fasterxml.jackson.annotation.*;

@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Signal {
    private String id;
    private String signalType;

    @JsonProperty("id")
    public String getID() { return id; }
    @JsonProperty("id")
    public void setID(String value) { this.id = value; }

    @JsonProperty("signalType")
    public String getSignalType() { return signalType; }
    @JsonProperty("signalType")
    public void setSignalType(String value) { this.signalType = value; }
}
