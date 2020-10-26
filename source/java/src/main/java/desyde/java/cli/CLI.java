package desyde.java.cli;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.jpl7.Atom;
import org.jpl7.Compound;
import org.jpl7.Query;
import org.jpl7.Term;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import com.sun.tools.javac.util.List;

import desyde.java.prolog.jpl.JPLEngine;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

@Command(
		name = "DeSyDe(R)", 
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

    @Option(
		names = {"-p", "--prolog"}, 
		description = "How to connect to a prolog engine. It can be a string of the form 'cmd://<command>' or 'jpl'", 
		defaultValue = "jpl" 
	)
    private String prologEngine = "jpl";

	public static void main(String[] args) {
		try {
			JPLEngine engine = JPLEngine.getInstance();
			engine.singleQuery(Set.of("rules.pl"), "sdfActor(X)", null);
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
