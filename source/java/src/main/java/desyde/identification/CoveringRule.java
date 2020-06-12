package desyde.identification;

import java.util.Set;

import ForSyDe.Model.Core.Edge;
import ForSyDe.Model.IO.ForSyDeIO;

public interface CoveringRule {
	
	public Cover execute(ForSyDeIO model, Set<Cover> identified);
	
	public Boolean applicable(ForSyDeIO model, Set<Cover> identified);
	
	public String getCoverRuleId();
	
}
