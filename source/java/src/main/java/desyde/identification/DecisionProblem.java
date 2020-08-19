package desyde.identification;

import ForSyDe.Model.IO.ForSyDeIO;

public abstract class DecisionProblem {
	
	ForSyDeIO model;

	public Boolean isSolvable() {
		return false;
	}
	
	public abstract ForSyDeIO solve();
}
