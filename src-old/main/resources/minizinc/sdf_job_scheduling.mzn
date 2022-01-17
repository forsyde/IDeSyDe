include "globals.mzn";

% objectives
enum objectives = {MIN_LATENCY, MAX_LATENCY};

% model parameters
set of int: procs;
set of int: comms;
set of int: jobs;

array[jobs] of opt int: release;
array[jobs] of opt int: deadline;
array[jobs, jobs] of bool: next;
array[jobs, procs] of int: wcet;
array[jobs, jobs, comms] of int: wcct;
array[proc, procs, comms] of int: path;
array[comms] of int: comm_capacity;

% extra model parameters
set of set of procs: topologically_symmetric;

% objectives
array[objectives] of int: objective_weights;

% variables
array[jobs] of var 0..sum(wcet): job_start;
array[jobs] of var min(wcet)..max(wcet): job_duration;
array[jobs, procs] of var opt 0..sum(wcet): start;
array[jobs, procs] of var int: duration;
array[jobs, jobs] of var 0..sum(wcet): comm_job_start;
array[jobs, jobs] of var 0..max(wcct): comm_job_duration;
array[jobs, jobs, comm] of var opt 0..sum(wcct): comm_start;
array[jobs, jobs, comm] of var int: comm_duration;

% objectives
int: max_makespan = sum(wcet)+sum(wcct)
array[jobs] of var max(wcet)..max_makespan: job_min_latency;
array[jobs] of var sum(wcet)/length(procs)..max_makespan: job_max_latency;
array[objectives] of var max(wcet)..max_makespan: objective;

% tighten bounds for faster exploration
constraint forall(j in jobs, p in procs) (
  duration[j, p] = wcet[j, p]
);
constraint forall(j, jj in jobs, p in comms) (
  comm_duration[j, jj, p] <= wcct[j, jj, p]
);
constraint forall(j in jobs) (
  occurs(release[j]) -> release[j] <= job_start[j]  
);
constraint forall(j in jobs) (
  occurs(deadline[j]) -> job_start[j] <= deadline[j]
);

% job level assertions
constraint forall(j, jj in jobs where j != jj) (
  next[j, jj] -> job_start[j] + job_duration[j] <= job_start[jj]
);
constraint forall(j, jj in jobs where next[j, jj]) (
  job_start[j] + job_duration[jj] + sum(comm_job_duration[j, jj, ..]) <= job_start[jj]
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
constraint forall(p, pp in procs, j, jj in jobs, u, uu in path[p, pp] where next[j, jj]) (
  path[p, pp, u] = path[p, pp, uu] - 1 ->
  comm_start[j, jj, u] + comm_duration[j, jj, u] <= comm_start[j, jj, uu]
);
constraint forall(p, pp in procs, j, jj in jobs where next[j, jj]) (
  occurs(start[j, p]) /\ occurs(start[jj, pp] -> (
    p != pp -> forall(u in path[p, pp]) (comm_duration[j, jj, u] = wcct[j, jj, u]) /\
    p = pp -> forall(u in path[p, pp]) (comm_duration[j, jj, u] = 0)
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
constraint forall(j, jj in jobs where next[j, jj]) (
  job_throughput[j] = job_throughput[jj] + job_start[jj] - job_start[j]
);
constraint forall(j in jobs) (
  job_throughput[j] >= job_duration[j]
);
constraint objective[MIN_LATENCY] = max(j in jobs) (job_throughput[j]);

constraint objective[MAX_LATENCY] = max(j in jobs) (job_start[j] + job_duration[j]);

constraint objective[MIN_LATENCY] <= MIN_LATENCY[MAX_LATENCY];

solve
  :: warm_start(start, [i mod length(procs) | i in jobs])
  :: int_search(job_start, first_fail, indomain_min)
  :: restart_luby(length(jobs) * length(units))
  minimize max(o in objectives) (objective_weights[o] * objective[o]);
