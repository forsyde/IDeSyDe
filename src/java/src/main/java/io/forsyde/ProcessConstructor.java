package io.forsyde;

import java.util.Map;

public abstract class ProcessConstructor {

	Map<String, ProcessConstructor> childProcessConstructors;
	Map<String, ?> parameters;
	Map<String, ?> arguments;

}
