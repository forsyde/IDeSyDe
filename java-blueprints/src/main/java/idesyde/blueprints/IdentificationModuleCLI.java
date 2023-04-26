package idesyde.blueprints;


import picocli.CommandLine;

import java.nio.file.Path;

@CommandLine.Command(mixinStandardHelpOptions = true)
public class IdentificationModuleCLI {
    @CommandLine.Option(names = {"m", "design-path"}, description = "The path where the design models (and headers) are stored.")
    Path designPath;
    @CommandLine.Option(names = {"i", "identified-path"}, description = "The path where identified decision models (and headers) are stored.")
    Path identifiedPath;
    @CommandLine.Option(names = {"s", "solved-path"}, description = "The path where explored decision models (and headers) are stored.")
    Path solvedPath;
    @CommandLine.Option(names = {"r", "reverse-path"}, description = "The path where reverse identified design models (and headers) are stored.")
    Path reversePath;
    @CommandLine.Option(names = {"o", "output-path"}, description = "The path where final integrated design models are stored, in their original format.")
    Path outputPath;
    @CommandLine.Option(names = {"t", "identification-step"}, description = "The overall identification iteration number.")
    int identStep;
}
