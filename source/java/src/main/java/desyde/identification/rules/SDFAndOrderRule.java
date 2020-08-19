package desyde.identification.rules;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ForSyDe.Model.IO.ForSyDeIO;
import ForSyDe.Model.Refinement.StaticCyclicSchedule;
import desyde.identification.DecisionExpansionRule;
import desyde.identification.DecisionProblem;
import desyde.identification.problems.SDFAndOrder;
import desyde.identification.problems.SDFExecution;

public class SDFAndOrderRule implements DecisionExpansionRule {
	
	

	@Override
	public DecisionProblem execute(ForSyDeIO model, Set<DecisionProblem> identifiedSet) {
		Set<StaticCyclicSchedule> orders = model.streamContained()
				.filter(e -> e instanceof StaticCyclicSchedule)
				.map(e -> (StaticCyclicSchedule) e)
				.collect(Collectors.toSet());
		Optional<SDFExecution> identifiedSDF = identifiedSet.stream()
				.filter(e -> e instanceof SDFExecution)
				.map(e -> (SDFExecution) e)
				.findAny();
		if (identifiedSDF.isPresent() && orders.size() > 0) {
			SDFAndOrder identified = new SDFAndOrder();
			identified.sdfActors = identifiedSDF.get().sdfActors;
			identified.sdfChannels = identifiedSDF.get().sdfChannels;
			identified.sdfDelays = identifiedSDF.get().sdfDelays;
			identified.orders = orders;
			return identified;
		}
		else
			return null;
	}

	@Override
	public String getCoverRuleId() {
		// TODO Auto-generated method stub
		return null;
	}

}
