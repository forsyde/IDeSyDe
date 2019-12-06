import java.io.File;
import java.util.concurrent.Callable;

import forsyde.model.Converter;
import forsyde.model.ForSyDeDescription;
import io.InputFileTransformer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
name = "desyde",
mixinStandardHelpOptions = true,
description = "DeSyDe - ForSyDe's Analytical Design Space Exploration tool"
)
public class DeSyDeCLI implements Callable<Integer> {

	@Parameters(
	paramLabel = "FILES", 
	description = "Files used as the input model, including ForSyDe Models and Hardware Models." +
				  "\n\n" +
			      "Currently only amalthea description files containing HW models are supported." +
				  "\n"				  
	)
	File[] inputFiles;

	public static void main(String[] args) {
		int exitCode = new CommandLine(new DeSyDeCLI()).execute(args);
		System.exit(exitCode);
	}

	@Override
	public Integer call() throws Exception {
		if (inputFiles == null || inputFiles.length < 2) {
			System.out.println("At least two files are required: ForSyDe System Models and Hardware Description Models.");
			System.out.println((inputFiles == null ? 0 : inputFiles.length) + " files were supplied.");
		} else {
			InputFileTransformer inputFileTransformer = new InputFileTransformer(inputFiles);
			ForSyDeDescription f = inputFileTransformer.getForSyDeDescription();
			System.out.println(Converter.toJsonString(f));
			return 0;
		}
		return 0;
	}
}
