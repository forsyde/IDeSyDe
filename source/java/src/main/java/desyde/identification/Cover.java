package desyde.identification;

import ForSyDe.Model.IO.ForSyDeIO;

public abstract class Cover {
	
	ForSyDeIO model;
	ForSyDeIO flattenedModel;

	public Boolean isSolvable() {
		return false;
	}
	
	public abstract ForSyDeIO solve();
}
