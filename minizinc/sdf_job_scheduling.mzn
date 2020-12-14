include "globals.mzn";

% objectives
enum objectives = {THROUGHPUT, LATENCY};

% model parameters
set of int: sdf_actors;
set of int: sdf_channels;
set of int: procs;
set of int: comm_units;
set of int: units = procs union comm_units;

set of int: jobs;

array[sdf_channels] of int: max_tokens;
array[jobs] of sdf_actors: jobs_actors;
array[sdf_channels] of int: initial_tokens;
array[sdf_channels, sdf_actors] of int: sdf_topology;
array[sdf_actors, procs] of int: wcet;
array[sdf_channels, units] of int: token_wcct;
array[units] of set of int: units_neighs;
% array[sdf_channels, procs] of int: send_overhead;
% array[sdf_channels, procs] of int: read_overhead;

% objectives
array[objectives] of int: objective_weights;

% variables
array[jobs] of var procs: mapping;
array[jobs, procs] of var opt 0..sum(wcet): start;
array[jobs] of var 0..sum(wcet): job_start;
array[jobs] of var min(wcet)..max(wcet): job_duration;
array[jobs, procs] of var int: duration;
array[sdf_channels, jobs] of var 0..max(max_tokens): buffer_end;
array[sdf_channels, jobs] of var 0..max(max_tokens): buffer_start;
array[sdf_channels, jobs, jobs] of var 0..max(max_tokens): job_send;
array[sdf_channels, jobs, jobs, units, units] of var 0..max(max_tokens): transfers;
array[sdf_channels, jobs, jobs, units] of var 0..sum(wcet): transfers_start;
array[sdf_channels, jobs, jobs, units] of var 0..max(token_wcct): transfers_duration;

% objectives
array[jobs] of var 0..sum(wcet): job_throughput;
array[objectives] of var 0..2*sum(wcet): objective;

% tighten bounds for faster exploration
constraint forall(j in jobs, p in procs) (
  duration[j, p] = wcet[jobs_actors[j], p]
);
constraint forall(c in sdf_channels, j in jobs) (
  buffer_end[c, j] <= max_tokens[c] /\
  buffer_start[c, j] <= max_tokens[c]
);
constraint forall(c in sdf_channels, j, jj in jobs) (
  job_send[c, j, jj] <= max_tokens[c]
);
constraint forall(c in sdf_channels, j in jobs) (
  job_send[c, j, j] = 0
);
constraint forall(c in sdf_channels, j, jj in jobs, p, pp in units) (
  transfers[c, j, jj, p, pp] <= max_tokens[c]
);
constraint forall(c in sdf_channels, j, jj in jobs, p, pp in units where j != jj) (
  not(pp in units_neighs[p]) -> 
  transfers[c, j, jj, p, pp] = 0 /\
  transfers[c, j, jj, pp, p] = 0
);
constraint forall(c in sdf_channels, j in jobs, p, pp in units) (
  transfers[c, j, j, p, pp] = 0
);
constraint forall(c in sdf_channels, j, jj in jobs, p in units) (
  transfers[c, j, jj, p, p] = 0
);
constraint forall(j in jobs, p in procs) (
  min(wcet[jobs_actors[j], ..]) <= job_throughput[j]
);
constraint forall(j in jobs) (
  min(wcet[jobs_actors[j], ..]) <= job_duration[j] /\
  job_duration[j] <= max(wcet[jobs_actors[j], ..])
);
constraint forall(c in sdf_channels, j, jj in jobs, p in units where j != jj) (
  transfers_duration[c, j, jj, p] <= token_wcct[c, p]
);
constraint forall(c in sdf_channels, j in jobs, p in units) (
  transfers_duration[c, j, j, p] = 0
);


%sdf semantics
constraint forall(c in sdf_channels) (
  network_flow(
    array2d(1..length(jobs)*length(jobs), 1..2, [ if i mod 2 = 0 then jj else j endif | j, jj in jobs, i in 1..2 ]),
    [sdf_topology[c, jobs_actors[j]] | j in jobs ],
    [job_send[c, j, jj] | j, jj in jobs ]
  )
);
constraint forall(c in sdf_channels, j in jobs) (
  0 <= sdf_topology[c, jobs_actors[j]] - sum(jj in jobs) (job_send[c, j, jj]) + sum(jj in jobs) (job_send[c, jj, j])
);
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
constraint forall(j, jj in jobs, c in sdf_channels where j != jj) (
  let {
    var procs: jp = mapping[j];
    var procs: jjp = mapping[jj];
  } in
  jp != jjp ->
  job_send[c, j, jj] = sum(ps in units) (transfers[c, j, jj, jp, ps]) /\
  job_send[c, j, jj] = sum(ps in units) (transfers[c, j, jj, ps, jjp])
);

%% the paths between units must be connected
constraint forall(c in sdf_channels, j, jj in jobs) (
  network_flow(
    array2d(1..length(units)*length(units), 1..2, [if i mod 2 == 0 then p else pp endif | p, pp in units, i in 1..2]),
    [0 | p in units],
    [transfers[c, j, jj, p, pp] | p, pp in units]
  )
);
constraint forall(c in sdf_channels, j, jj in jobs, p in units where j != jj) (
  sum(pb in units_neighs[p]) (transfers[c, j, jj, pb, p]) = 
  sum(pa in units_neighs[p]) (transfers[c, j, jj, p, pa])
);

% timing semantics
constraint forall(j, jj in jobs where j != jj) (
  exists(c in sdf_channels) (job_send[c, j, jj] > 0) -> job_start[j] + job_duration[j] <= job_start[jj]
);
constraint forall(c in sdf_channels, j, jj in jobs where j != jj) (
  job_send[c, j, jj] > 0 -> 
  job_start[j] + job_duration[j] <= transfers_start[c, j, jj, mapping[j]] /\
  transfers_start[c, j, jj, mapping[jj]] <= job_start[jj]
);

constraint forall(c in sdf_channels, j, jj in jobs, p, pp in units where j != jj /\ p != pp) (
  transfers[c, j, jj, p, pp] > 0 ->
  transfers_start[c, j, jj, p] + transfers[c, j, jj, p, pp] * token_wcct[c, p]
  <= transfers_start[c, j, jj, pp]
);

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

constraint forall(p in units) (
  disjunctive(
    [transfers_start[c, j, jj, p] | j, jj in jobs, c in sdf_channels where j != jj],
    [transfers_duration[c, j, jj, p] | j, jj in jobs, c in sdf_channels where j != jj]
  )
);

%objectives
constraint forall(j, jj in jobs where j != jj) (
  mapping[j] = mapping[jj] ->
  job_throughput[j] >= job_start[jj] + job_duration[jj] - job_start[j]
);
constraint forall(j in jobs) (
  job_throughput[j] >= 
  max(jj in jobs where j != jj /\ mapping[j] != mapping[jj]) (job_throughput[jj])
);
constraint forall(j in jobs) (
  job_duration[j] <= job_throughput[j]
);
constraint objective[THROUGHPUT] = max(j in jobs) (job_throughput[j]);

constraint objective[THROUGHPUT] <= 
  max(j in jobs) (job_start[j] + job_duration[j]) -
  min(j in jobs) (job_start[j]);

constraint objective[LATENCY] = max(j in jobs) (job_start[j] + job_duration[j]);

constraint objective[THROUGHPUT] <= objective[LATENCY];

solve
  :: warm_start(mapping, [i mod length(procs) | i in jobs])
  :: restart_luby(length(jobs) * length(units))
  minimize sum(o in objectives) (objective_weights[o] * objective[o]);
