include "globals.mzn";

% objectives
enum objectives = {MIN_LATENCY, MAX_LATENCY};

% model parameters
set of int: procs;
set of int: comms;
set of int: jobs;

array[jobs] of int: release;
array[jobs] of int: deadline;
array[jobs, jobs] of bool: weak_next;
array[jobs, jobs] of bool: strong_next;
array[jobs, procs] of int: wcet;
array[jobs, jobs, comms] of int: wcct;
array[procs, procs, comms] of int: path;
array[comms] of int: comm_capacity;

% extra model parameters
% set of set of procs: topologically_symmetric;
int: max_makespan = max(p in procs) (sum(j in jobs) (wcet[j, p])) +
  max(u in comms) (sum(j, jj in jobs) (wcct[j, jj, u]));

% objectives
array[objectives] of int: objective_weights;

% variables
array[jobs] of var 0..max_makespan: job_start;
array[jobs] of var min(wcet)..max(wcet): job_duration;
array[jobs, procs] of var opt 0..max_makespan: start;
array[jobs, procs] of var 0..max(wcet): duration;
array[jobs, jobs] of var 0..max_makespan: comm_job_start;
array[jobs, jobs] of var 0..max(wcct): comm_job_duration;
array[jobs, jobs, comms] of var opt 0..max_makespan: comm_start;
array[jobs, jobs, comms] of var 0..max(wcct): comm_duration;

% objectives
array[jobs] of var max(wcet)..max_makespan: job_min_latency;
array[jobs] of var max_makespan/length(procs)..max_makespan: job_max_latency;
array[objectives] of var max(wcet)..max_makespan: objective;

% tighten bounds for faster exploration
constraint forall(j in jobs, p in procs) (
  duration[j, p] = wcet[j, p]
);
constraint forall(j, jj in jobs, p in comms) (
  comm_duration[j, jj, p] <= wcct[j, jj, p]
);
constraint forall(j in jobs) (
  release[j] > 0 -> release[j] <= job_start[j]  
);
constraint forall(j in jobs) (
  deadline[j] > 0 -> job_start[j] <= deadline[j]
);

% job level assertions
constraint forall(j, jj in jobs where j != jj) (
  weak_next[j, jj] -> job_start[j] <= job_start[jj]
);
constraint forall(j, jj in jobs where j != jj) (
  strong_next[j, jj] -> job_start[j] + job_duration[j] <= job_start[jj]
);
constraint forall(j, jj in jobs where strong_next[j, jj]) (
  job_start[j] + job_duration[jj] + sum(comm_duration[j, jj, ..]) <= job_start[jj]
);

% processors can only run one job at a time
constraint forall(j in jobs) (
  alternative(job_start[j], job_duration[j], start[j, ..], duration[j, ..])
);
% processors only run disjunctive
constraint forall(p in procs) (
  disjunctive(start[.., p], wcet[.., p])
);
% inferred constraint
constraint cumulative(job_start, job_duration, [1|i in jobs], length(procs));

% communication concerns
constraint forall(p, pp in procs, j, jj in jobs, u, uu in comms where strong_next[j, jj]) (
  path[p, pp, u] = path[p, pp, uu] - 1 ->
  comm_start[j, jj, u] + comm_duration[j, jj, u] <= comm_start[j, jj, uu]
);
constraint forall(p, pp in procs, j, jj in jobs where strong_next[j, jj]) (
  occurs(start[j, p]) /\ occurs(start[jj, pp]) -> (
    p != pp -> forall(u in comms where path[p, pp, u] > 0) (comm_duration[j, jj, u] = wcct[j, jj, u]) /\
    p = pp -> forall(u in comms where path[p, pp, u] > 0) (comm_duration[j, jj, u] = 0)
  )
);
constraint forall(p in comms) (
 cumulative(
   [comm_start[j, jj, p] | j, jj in jobs where j != jj],
   [comm_duration[j, jj, p] | j, jj in jobs where j != jj],
   [1 | j, jj in jobs where j != jj],
   comm_capacity[p]
 )
);

%objectives
constraint forall(j in jobs, p in procs) (
  job_min_latency[j] >= 
  max(jj in jobs where j != jj /\ occurs(start[j, p]) /\ occurs(start[jj, p])) 
  (start[jj, p] - duration[j, p] - start[j, p])
);
constraint forall(j, jj in jobs where strong_next[j, jj] \/ weak_next[j, jj]) (
  job_min_latency[j] >= job_min_latency[jj]
);
constraint forall(j in jobs) (
  job_min_latency[j] >= job_duration[j]
);
constraint objective[MIN_LATENCY] = max(j in jobs) (job_min_latency[j]);

constraint objective[MAX_LATENCY] = max(j in jobs) (job_start[j] + job_duration[j]);

constraint objective[MIN_LATENCY] <= objective[MAX_LATENCY];


solve
  % :: warm_start(start, [i mod length(procs) | i in jobs])
  :: int_search(job_start, first_fail, indomain_min)
  :: restart_luby(length(jobs))
  minimize max(o in objectives) (objective_weights[o] * objective[o]);

output [show(max_makespan), "\n", show(objective), "\n", show2d(start), "\n", show(job_min_latency)];
