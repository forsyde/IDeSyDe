include "globals.mzn";

% objectives
enum objectives = {THROUGHPUT, LATENCY};

% model parameters
int: max_steps;

set of int: sdf_actors; % not flattened
set of int: sdf_channels;
set of int: procs;
set of int: comm_units;
set of int: units = procs union comm_units;

array[sdf_channels] of int: max_tokens;
array[sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_channels, sdf_actors] of int: sdf_topology;
array[sdf_actors, procs] of int: wcet;
% this numbers are 'per channel token'
array[sdf_channels, units] of int: token_wcct;
array[units] of set of int: units_neighs;


% objectives
array[objectives] of int: objective_weights;

% deduced model parameters
set of int: steps0 = 0..max_steps;
set of int: steps = 1..max_steps;

% variables
array[sdf_actors, procs, steps] of var 0..max(activations): mapped_actors;
array[procs, steps0] of var 0..sum(wcet): start;
array[procs, steps] of var 0..sum(wcet): busy_time;
array[sdf_channels, procs, steps0] of var 0..max(max_tokens): buffer;
array[sdf_channels, procs, procs, steps0, steps0] of var 0..max(max_tokens): send;
array[sdf_channels, units, units, steps0, steps0] of var 0..max(max_tokens): send_fabric;
array[sdf_channels, units, units, steps] of var 0..length(comm_units) * max(max_tokens) * max(token_wcct): send_duration;
%array[sdf_channels, units, units, steps] of var 0..token_wcct(token_wcct): send_time;
%array[sdf_channels, units, units] of var opt 0..max(max_tokens): send;

% objectives
array[procs, steps0] of var 0..sum(wcet): local_throughput;
array[procs] of var 0..sum(wcet): cpu_latency;
array[objectives] of var 0..2*sum(wcet): objective;

% tigthen bounds
constraint forall(c in sdf_channels, t in steps0, p in procs) (
  buffer[c, p, t] <= max_tokens[c]
);
constraint forall(c in sdf_channels, p,pp in procs, t in steps0) (
  send[c, p, pp, 0, t] = 0
);
constraint forall(c in sdf_channels, p,pp in procs) (
  send[c, p, pp, max_steps] = 0
);
constraint forall(c in sdf_channels, p, pp in procs, t in steps) (
  send[c, p, pp, t] <= max_tokens[c]
);
constraint forall(c in sdf_channels, p in procs, t in steps) (
  send[c, p, p, t] = 0
);
constraint forall(p in procs) (
  start[p, 0] = 0
);
constraint forall(p in procs, t in steps) (
  0 <= start[p, t] /\
  start[p, t] <= sum(wcet[.., p])
);
constraint forall(p in procs, t in steps) (
  local_throughput[p, t] <= sum(wcet[.., p])
);
constraint forall(c in sdf_channels, t in steps, p, pp in units) (
  send_fabric[c, p, pp, t] <= max_tokens[c]
);
constraint forall(c in sdf_channels, p in units, t in steps) (
  send_fabric[c, p, p, t] = 0
);
constraint forall(c in sdf_channels, p, pp in units, t in steps) (
  not(pp in units_neighs[p]) -> send_fabric[c, p, pp, t] = 0
);
constraint forall(c in sdf_channels, p, pp in units, t in steps) (
  not(pp in units_neighs[p]) -> send_duration[c, p, pp, t] = 0
);


% unable any communiation at the 'pre' start, or initial state

% unable any communication between elements
    
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
   + sum(pp in procs, tt in steps where tt) (send[c, pp, p, tt, t])
   - sum(pp in procs, tt in steps where tt) (send[c, p, pp, t, tt])
);

% sdf semantics: no buffer is ever negative
constraint forall(c in sdf_channels, t in steps0, p in procs) (
  buffer[c, p, t] >= 0
);

% platform semantics: communication elements never hold data
% constraint forall(c in sdf_channels, p in comm_units, t in steps) (
%   sum(pp in units_neighs[p]) (send[c, pp, p, t]) = sum(pp in units_neighs[p]) (send[c, p, pp, t])
% );
constraint forall(c in sdf_channels, t in steps) (
  network_flow(
    array2d(1..length(units)*length(units), 1..2,
      [if i mod 2 == 0 then pp else p endif | p, pp in units, i in 1..2]
    ),
    [0 | p in units],
    [send_fabric[c, p, pp, t] | p, pp in units]
  )
);

constraint forall(c in sdf_channels, p, pp in procs, t in steps) (
  send[c, p, pp, t] = sum(ps in units) (send_fabric[c, p, ps, t]) /\
  send[c, p, pp, t] = sum(ps in units) (send_fabric[c, ps, pp, t])
);

constraint forall(p in procs, t in steps) (
  busy_time[p, t] = sum(a in sdf_actors) (wcet[a, p] * mapped_actors[a, p, t])
);

%% monotonically increasing
constraint forall(p in procs, t in steps) (
    start[p, t] >= start[p, t-1]
);

%% timing between processing steps
constraint forall(t in steps, p in procs) (
  start[p, t] >= start[p, t-1] + busy_time[p, t]
);

%% timign with communication
% constraint forall(t in steps, p in units, pp in units_neighs[p]) (
%   start[p, t] >= start[pp, t] + sum(c in sdf_channels) (
%     wcct[c, pp, p] * send[c, pp, p, t]
%   )
% );

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
  local_throughput[p, 0] = 0
);
constraint forall(p in procs, t in steps) (
  sum(mapped_actors[.., p, t]) > 0 \/ local_throughput[p, t-1] > 0 ->
  local_throughput[p, t] = start[p, t] - start[p, t-1] + local_throughput[p, t-1]
);
constraint forall(p in procs, t in steps) (
  sum(mapped_actors[.., p, t]) = 0 /\ local_throughput[p, t-1] = 0 ->
  local_throughput[p, t] = 0
);

constraint objective[THROUGHPUT] = max(local_throughput);

constraint forall(p in procs) (
  cpu_latency[p] = start[p, max_steps]
);

constraint objective[LATENCY] = max(p in procs) (
  start[p, max_steps] + busy_time[p, max_steps]
);

solve
  :: int_search(mapped_actors, first_fail, indomain_max, complete)
  :: int_search(buffer, first_fail, indomain_min, complete)
  :: int_search(send, first_fail, indomain_min, complete)
  :: restart_luby(sum(activations) * length(procs))
  minimize sum(o in objectives) (objective_weights[o] * objective[o]);
