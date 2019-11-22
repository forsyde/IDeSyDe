package io.forsyde.parse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import javax.json.Json;
import javax.json.JsonReader;
import javax.json.stream.JsonParser;
import javax.json.stream.JsonParser.Event;

import forsyde.Function;
import forsyde.Process;

public class ForSyDeJsonParser implements Parser {
	
	Process topProcess;
	Map<String, Function> functions;
	
	@Override
	public Process getNetList() {
		return topProcess;
	}
	
	@Override
	public Map<String, Function> getFunctions() {
		return functions;
	}
	
	@Override
	public void parse(Path p) throws IOException {
		final JsonParser jsonParser = Json.createParser(Files.newBufferedReader(p));
		String key = null;
		String value = null;
		while(jsonParser.hasNext()) {
			final Event event = jsonParser.next();
		}
	}
	
	private void parseApplications(JsonParser jp) {
		
	}
	
	private void parseFunctions(JsonParser jp) {
		
	}

}
