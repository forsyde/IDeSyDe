package desyde.identification.rules;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import ForSyDe.Model.Application.ConstructedProcess;
import ForSyDe.Model.Application.SDFComb;
import ForSyDe.Model.Application.SDFPrefix;
import ForSyDe.Model.Application.Signal;
import ForSyDe.Model.Core.Vertex;
import ForSyDe.Model.IO.ForSyDeIO;
import desyde.identification.DecisionExpansionRule;
import desyde.identification.DecisionProblem;
import desyde.identification.problems.SDFExecution;

public class EmptyToSDFRule implements DecisionExpansionRule {

	@Override
	public DecisionProblem execute(ForSyDeIO model, Set<DecisionProblem> identifiedSet) {
		Set<ConstructedProcess> sdfActors = model.streamContained()
			.filter(e -> e instanceof ConstructedProcess)
			.map(e -> (ConstructedProcess) e)
			.filter(e -> e.constructor instanceof SDFComb)
			.collect(Collectors.toSet());
		Set<Signal> sdfChannels = model.streamContained()
				.filter(e -> e instanceof Signal)
				.map(e -> (Signal) e)
				.collect(Collectors.toSet());
		Set<ConstructedProcess> sdfDelays = model.streamContained()
				.filter(e -> e instanceof ConstructedProcess)
				.map(e -> (ConstructedProcess) e)
				.filter(e -> e.constructor instanceof SDFPrefix)
				.collect(Collectors.toSet());
		if (!sdfActors.isEmpty() && !sdfChannels.isEmpty() && !sdfDelays.isEmpty()) {
			SDFExecution identified = new SDFExecution();
			identified.sdfActors = sdfActors;
			identified.sdfChannels = sdfChannels;
			identified.sdfDelays = sdfDelays;
			return identified;
		} else
			return null;
	}

	@Override
	public String getCoverRuleId() {
		// TODO Auto-generated method stub
		return null;
	}

}
