/**
 * 
 */
package io.forsyde;

import java.util.Map;

/**
 * @author rjordao
 *
 */
public class Process {

	Map<String, Process> childProcesses;
	ProcessConstructor constructor;
	Map<String, Signal> inputs;
	Map<String, Signal> outputs;

}
