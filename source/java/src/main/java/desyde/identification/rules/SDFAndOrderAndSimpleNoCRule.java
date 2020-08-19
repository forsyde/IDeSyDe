package desyde.identification.rules;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import ForSyDe.Model.IO.ForSyDeIO;
import ForSyDe.Model.Platform.ComputationalTile;
import ForSyDe.Model.Platform.TimeDivisionMultiplexer;
import desyde.identification.DecisionExpansionRule;
import desyde.identification.DecisionProblem;
import desyde.identification.problems.SDFAndOrder;
import desyde.identification.problems.SDFExecution;
import desyde.identification.problems.SDFInSimpleNoC;

public class SDFAndOrderAndSimpleNoCRule implements DecisionExpansionRule {

	@Override
	public DecisionProblem execute(ForSyDeIO model, Set<DecisionProblem> identifiedSet) {
		Set<ComputationalTile> tiles = model.streamContained()
				.filter(e -> e instanceof ComputationalTile)
				.map(e -> (ComputationalTile) e)
				.collect(Collectors.toSet());
		Set<TimeDivisionMultiplexer> tdms = model.streamContained()
				.filter(e -> e instanceof TimeDivisionMultiplexer)
				.map(e -> (TimeDivisionMultiplexer) e)
				.collect(Collectors.toSet());
		Optional<SDFAndOrder> identifiedSDFAndOrder = identifiedSet.stream()
				.filter(e -> e instanceof SDFExecution)
				.map(e -> (SDFAndOrder) e)
				.findAny();
		if (identifiedSDFAndOrder.isPresent() && tiles.size() > 0 && tdms.size() > 0) {
			SDFInSimpleNoC identified = new SDFInSimpleNoC();
			identified.sdfActors = identifiedSDFAndOrder.get().sdfActors;
			identified.sdfChannels = identifiedSDFAndOrder.get().sdfChannels;
			identified.sdfDelays = identifiedSDFAndOrder.get().sdfDelays;
			identified.orders = identifiedSDFAndOrder.get().orders;
			identified.tiles = tiles;
			identified.tdms = tdms;
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
