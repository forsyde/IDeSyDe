package desyde.identification;

import java.util.Set;

import ForSyDe.Model.Core.Edge;
import ForSyDe.Model.IO.ForSyDeIO;

public interface DecisionExpansionRule {
	
	public DecisionProblem execute(ForSyDeIO model, Set<DecisionProblem> identifiedSet);
	
	public String getCoverRuleId();
	
}
