include "globals.mzn";

% objectives
enum objectives = {THROUGHPUT, LATENCY};

% model parameters
int: max_tokens;

set of int: sdf_actors;
set of int: sdf_channels;
set of int: procs;
set of int: comm_units;
set of int: units = procs union comm_units;

set of int: jobs;

array[jobs] of sdf_actors: jobs_actors;
array[sdf_channels] of int: initial_tokens;
array[sdf_channels, sdf_actors] of int: sdf_topology;
array[sdf_actors, procs] of int: wcet;
% this numbers are 'per channel token'
array[sdf_channels, units, units] of int: wcct;
array[units] of set of int: units_neighs;
% array[sdf_channels, procs] of int: send_overhead;
% array[sdf_channels, procs] of int: read_overhead;

% objectives
array[objectives] of int: objective_weights;

% variables
array[jobs] of var procs: mapping;
array[jobs, procs] of var opt 0..(sum(wcet)+sum(wcct)): start;
array[jobs] of var 0..(sum(wcet)+sum(wcct)): job_start;
array[jobs] of var min(wcet)..max(wcet): job_duration;
array[jobs, procs] of var int: duration;
array[sdf_channels, jobs] of var 0..max_tokens: buffer_end;
array[sdf_channels, jobs] of var 0..max_tokens: buffer_start;
array[sdf_channels, jobs, jobs] of var 0..max_tokens: job_send;
array[jobs, jobs, units, units] of var bool: transfers;
% array[jobs, jobs, comm_units] of var opt 0..(sum(wcet)+sum(wcct)): start_transf;
% array[jobs, jobs, comm_units] of var int: dur_transf;

% objectives
array[procs] of var 0..sum(wcet)+sum(wcct): cpu_latency;
array[objectives] of var 0..2*sum(wcet)+sum(wcct): objective;

% tighten bounds for faster exploration
constraint forall(j in jobs, p in procs) (
  duration[j, p] = wcet[jobs_actors[j], p]
);

% constraint forall(j, jj in jobs, p in comm_units where j != jj) (
%   max_tokens * min(c in sdf_channels, pp in units where p != pp) (wcct[c, p, pp]) <= dur_transf[j, jj, p] /\
%   dur_transf[j, jj, p] <= max(c in sdf_channels, pp in units where p != pp) (wcct[c, p, pp]) * max_tokens
% );

constraint forall(j, jj in jobs, p, pp in units where j != jj) (
  not(pp in units_neighs[p]) -> 
  transfers[j, jj, p, pp] = false /\
  transfers[j, jj, pp, p] = false
);
constraint forall(j in jobs, p, pp in units) (
  transfers[j, j, p, pp] = false
);
constraint forall(j, jj in jobs, p in units) (
  transfers[j, jj, p, p] = false
);


constraint forall(j in jobs) (
  min(wcet[jobs_actors[j], ..]) <= job_duration[j] /\
  job_duration[j] <= max(wcet[jobs_actors[j], ..])
);

%sdf semantics
constraint forall(c in sdf_channels, j in jobs) (
  buffer_start[c, j] = sum(jj in jobs where j != jj) (job_send[c, jj, j] + buffer_end[c, jj])
);

constraint forall(c in sdf_channels, j in jobs) (
  buffer_end[c, j] = sdf_topology[c, jobs_actors[j]] + buffer_start[c, j]
                   - sum(jj in jobs where j != jj) (job_send[c, j, jj])
);


% transfer semantics
%% if two jobs communicate from two different processing units,
%% force them to use at least one of the neighboring hw units, i.e.
%% force the model to recognize they are communicating
constraint forall(j, jj in jobs, c in sdf_channels, p, pp in procs where p != pp /\ j != jj) (
  occurs(start[j, p]) /\ occurs(start[jj, pp]) <->
  transfers[j, jj, p, pp]
);

%% the paths between units must be connected
constraint forall(c in sdf_channels, j, jj in jobs, p, pp, ppp in units where j != jj /\ p != pp /\ pp != ppp) (
  transfers[j, jj, p, pp] /\ transfers[j, jj, pp, ppp] <->
  transfers[j, jj, p, ppp]
);

% timing semantics
constraint forall(j, jj in jobs where j != jj) (
  sum(job_send[.., j, jj]) > 0 -> job_start[j] + job_duration[j] <= job_start[jj]
);

% constraint forall(c in sdf_channels, j, jj in jobs, p, pb in units where j != jj /\ p != pb) (
%   transfers[j, jj, pb, p] -> 
%   start_transf[j, jj, pb] + dur_transf[j, jj, pb] <= start_transf[j, jj, p]
% );

constraint forall(j in jobs, p in procs) (
  mapping[j] = p <-> occurs(start[j, p])
);
% processors can only run one job at a time
constraint forall(j in jobs) (
  alternative(job_start[j], job_duration[j], start[j, ..], duration[j, ..])
);
constraint forall(p in procs) (
  disjunctive(start[.., p], [wcet[jobs_actors[j], p] | j in jobs])
);
% inferred constraint
constraint cumulative(job_start, job_duration, [1|i in jobs], length(procs));
% communications at each place happen only once
% constraint forall(p in procs) (
%   disjunctive(c in sdf_channels, j, jj in jobs where j != jj)
%   (send_start[c, j, jj, p], wcct[c, jobs_actors[j], p] | j in jobs])
% );

%objectives
constraint objective[THROUGHPUT] = max(p in procs) (
  max(j in jobs where mapping[j] = p) (job_start[j] + job_duration[j]) -
  min(j in jobs where mapping[j] = p) (job_start[j])
);

constraint objective[THROUGHPUT] <= 
  max(j in jobs) (job_start[j] + job_duration[j]) -
  min(j in jobs) (job_start[j]);

constraint objective[LATENCY] = max(j in jobs) (job_start[j] + job_duration[j]);

constraint objective[THROUGHPUT] <= objective[LATENCY];

solve
  :: int_search(mapping, smallest, indomain_min, complete)
  :: restart_luby(length(jobs) * length(units))
  minimize sum(o in objectives) (objective_weights[o] * objective[o]);