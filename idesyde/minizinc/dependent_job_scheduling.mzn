include "globals.mzn";

% objectives
enum objectives = {LATENCY_MIN, LATENCY_MAX};

% model parameters
set of int: procs;
set of int: comms;
set of int: jobs;

array[jobs] of int: release;
array[jobs] of int: deadline;
array[jobs] of (procs union {0}): pre_mapping;
array[jobs] of int: pre_scheduling;
array[jobs, jobs] of bool: weak_next;
array[jobs, jobs] of bool: strong_next;
array[jobs, procs] of int: wcet;
array[jobs, jobs, comms] of int: wcct;
array[procs, procs, comms] of int: path;
array[comms] of int: comm_capacity;
array[jobs, procs] of bool: allowed_mapping;

% extra model parameters
% set of set of procs: topologically_symmetric;
% the +1 comes from the fact jobs withotu duration should also be considered
% in reality, so make the case for when the WCET is totally zero
int: max_makespan = max(p in procs) (sum(j in jobs) (wcet[j, p] + 1));

% objectives
array[objectives] of int: objective_weights;

% variables
array[jobs] of var 0..max_makespan: job_start;
array[jobs] of var min(wcet)..max(wcet): job_duration;
array[jobs, procs] of var opt 0..max_makespan: start;
array[jobs, procs] of var min(wcet)..max(wcet): duration;
array[jobs, jobs, comms] of var 0..max_makespan: comm_start;
array[jobs, jobs, comms] of var min(wcct)..max(wcct): comm_duration;

% objectives
array[jobs] of var max(wcet)..max_makespan: job_min_latency;
array[jobs] of var max(wcet)..max_makespan: job_max_latency;
array[objectives] of var max(wcet)..max_makespan: objective;

% apply pre mapping
constraint forall(j in jobs, p in procs) (
  pre_mapping[j] = p -> occurs(start[j, p]) /\ forall(pp in procs where p != pp) (not(occurs(start[j, pp])))
);

% apply pre scheduling
constraint forall(j in jobs) (
  pre_scheduling[j] > 0 -> job_start[j] = pre_scheduling[j]
);

% tighten bounds for faster exploration
constraint forall(j in jobs, p in procs) (
  duration[j, p] = wcet[j, p]
);
constraint forall(j, jj in jobs, p in comms where j != jj) (
  comm_duration[j, jj, p] = wcct[j, jj, p]
);
constraint forall(j in jobs, p in comms) (
  comm_duration[j, j, p] = 0
);
constraint forall(j in jobs, p in procs) (
  not(allowed_mapping[j, p]) -> start[j, p] = <>
);
constraint forall(j in jobs where release[j] > 0) (
  release[j] <= job_start[j]  
);
constraint forall(j in jobs where deadline[j] > 0) (
  job_start[j] <= deadline[j]
);

% job level assertions
constraint forall(j, jj in jobs where weak_next[j, jj]) (
  job_start[j] <= job_start[jj]
);
constraint forall(j, jj in jobs where strong_next[j, jj]) (
  job_start[j] + job_duration[j] <= job_start[jj]
);

% processors can only run one job at a time
constraint forall(j in jobs) (
  alternative(job_start[j], job_duration[j], start[j, ..], duration[j, ..])
);
% processors only run disjunctive
constraint forall(p in procs) (
  disjunctive(start[.., p], wcet[.., p])
);
% even if jobs have zero duration by input, they should always start at different times
constraint forall(p in procs) (
  disjunctive(start[.., p], [1 | j in jobs])
);
% inferred constraint
constraint cumulative(job_start, job_duration, [1|i in jobs], length(procs));

% communication concerns
constraint forall(p, pp in procs, j, jj in jobs, u, uu in comms where strong_next[j, jj]) (
  path[p, pp, u] > 0 /\ path[p, pp, u] = path[p, pp, uu] - 1 /\ occurs(start[j, p]) /\ occurs(start[jj, pp]) ->
  comm_start[j, jj, u] + comm_duration[j, jj, u] <= comm_start[j, jj, uu]
);
% constraint forall(p, pp in procs, j, jj in jobs, u in comms where strong_next[j, jj] /\ path[p, pp, u] > 0) (
%   occurs(start[j, p]) /\ occurs(start[jj, pp]) -> 
%   start[j, p] + duration[j, p] <= comm_start[j, jj, u]
% );
constraint forall(j, jj in jobs, u in comms where strong_next[j, jj]) (
  job_start[j] + job_duration[j] <= comm_start[j, jj, u]
);
constraint forall(j, jj in jobs, u in comms where strong_next[j, jj]) (
  comm_start[j, jj, u] <= job_start[jj] + job_duration[jj]
);
constraint forall(p, pp in procs, j, jj in jobs, u in comms where strong_next[j, jj] /\ path[p, pp, u] > 0) (
  % occurs(start[j, p]) /\ occurs(start[jj, pp]) -> 
  start[j, p] + sum(u in comms where path[p, pp, u] > 0) (comm_duration[j, jj, u]) <= start[jj, pp]
);
constraint forall(p in comms) (
 cumulative(
   [comm_start[j, jj, p] | j, jj in jobs where strong_next[j, jj]],
   [comm_duration[j, jj, p] | j, jj in jobs where strong_next[j, jj]],
   [1 | j, jj in jobs where strong_next[j, jj]],
   comm_capacity[p]
 )
);

%objectives
constraint forall(j, jj in jobs, p in procs) (
  occurs(start[j, p]) /\ occurs(start[jj, p]) -> 
  job_min_latency[j] >= job_duration[jj] + job_start[jj] - job_start[j]
);
constraint forall(j, jj in jobs where strong_next[j, jj] \/ weak_next[j, jj]) (
  job_min_latency[j] >= job_min_latency[jj]
);
constraint forall(j in jobs) (
  job_min_latency[j] >= job_duration[j]
);
constraint forall(j in jobs) (
  job_start[j] + job_duration[j] <= job_max_latency[j]
);
constraint objective[LATENCY_MIN] = max(job_min_latency);

constraint objective[LATENCY_MAX] = max(job_max_latency);

constraint objective[LATENCY_MIN] <= objective[LATENCY_MAX];


solve
  % :: warm_start(start, [i mod length(procs) | i in jobs])
  :: int_search(job_start, first_fail, indomain_min)
  :: int_search([comm_start[j, jj, u] | j, jj in jobs, u in comms], first_fail, indomain_min)
  :: restart_luby(length(jobs))
  minimize max(o in objectives) (objective_weights[o] * objective[o]);

% output [show(max_makespan), "\n", show(objective), "\n", show2d(start), "\n", show(job_min_latency)];
