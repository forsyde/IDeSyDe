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
set of int: steps = 1..max_steps;

% variables
array[sdf_actors, procs, steps] of var 0..max(activations): mapped_actors;
array[procs, steps] of var 0..sum(wcet)+sum(token_wcct): start;
array[procs, steps] of var 0..sum(wcet): busy_time;
array[sdf_channels, procs, steps] of var 0..max(max_tokens): buffer_start;
array[sdf_channels, procs, steps] of var 0..max(max_tokens): buffer_end;
array[sdf_channels, procs, procs, steps, steps] of var 0..max(max_tokens): flow;
% array[sdf_channels, procs, procs, steps, steps] of var 0..length(comm_units) * max(max_tokens) * max(token_wcct): send_duration;
array[sdf_channels, procs, procs, steps, steps, comm_units] of var 0..max(max_tokens): send_allocation;
array[sdf_channels, procs, procs, steps, steps, comm_units] of var 0..length(comm_units) * max(max_tokens) * max(token_wcct): send_duration;
%array[sdf_channels, units, units, steps] of var 0..token_wcct(token_wcct): send_time;
%array[sdf_channels, units, units] of var opt 0..max(max_tokens): send;

% objectives
array[procs, steps] of var 0..sum(wcet): local_throughput;
array[objectives] of var 0..2*sum(wcet): objective;

% tigthen bounds
constraint forall(c in sdf_channels, p in procs, t in steps) (
  buffer_start[c, p, t] <= max_tokens[c] /\
  buffer_end[c, p, t] <= max_tokens[c] /\
  flow[c, p, p, t, t] = 0
);
constraint forall(c in sdf_channels, p, pp in procs, t, tt in steps) (
  flow[c, p, pp, t, tt] <= max_tokens[c]
);
constraint forall(c in sdf_channels, p in procs, t, tt in steps where tt != t + 1) (
  flow[c, p, p, t, tt] = 0
);
constraint forall(p in procs, t in steps) (
  0 <= start[p, t] /\
  start[p, t] <= sum(wcet[.., p]) /\
  local_throughput[p, t] <= sum(wcet[.., p])
);
constraint forall(c in sdf_channels, t, tt in steps, p, pp in procs, u in comm_units) (
  send_allocation[c, p, pp, t, tt, u] <= max_tokens[c] /\
  send_allocation[c, p, p, t, tt, u] = 0
);

% sdf semantics: repetion vector constraint
constraint forall(a in sdf_actors) (
  sum(mapped_actors[a, .., ..]) = activations[a]
);

constraint forall(c in sdf_channels, p in procs, t in steps) (
  buffer_start[c, p, t] +
  sum(a in sdf_actors) (sdf_topology[c, a]*mapped_actors[a, p, t]) =
  buffer_end[c, p, t]
);

constraint forall(c in sdf_channels, p in procs, t in steps) (
  buffer_start[c, p, t] = sum(pp in procs, tt in steps) (flow[c, pp, p, tt, t]) /\
  buffer_end[c, p, t] = sum(pp in procs, tt in steps) (flow[c, p, pp, t, tt])
);

% sdf semantics: final state
constraint forall(c in sdf_channels, p in procs, t in steps) (
  buffer_start[c, p, t] >= 0 /\
  buffer_end[c, p, t] >= 0
);

% sdf semantics: initial state
constraint forall(c in sdf_channels) (
  sum(buffer_start[c, .., min(steps)]) >= initial_tokens[c]
);

% sdf semantics: firing with communication
constraint forall(t in steps, c in sdf_channels, p in procs) (
  sum(pp in procs, tt in steps) (flow[c, pp, p, tt, t]) +
  sum(a in sdf_actors) (sdf_topology[c, a]*mapped_actors[a, p, t]) =
  sum(pp in procs, tt in steps) (flow[c, p, pp, t, tt])
);

% platform semantics: communication elements never hold data
% constraint forall(c in sdf_channels, p, pp in procs, t, tt in steps) (
%   network_flow(
%     array2d(1..length(comm_units)*length(comm_units), 1..2,
%       [if i mod 2 == 0 then uu else u endif | u, uu in comm_units, i in 1..2]
%     ),
%     [0 | u in comm_units],
%     [send_allocation[c, p, pp, t, tt, uu] - send_allocation[c, p, pp, t, tt, u] | u, uu in comm_units]
%   )
% );
constraint forall(c in sdf_channels, p, pp in procs, t, tt in steps, u in comm_units) (
  sum(uu in units_neighs[u] where uu in comm_units)
  (send_allocation[c, p, pp, t, tt, u] - send_allocation[c, p, pp, t, tt, uu]) =
  sum(uu in units_neighs[u] where uu in comm_units)
  (send_allocation[c, p, pp, t, tt, uu] - send_allocation[c, p, pp, t, tt, u])
);

constraint forall(c in sdf_channels, p, pp in procs, t, tt in steps where p != pp) (
  flow[c, p, pp, t, tt] = sum(u in comm_units where u in units_neighs[p]) 
  (send_allocation[c, p, pp, t, tt, u]) /\
  flow[c, p, pp, t, tt] = sum(u in comm_units where u in units_neighs[pp]) 
  (send_allocation[c, p, pp, t, tt, u])
);

constraint forall(p in procs, t in steps) (
  busy_time[p, t] = sum(a in sdf_actors) (wcet[a, p] * mapped_actors[a, p, t])
);

%% monotonically increasing
constraint forall(p in procs) (
  increasing(start[p, ..])
);

%% timing between processing steps
constraint forall(p in procs, t in min(steps)+1..max(steps)) (
  start[p, t] >= start[p, t-1] + busy_time[p, t]
);

constraint forall(c in sdf_channels, t, tt in steps, p, pp in procs, u in comm_units) (
  send_duration[c, p, pp, t, tt, u] >= token_wcct[c, u] * send_allocation[c, p, pp, t, tt, u]
);

%% timign with communication
%% TODO: the sum is conservative. Maybe a job view of the communication could
%% help later!
constraint forall(c in sdf_channels, t, tt in steps, p, pp in procs) (
  flow[c, p, pp, t, tt] > 0 ->
  start[pp, tt] >= start[p, t] + busy_time[p,t] + sum(send_duration[c, p, pp, t, tt, ..])
);

% symmetry breaking
% the "space-time cut" constraint, to be explained better later.
constraint forall(c in sdf_channels, t, tt in steps, p, pp in procs) (
  flow[c, p, pp, t, tt] > 0 ->
  forall(t2, tt2 in steps where t < t2 /\ tt2 < tt) (flow[c, p, pp, t2, tt2] = 0) /\
  forall(t2, tt2 in steps where t2 < t /\ tt < tt2) (flow[c, p, pp, t2, tt2] = 0) /\
  forall(t2, tt2 in steps where t < t2 /\ tt2 < tt) (flow[c, pp, p, tt2, t2] = 0) /\
  forall(t2, tt2 in steps where t2 < t /\ tt < tt2) (flow[c, pp, p, tt2, t2] = 0)
);
 
constraint forall(c in sdf_channels, t, tt in steps, p, pp in procs) (
  flow[c, p, pp, t, tt] > 0 ->
  sum(t2, tt2 in steps where t < t2 /\ tt2 < tt) (flow[c, p, pp, t2, tt2]) = 0 /\
  sum(t2, tt2 in steps where t2 < t /\ tt < tt2) (flow[c, p, pp, t2, tt2]) = 0 /\
  sum(t2, tt2 in steps where t < t2 /\ tt2 < tt) (flow[c, pp, p, tt2, t2]) = 0 /\
  sum(t2, tt2 in steps where t2 < t /\ tt < tt2) (flow[c, pp, p, tt2, t2]) = 0
);

%
constraint forall(t, tt in steps, p, pp in procs) (
  sum(c in sdf_channels) (flow[c, p, pp, t, tt]) >= 
  sum(c in sdf_channels, t2, tt2 in steps where t < t2 /\ tt2 < tt) (flow[c, p, pp, t2, tt2]) +
  sum(c in sdf_channels, t2, tt2 in steps where t2 < t /\ tt < tt2) (flow[c, p, pp, t2, tt2]) +
  sum(c in sdf_channels, t2, tt2 in steps where t < t2 /\ tt2 < tt) (flow[c, pp, p, tt2, t2]) +
  sum(c in sdf_channels, t2, tt2 in steps where t2 < t /\ tt < tt2) (flow[c, pp, p, tt2, t2])
);

% at least one slot must start from zero for symmetry breaking
constraint forall(p in procs) (
  count_geq([start[p, t] | t in steps, p in procs], 0, 1)
);
%% if next step is empty, all next ones are also empty
% constraint forall(p in procs, t in 1..max_steps-1) (
%   sum(mapped_actors[.., p, t]) = 0 -> sum(mapped_actors[.., p, t+1]) = 0
% );

% calculate the objectives
constraint forall(p in procs, t in steps) (
  local_throughput[p, t] >= busy_time[p, t]
);
constraint forall(p in procs, t in min(steps)+1..max(steps)) (
  local_throughput[p, t] >= busy_time[p, t] + local_throughput[p, t-1]
);
constraint forall(p in procs, t in min(steps)+1..max(steps)-1) (
  local_throughput[p, t] >= start[p, t+1] - (start[p, t-1] + busy_time[p, t-1])
);

constraint objective[THROUGHPUT] = max(local_throughput);

constraint objective[LATENCY] = max(p in procs) (
  start[p, max(steps)] + busy_time[p, max(steps)]
);

solve
  % :: warm_start(
  %      [mapped_actors[a, p, t] | a in sdf_actors, p in procs, t in steps],
  %      [if t <= activations[a mod p] then 1 else 0 endif |
  %       a in sdf_actors, p in procs, t in steps]
  %    )
  % :: int_search(mapped_actors, first_fail, indomain_max, complete)
  % :: int_search(buffer, first_fail, indomain_min, complete)
  % :: int_search(send, first_fail, indomain_min, complete)
  :: restart_luby(sum(activations) * length(procs))
  minimize sum(o in objectives) (objective_weights[o] * objective[o]);
