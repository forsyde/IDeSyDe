package idesyde.core.headers;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;

import java.util.Map;
import java.util.Set;

@JsonSerialize
public record ExplorationBidding(
		@JsonProperty("explorer_unique_identifier") String explorerUniqueIdentifier,
		@JsonProperty("can_explore") Boolean canExplore,
		@JsonProperty("is_complete") Boolean isComplete,
		Double competitiveness,
		@JsonProperty("target_objectives") Set<String> targetObjectives,
		@JsonProperty("additional_numeric_properties") Map<String, Double> additionalNumericProperties) {

	public boolean dominates(ExplorationBidding other) {
		for (var k : additionalNumericProperties.keySet()) {
			if (!other.additionalNumericProperties.containsKey(k)
					|| other.additionalNumericProperties.get(k) < additionalNumericProperties.get(k)) {
				return false;
			}
		}
		return true;
	}
}
