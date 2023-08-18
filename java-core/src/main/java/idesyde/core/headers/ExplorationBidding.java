package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;

@JsonSerialize
public record ExplorationBidding(@JsonProperty("explorer_unique_identifier") String explorerUniqueIdentifier,
		@JsonProperty("can_explore") Boolean canExplore, Map<String, Double> properties) {

	public boolean dominates(ExplorationBidding other) {
		for (var k : properties.keySet()) {
			if (!other.properties.containsKey(k) || other.properties.get(k) < properties.get(k)) {
				return false;
			}
		}
		return true;
	}
}
