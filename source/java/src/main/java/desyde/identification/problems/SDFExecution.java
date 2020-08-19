package desyde.identification.problems;

import java.util.Set;

import ForSyDe.Model.Application.ConstructedProcess;
import ForSyDe.Model.Application.Signal;
import ForSyDe.Model.Core.Vertex;
import ForSyDe.Model.IO.ForSyDeIO;
import desyde.identification.DecisionProblem;

public class SDFExecution extends DecisionProblem {
	
	public Set<ConstructedProcess> sdfActors;
	public Set<Signal> sdfChannels;
	public Set<ConstructedProcess> sdfDelays;

	@Override
	public ForSyDeIO solve() {
		// TODO Auto-generated method stub
		return null;
	}

}
