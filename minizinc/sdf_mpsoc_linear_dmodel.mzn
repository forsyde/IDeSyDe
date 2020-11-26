include "globals.mzn";

% model parameters-
int: max_bus_slots;
int: max_tokens;
int: max_steps;

set of int: sdf_actors; % not flattened
set of int: sdf_channels;
set of int: processing_units;

array[sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_channels, sdf_actors] of int: sdf_topology;
array[sdf_actors, processing_units] of int: wcet;
% this numbers are 'per channel token'
array[sdf_channels, processing_units, processing_units] of int: wcct;
array[sdf_channels, processing_units] of int: send_overhead;
array[sdf_channels, processing_units] of int: read_overhead;

% deduced model parameters
set of int: steps0 = 0..max_steps;
set of int: steps = 1..max_steps;
set of int: bus_slots = 1..max_bus_slots;

% variables
array[sdf_channels, processing_units, steps0] of var 0..max_tokens: buffer;
array[sdf_channels, processing_units, processing_units, bus_slots, steps0] of var 0..max_tokens: send;
array[sdf_actors, processing_units, steps] of var 0..max(activations): mapped_actors;
array[processing_units, steps0] of var 0..sum(wcet): cpu_time;

% objectives
array[processing_units] of var 0..sum(wcet): cpu_throughput;
var 0..sum(wcet): global_throughput;
array[processing_units] of var 0..sum(wcet): cpu_latency;
var 0..sum(wcet): global_latency;


% unable any communiation at the 'pre' time, or initial state
constraint sum(send[.., .., .., .., 0]) = 0;
  
% sdf semantics: repetion vector constraint
constraint forall(a in sdf_actors) (
  sum(mapped_actors[a, .., ..]) = activations[a]
);

% sdf semantics: final state
constraint forall(c in sdf_channels) (
  sum(buffer[c, .., 0]) = initial_tokens[c]
);

% sdf semantics: initial state
constraint forall(c in sdf_channels) (
  sum(buffer[c, .., max_steps]) = initial_tokens[c]
);

% sdf semantics: firing with communication
constraint forall(t in steps, c in sdf_channels, p in processing_units) (
  buffer[c, p, t] = sum(a in sdf_actors) (sdf_topology[c, a]*mapped_actors[a, p, t]) + buffer[c, p, t-1] 
   + sum(s in bus_slots, pp in processing_units where p != pp) (send[c, pp, p, s, t-1])
   - sum(s in bus_slots, pp in processing_units where p != pp) (send[c, p, pp, s, t])
);

% sdf semantics: no buffer is ever negative
constraint forall(c in sdf_channels, t in steps0, p in processing_units) (
  buffer[c, p, t] >= 0
);

% timing for the cpus
constraint forall(p in processing_units) (
    cpu_time[p, 0] = 0
);

constraint forall(t in steps, p in processing_units) (
  cpu_time[p, t] >= cpu_time[p, t-1] + sum(a in sdf_actors) (wcet[a, p] * mapped_actors[a, p, t])
    + sum(c in sdf_channels, pp in processing_units where p != pp) (
        send_overhead[c, p]*sum(send[c, p, pp, .., t])
      + read_overhead[c, p]*sum(send[c, pp, p, .., t-1])
      )
);

constraint forall(t in steps, p, pp in processing_units where p != pp) (
  cpu_time[p, t] >= cpu_time[pp, t-1] + sum(s in bus_slots, c in sdf_channels) (
    wcct[c, pp, p] * send[c, pp, p, s, t-1]
  )
);

% cannot use more than max bus slots per step
constraint forall(t in steps) (
  sum(c in sdf_channels, p, pp in processing_units, l in bus_slots) (send[c, p, pp, l, t] > 0) <= max_bus_slots
);


% constrain a channel to choose only and one only slot
constraint forall(c in sdf_channels, t in steps, l, ll in bus_slots where l != ll) (
  sum(send[c, .., .., l, t-1]) > 0 -> sum(send[c, .., .., ll, t-1]) = 0
);

% calculate the objectives
constraint forall(p in processing_units) (
  cpu_throughput[p] = cpu_time[p, max_steps] - cpu_time[p, 0]
);

constraint global_throughput = max(cpu_throughput);

constraint forall(p in processing_units) (
  cpu_latency[p] = cpu_time[p, max_steps]
);

constraint global_latency = max(cpu_latency);

solve 
  :: int_search(mapped_actors, first_fail, indomain_min, complete)
  :: int_search(buffer, first_fail, indomain_min, complete)
  :: int_search(send, first_fail, indomain_min, complete)
  satisfy;
