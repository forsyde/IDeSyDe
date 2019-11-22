package io.forsyde.parse;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

import forsyde.Function;
import forsyde.Process;

public interface Parser {
	
	void parse(Path p) throws IOException;
	
	Process getNetList();
	
	Map<String, Function> getFunctions();

}
