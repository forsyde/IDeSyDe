package desyde.java.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.concurrent.Callable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import ForSyDe.Model.IO.ForSyDeIO;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

@Command(
		name = "desyder", 
		mixinStandardHelpOptions = true, 
		version = "0.1.0",
		description = "ForSyDe's Design Space Exploration (Refactored) Tool"
		)
public class CLI implements Callable<Integer> {
	
    @Parameters(
    		index = "0",
			paramLabel = "<ForSyDeIO file>",
    		description = "The ForSyDe IO Model in XMI format to be processed."
    		)
    private File input;

    //@Option(names = {"-a", "--algorithm"}, description = "MD5, SHA-1, SHA-256, ...")
    //private String algorithm = "MD5";

	public static void main(String[] args) {
		try {
	        int exitCode = new CommandLine(new CLI()).execute(args);
	        System.exit(exitCode);
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public Integer call() throws Exception {
		String outputName = "augmented-" + input.getName();
		File outputFile = new File(outputName);
		return 0;
	}

}
