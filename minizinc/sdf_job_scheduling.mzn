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
% array[jobs] of var procs: mapping;
array[jobs] of var 0..(sum(wcet)+sum(wcct)): job_start;
array[jobs] of var min(wcet)..max(wcet): job_duration;
array[jobs] of var 0..(sum(wcet)+sum(wcct)): job_end;
array[sdf_channels, jobs, jobs] of var 0..max_tokens: job_send;
array[sdf_channels, jobs] of var 0..max_tokens: buffer_start;
array[sdf_channels, jobs] of var 0..max_tokens: buffer_end;
array[jobs, procs] of var opt 0..(sum(wcet)+sum(wcct)): start;
array[jobs, procs] of var min(wcet)..max(wcet): duration;


% objectives
array[procs] of var 0..sum(wcet)+sum(wcct): cpu_latency;
array[objectives] of var 0..2*sum(wcet)+sum(wcct): objective;

%sdf semantics
constraint forall(c in sdf_channels, j in jobs) (
  buffer_start[c, j] = sum(jj in jobs where j != jj) (job_send[c, jj, j] + buffer_end[c, jj])
);

constraint forall(c in sdf_channels, j in jobs) (
  buffer_end[c, j] = sdf_topology[c, jobs_actors[j]] + buffer_start[c, j]
                   - sum(jj in jobs where j != jj) (job_send[c, j, jj])
);

% transfer semantics
constraint forall(c in sdf_channels, p, pp in units where p != pp) (
  not(pp in units_neighs[p]) -> transf[c, p, pp] = 0
);

% TODO fixz this
% constraint forall(c in sdf_channels, p in units) (
%   sum(j in jobs) (recv[c, p, j]) + sum(pp in units where p != pp) (transf[c, p, pp]) =
%   sum(j in jobs) (send[c, j, p]) + sum(pp in units where p != pp) (transf[c, pp, p])
% );

% timing semantics
constraint forall(j, jj in jobs where j != jj) (
  sum(job_send[.., j, jj]) > 0 -> job_end[j] <= job_start[jj]
);

constraint forall(j in jobs, p in procs) (
  min(wcet[jobs_actors[j], ..]) <= job_duration[j] /\
  job_duration[j] <= max(wcet[jobs_actors[j], ..])
);

constraint forall(j in jobs) (
  job_start[j] + job_duration[j] = job_end[j]
);

constraint forall(j in jobs) (
  alternative(job_start[j], job_duration[j], start[j, ..], duration[j, ..])
);
constraint forall(p in procs) (
  disjunctive(start[.., p], [wcet[jobs_actors[j], p] | j in jobs])
);
constraint cumulative(job_start, job_duration, [1|i in jobs], length(procs));

%objectives
constraint objective[THROUGHPUT] = max(p in procs) (
  sum(j in jobs) (duration[j, p])
);

constraint objective[LATENCY] = max(j in jobs) (job_start[j] + job_duration[j]);

solve minimize sum(o in objectives) (objective_weights[o] * objective[o]);