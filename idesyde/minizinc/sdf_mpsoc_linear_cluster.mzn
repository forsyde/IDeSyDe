include "globals.mzn";

% objectives
enum objectives = {THROUGHPUT, LATENCY};

% model parameters
int: max_steps;

set of int: sdf_actors; % not flattened
set of int: sdf_channels;
set of int: procs;
set of int: comms;
set of int: clusters;

array[sdf_channels] of int: max_tokens;
array[sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_channels, sdf_actors] of int: sdf_topology;
array[sdf_actors, procs] of int: wcet;
% this numbers are 'per channel token'
array[sdf_channels, comms] of int: token_wcct;
array[procs, procs, comms] of 0..length(comms): path;
array[comms] of int: comms_capacity;
% array[comms] of 


% objectives
array[objectives] of int: objective_weights;
array[objectives] of int: obj_min = [
  min(wcet), sum(wcet)
  ];
array[objectives] of int: obj_max = [
  sum(wcet), sum(wcet)+sum(token_wcct)
  ];

% deduced model parameters
set of int: steps = 1..max_steps;

% variables
array[sdf_actors, clusters] of var 0..max(activations): mapped_actors;
array[clusters, clusters] of var bool: successor;
array[sdf_actors, clusters] of var 0..max(activations): mapped_actors_accum;
array[clusters, procs] of var opt 0..sum(wcet): start_clusters_proc;
array[clusters, procs] of var 0..sum(wcet): dur_clusters_proc;
array[clusters] of var 0..sum(wcet): start_clusters;
array[clusters] of var 0..sum(wcet): dur_clusters;
array[sdf_channels, procs, procs, steps, steps] of var 0..max(max_tokens): flow;
% array[sdf_channels, procs, procs, steps, steps] of var 0..length(comms) * max(max_tokens) * max(token_wcct): send_duration;
% array[sdf_channels, procs, procs, steps, steps, comms] of var 0..max(max_tokens): send_comms;
array[sdf_channels, procs, procs, steps, steps, comms] of var 0..sum(wcet)+sum(token_wcct): send_start;
array[sdf_channels, procs, procs, steps, steps, comms] of var 0..length(comms) * max(max_tokens) * max(token_wcct): send_duration;

% objectives
array[procs, steps] of var 0..sum(wcet): local_throughput;
array[objectives] of var 0..2*sum(wcet): objective;

% tigthen bounds
% constraint forall(c in sdf_channels, t, tt in steps, p, pp in procs, u in comms) (
%   send_comms[c, p, pp, t, tt, u] <= max_tokens[c] /\
%   send_comms[c, p, p, t, tt, u] = 0
% );

% SDF
%% Repetetion vector
constraint forall(a in sdf_actors) (
  sum(mapped_actors[a, ..]) = activations[a]
);
%%% tight bound
constraint forall(a in sdf_actors, j in clusters) (
  mapped_actors[a, j] <= activations[a]
);

%% accumulation of activations
constraint forall(j, jj in clusters, a in sdf_actors where j != jj) (
  successor[j, jj] -> mapped_actors_accum[j] + mapped_actors[j] = mapped_actors_accum[jj]
);
%%% tight bound
constraint forall(j in clusters, a in sdf_actors) (
  mapped_actors_accum[a, j] <= activations[a]
);

%% always positive buffers
constraint forall(j in clusters) (
  sum(a in sdf_actors) (mapped_actors_accum[j] + mapped_actors[j]) >= 0
);

%% Initial state
% TODO

% Time
%% successor
constraint forall(j, jj in clusters where j != jj) (
  successor[j, jj] <-> start_clusters[j] + dur_clusters[j] <= start_clusters[jj]
);

%% WCET
constraint forall(j in clusters, p in procs) (
  dur_clusters_proc[j, p] = sum(a in sdf_actors) (mapped_actors[a, j] * wcet[a, p])
);

%% clusters can only go in one proc
constraint forall(j in clusters) (
  alernative(start_clusters[j], dur_clusters[j], start_clusters_proc[j, ..], dur_clusters_proc[j, ..])
);

%% disjunctive per proc
constraint forall(p in procs) (
  disjunctive(start_clusters_proc[.., p], dur_clusters_proc[.., p])
);

%% sucessor must follow
constraint forall(j, jj in clusters where j != jj) (
  success[j, jj] <-> start_clusters[j] + dur_clusters[j] <= start_clusters[jj]
);

%% implied disjunctive constraint
constraint forall(u in comms) (
  cumulative(
    start_clusters,
    dur_clusters,
    [1 | for j in clusters],
    length(procs)
  )
);

% symmetry breaking
% the "space-time cut" constraint, to be explained better later.
% at least one slot must start from zero for symmetry breaking
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
  :: warm_start(
       [mapped_actors[a, p, t] | a in sdf_actors, p in procs, t in steps],
       [if t <= activations[a mod p] then 1 else 0 endif |
        a in sdf_actors, p in procs, t in steps]
     )
  % :: int_search(mapped_actors, first_fail, indomain_max, complete)
  % :: int_search(buffer, first_fail, indomain_min, complete)
  % :: int_search(send, first_fail, indomain_min, complete)
  :: restart_luby(sum(activations) * length(procs))
  minimize sum(o in objectives) (objective_weights[o] * objective[o]);
