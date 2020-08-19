package desyde.identification;

import java.util.HashSet;
import java.util.Set;

import ForSyDe.Model.IO.ForSyDeIO;
import desyde.identification.rules.EmptyToSDFRule;
import desyde.identification.rules.SDFAndOrderAndSimpleNoCRule;
import desyde.identification.rules.SDFAndOrderRule;

public class Identifier {
	
	public Set<DecisionProblem> identify(ForSyDeIO model) {
		HashSet<DecisionProblem> identifiedSet = new HashSet<>();
		Set<DecisionExpansionRule> rules = Set.of(
				new EmptyToSDFRule(),
				new SDFAndOrderRule(),
				new SDFAndOrderAndSimpleNoCRule()
		);
		Boolean modified = true;
		while (modified) {
			modified = false;
			for (DecisionExpansionRule rule : rules) {
				DecisionProblem identified = rule.execute(model, identifiedSet);
				if (identified != null && identifiedSet.contains(identified) == false) {
					identifiedSet.add(identified);
					modified = true;
				}
			}
		}
		return identifiedSet;
	}

}
