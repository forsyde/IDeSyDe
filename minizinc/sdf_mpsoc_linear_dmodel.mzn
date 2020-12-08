include "globals.mzn";

% objectives
enum objectives = {THROUGHPUT, LATENCY};

% model parameters
int: max_tokens;
int: max_steps;

set of int: sdf_actors; % not flattened
set of int: sdf_channels;
set of int: procs;
set of int: comm_units;
set of int: units = procs union comm_units;

array[sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_channels, sdf_actors] of int: sdf_topology;
array[sdf_actors, procs] of int: wcet;
% this numbers are 'per channel token'
array[sdf_channels, units, units] of int: wcct;
array[units] of set of int: units_neighs;
% array[sdf_channels, procs] of int: send_overhead;
% array[sdf_channels, procs] of int: read_overhead;

% objectives
array[objectives] of int: objective_weights;

% deduced model parameters
set of int: steps0 = 0..max_steps;
set of int: steps = 1..max_steps;

% variables
array[sdf_channels, procs, steps0] of var 0..max_tokens: buffer;
array[sdf_channels, units, units, steps0] of var 0..max_tokens: send;
array[sdf_actors, procs, steps] of var 0..max(activations): mapped_actors;
array[units, steps0] of var 0..sum(wcet): time;

% objectives
array[procs] of var 0..sum(wcet): cpu_throughput;
array[procs] of var 0..sum(wcet): cpu_latency;
array[objectives] of var int: objective;

% unable any communiation at the 'pre' time, or initial state
constraint forall(c in sdf_channels, p,pp in units) (
    send[c, p, pp, 0] = 0
);

% unable any communication between elements
constraint forall(c in sdf_channels, p in units, t in steps) (
    send[c, p, p, t] = 0
);

% unable any communication between non neighbors
constraint forall(c in sdf_channels, p, pp in units, t in steps where not (pp in units_neighs[p])) (
    send[c, p, pp, t] = 0
);

    
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
constraint forall(t in steps, c in sdf_channels, p in procs) (
  buffer[c, p, t] = sum(a in sdf_actors) (sdf_topology[c, a]*mapped_actors[a, p, t]) + buffer[c, p, t-1] 
   + sum(pp in units_neighs[p]) (send[c, pp, p, t])
   - sum(pp in units_neighs[p]) (send[c, p, pp, t])
);

% sdf semantics: no buffer is ever negative
constraint forall(c in sdf_channels, t in steps0, p in procs) (
  buffer[c, p, t] >= 0
);

% platform semantics: communication elements never hold data
constraint forall(c in sdf_channels, p in comm_units, t in steps) (
  sum(pp in units_neighs[p]) (send[c, pp, p, t]) = sum(pp in units_neighs[p]) (send[c, p, pp, t])
);

% timing for the elements
%% must start at zero
constraint forall(p in units) (
    time[p, 0] = 0
);

%% timing between processing steps
constraint forall(t in steps, p in procs) (
  time[p, t] >= time[p, t-1] + sum(a in sdf_actors) (wcet[a, p] * mapped_actors[a, p, t])
);

%% timign with communication
constraint forall(t in steps, p in units, pp in units_neighs[p]) (
  time[p, t] >= time[pp, t-1] + sum(c in sdf_channels) (
    wcct[c, pp, p] * send[c, pp, p, t]
  )
);

% constrain a channel to choose only and one only slot
% constraint forall(c in sdf_channels, t in steps, l, ll in bus_slots where l != ll) (
%   sum(send[c, .., .., l, t-1]) > 0 -> sum(send[c, .., .., ll, t-1]) = 0
% );

% symmetry breaking
%% if next step is empty, all next ones are also empty
% constraint forall(p in procs, t in 1..max_steps-1) (
%   sum(mapped_actors[.., p, t]) = 0 -> sum(mapped_actors[.., p, t+1]) = 0
% );

% calculate the objectives
constraint forall(p in procs) (
  cpu_throughput[p] = time[p, max_steps] - time[p, 0]
);

constraint objective[THROUGHPUT] = max(cpu_throughput);

constraint forall(p in procs) (
  cpu_latency[p] = time[p, max_steps]
);

constraint objective[LATENCY] = max(cpu_latency);

solve 
  :: int_search(mapped_actors, first_fail, indomain_min, complete)
  :: int_search(buffer, first_fail, indomain_min, complete)
  :: int_search(send, first_fail, indomain_min, complete)
  minimize sum(o in objectives) (objective_weights[o] * objective[o]);
