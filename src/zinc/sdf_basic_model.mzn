include "globals.mzn";

set of int: sdf_actors; % this assumes that the sdf actors are flattened, i.r. 1,1,1,1,2,2,3,3,3... knowing the rep. vector.
int: n_sdf_actors = max(sdf_actors);
int: SINK = max(sdf_actors)+1;
int: SOURCE = 0;
set of int: sdf_channels;
set of int: processing_elems;
array[sdf_channels] of int: initial_tokens;
array[sdf_actors, processing_elems] of int: sdf_actor_wcet;
array[sdf_actors, sdf_channels] of int: sdf_topology;
array[processing_elems, processing_elems] of int: sdf_channel_wcct;

%% true decision variables
% using the maximum for simplicty, maybe we can change it later
array[sdf_actors] of var processing_elems: mapping;
array[SOURCE..SINK, SOURCE..SINK] of var bool: next;

%% dependent variables that help in the decision making
% we can limit each guy by the rep. vector later
% array[sdf_actors_repeat] of var 0..max(sdf_actors): indegree;
array[SOURCE..SINK, sdf_channels] of var int: tokens;
array[SOURCE..SINK] of var 0..sum(sdf_topology): tag;

%% dependent metrics
array[processing_elems] of var int: local_throughput;
var int: global_throughput;

constraint forall(a in SOURCE..SINK) (next[a, a] = false);

constraint forall(a in SOURCE..SINK) (next[a, SOURCE] = false);

constraint forall(a in SOURCE..SINK) (next[SINK, a] = false);

constraint next[SOURCE, SINK] = false;

% token flow
constraint tokens[SOURCE, ..] = initial_tokens;

constraint tokens[SINK, ..] = initial_tokens;

constraint forall(a in sdf_actors, c in sdf_channels) (
  tokens[a, c] = sum(a_prev in SOURCE..n_sdf_actors where next[a_prev, a]) (tokens[a_prev, c]) +
    sdf_topology[a, c]
);

constraint forall(c in sdf_channels) (
  tokens[SINK, c] = sum(a_prev in SOURCE..n_sdf_actors where next[a_prev, SINK]) (tokens[a_prev, c])
);

constraint forall(a in sdf_actors, c in sdf_channels) (
  tokens[a, c] > 0
);

% forking on different PEs if are next
% constraint forall(a1 in SOURCE..n_sdf_actors, a2, a3 in sdf_actors) (
%   next[a1, a2] /\ next[a1, a3] <-> mapping[a2] != mapping[a3]
% );

%everyone must be executed at least once
constraint forall(a in sdf_actors) (
 sum(b in 1..SINK) (next[a, b]) > 0
);

% the source must hae at least one output
constraint sum(a in sdf_actors) (next[SOURCE, a]) > 0;

% at least one actor finishes at sink
constraint sum(a in sdf_actors) (next[a, SINK]) > 0;

constraint forall(pe in processing_elems) (local_throughput[pe] >= 0);

% define the global throughput
constraint global_throughput = max(local_throughput);

solve :: seq_search([
          int_search(mapping, input_order, indomain_min),
          int_search(next, input_order, indomain_min),
          int_search(tokens, first_fail, indomain_min)
          ]) satisfy;

output ["mapping: ", show(mapping), "\n",
	"next: ", show2d(next), "\n",
	"tag: ", show(tag), "\n",
	"tokens: ", show2d(tokens)];
