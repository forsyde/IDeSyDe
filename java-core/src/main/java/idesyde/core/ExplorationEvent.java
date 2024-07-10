package idesyde.core;

import java.util.Optional;

public record ExplorationEvent(
    Optional<ExplorationSolution> solution,
    Boolean optimalityProved
) {

}
