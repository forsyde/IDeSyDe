include "globals.mzn";

set of int: sdf_actors; % this assumes that the sdf actors are flattened, i.r. 1,1,1,1,2,2,3,3,3... knowing the rep. vector.
set of int: sdf_channels;
set of int: processing_elems;

set of int: steps = 1..n_sdf_actors;

int: n_sdf_actors = max(sdf_actors);
int: n_processing_elems = max(processing_elems);
int: SOURCE = 0;
int: SINK = n_sdf_actors+1;

array[sdf_channels] of int: initial_tokens;
array[sdf_actors, processing_elems] of int: sdf_actor_wcet;
array[sdf_actors, sdf_channels] of int: sdf_topology;
array[processing_elems, processing_elems] of int: sdf_channel_wcct;

%% true decision variables
% using the maximum for simplicty, maybe we can change it later
array[sdf_actors] of var processing_elems: mapping;
array[sdf_actors, sdf_actors, sdf_channels] of var int: flow;

%% dependent variables that help in the decision making
% we can limit each guy by the rep. vector later
% array[sdf_actors_repeat] of var 0..max(sdf_actors): indegree;
array[sdf_actors] of var 1..n_sdf_actors: rank;
array[sdf_actors] of var 0..sum(sdf_actor_wcet): tag;
array[sdf_actors, sdf_actors] of var bool: next;

%% dependent metrics
array[processing_elems] of var int: local_throughput;
var int: global_throughput;
var int: global_latency;

constraint forall(a, b in sdf_actors, c in sdf_channels) (
  flow[a, b, c] >= 0
);

constraint forall(a in sdf_actors, c in sdf_channels) (
  flow[a, a, c] = 0
);

constraint forall(a, b in sdf_actors) (
  sum(c in sdf_channels) (flow[a, b, c]) > 0 -> rank[a] < rank[b]
);

constraint forall(a, b in sdf_actors) (
  sum(c in sdf_channels) (flow[a, b, c]) > 0 -> next[a, b] = true
);

constraint forall(a, b, c in sdf_actors where a != b /\ b != c) (
  mapping[b] = mapping[c] ->
    next[a, b] xor next[a, c]
);

constraint at_least(1, rank, 1);

% constraint forall(c in sdf_channels) (
%   sum(a in sdf_actors where rank[a] = 1) (
%     sum(b in sdf_actors where b != a) (flow[a, b, c]) - sdf_topology[a, c]
%   )
%   =
%   initial_tokens[c]
% );

constraint forall(a in sdf_actors, c in sdf_channels) (
  sum(aprev in sdf_actors where a != aprev) (flow[aprev, a, c]) + sdf_topology[a, c] =
  sum(anext in sdf_actors where a != anext) (flow[a, anext, c])
);

constraint forall(a, b in sdf_actors, c in sdf_channels) (
  sdf_topology[a, c] = 0 -> flow[a, b, c] = 0
);

constraint forall(a in sdf_actors where rank[a] = 1) (
  tag[a] = 0
);

constraint forall(aprev, a in sdf_actors where aprev < SINK /\ SOURCE < a) (
   next[aprev, a] -> tag[aprev] + sdf_actor_wcet[aprev, mapping[aprev]] + 
     sdf_channel_wcct[mapping[aprev], mapping[a]]
   <= tag[a]
);

% constraint tag[SINK] = max(a in sdf_actors where next[a, SINK]) (tag[a]);

constraint forall(a, b in sdf_actors where a != b) (
  mapping[a] = mapping[b] -> 
    tag[a] + sdf_actor_wcet[a, mapping[a]] <= tag[b] \/
    tag[b] + sdf_actor_wcet[b, mapping[b]] <= tag[a]    
);

constraint forall(a, b in sdf_actors where a != b) (
  mapping[a] = mapping[b] -> rank[a] != rank[b]
);

constraint cumulative(tag, 
                      [sdf_actor_wcet[a, mapping[a]] | a in sdf_actors],
                      [1 | a in sdf_actors],
                      n_processing_elems);

constraint forall(pe in processing_elems) (local_throughput[pe] >= 0);

constraint forall(pe in processing_elems) (
  local_throughput[pe] = max(a in sdf_actors where mapping[a] = pe) (tag[a] + sdf_actor_wcet[a, mapping[a]]) - 
    min(a in sdf_actors where mapping[a] = pe) (tag[a])
);

% define the global throughput
constraint global_throughput = max(local_throughput);

constraint global_latency = max(a in sdf_actors) (tag[a] + sdf_actor_wcet[a, mapping[a]]);

solve :: seq_search([
          int_search(mapping, input_order, indomain_min)
          ]) minimize global_throughput * global_latency;

output ["mapping: ", show(mapping), "\n",
	"flow: ", show3d(flow), "\n",
	"next: ", show2d(next), "\n",
	"tag: ", show(tag), "\n",
	"rank: ", show(rank), "\n",
	"Throughput: ", show(global_throughput), "\n",
	"Latency: ", show(global_latency), "\n"];
