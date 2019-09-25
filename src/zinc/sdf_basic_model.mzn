include "globals.mzn";

set of int: sdf_actors; % this assumes that the sdf actors are flattened, i.r. 1,1,1,1,2,2,3,3,3... knowing the rep. vector.
int: REPEAT = max(sdf_actors)+1;
set of int: sdf_actors_repeat = 1..REPEAT; 
set of int: sdf_steps = 1..max(sdf_actors); % buffers AFTER firing each actor
set of int: sdf_channels;
set of int: processing_elems;
array[sdf_channels] of int: initial_tokens;
array[sdf_actors, processing_elems] of int: sdf_actor_wcet;
array[sdf_actors, sdf_channels] of int: sdf_topology;
array[processing_elems, processing_elems] of int: sdf_channel_wcct;

%% true decision variables
% using the maximum for simplicty, maybe we can change it later
array[sdf_actors] of var processing_elems: mapping;
array[sdf_actors] of var sdf_actors_repeat: next; 

%% dependent variables that help in the decision making
% we can limit each guy by the rep. vector later
array[sdf_actors_repeat] of var 0..max(sdf_actors): indegree;
array[sdf_steps, sdf_channels] of var int: tokens;
array[sdf_actors] of var int: completion_time;
array[sdf_actors] of var sdf_steps: steps;

%% dependent metrics
array[processing_elems] of var int: local_throughput;
var int: global_throughput;

constraint forall(actor in sdf_actors) (
 next[actor] != actor
);

constraint indegree[REPEAT] = max(sdf_actors);

constraint forall(a in sdf_actors)
(
 indegree[a] = sum(b in sdf_actors where next[b] = a /\ a != b) (1 + indegree[b])
);

constraint forall(a in sdf_actors, b in sdf_actors_repeat where a != b)
(
  next[a] = b <-> indegree[a] < indegree[b]
);

constraint forall(pe in processing_elems, a1 in sdf_actors, a2 in sdf_actors where a1 != a2)
(
 % all_different([next[a] | a in sdf_actors where mapping[a] = pe])
 mapping[a1] = mapping[a2] -> next[a1] != next[a2]
);

%timing only positive
constraint forall(actor in sdf_actors) (completion_time[actor] >= 0);

constraint forall(pe in processing_elems) (local_throughput[pe] >= 0);

% the tokens cannot be negative
constraint forall(step in sdf_steps, channel in sdf_channels) (tokens[step, channel] >= 0);

% step and time bijection
constraint forall(a1 in sdf_actors, a2 in sdf_actors)
(
  completion_time[a1] <= completion_time[a2] <-> steps[a1] <= steps[a2]
);

% from the moc perspective, the next paramters gives away from the buffers evolves
constraint tokens[1, ..] = initial_tokens;
constraint forall(step in 1..max(sdf_actors)-1, channel in sdf_channels)
(
 tokens[step+1, channel] = tokens[step, channel] + 
  sum(a in sdf_actors where steps[a] = step) (sdf_topology[a, channel])
);
constraint forall(c in sdf_channels)
(
 tokens[1, c] = tokens[max(sdf_actors), c] + 
 sum(a in sdf_actors where steps[a] = max(sdf_actors)) (sdf_topology[a, c])
);

constraint forall(a in sdf_actors, k in 1..max(sdf_actors))
(
 sum(c in sdf_channels) (tokens[k, c] + sdf_topology[a, c] < 0) > 0 -> steps[a] != k
);
 

% constraint forall(a1 in sdf_actors, a2 in sdf_actors where a1 != a2)
% (
%  next[a1] = a2 <-> completion_time[a1] < completion_time[a2]
% );

% constraint forall(an in sdf_actors)
% (
%  completion_time[an] >= max(a in sdf_actors where next[a] = an /\ mapping[a] = mapping[an]) (completion_time[a] + sdf_actor_wcet[a, mapping[a]])
% );

% the time for the next completion time is always greater
constraint forall(an in sdf_actors)
(
 completion_time[an] = sdf_actor_wcet[an, mapping[an]] + max(a in sdf_actors where next[a] = an)
  (completion_time[a] + sdf_channel_wcct[mapping[a], mapping[an]])
);

% define the local throughput of each PE
constraint forall(pe in processing_elems)
(
 local_throughput[pe] = max(actor in sdf_actors where mapping[actor] = pe) (completion_time[actor])
 - min(actor in sdf_actors where mapping[actor] = pe) (completion_time[actor] - sdf_actor_wcet[actor, mapping[actor]])
);

% define the global throughput
constraint global_throughput = max(local_throughput);

solve :: seq_search([
          int_search(mapping, input_order, indomain_min),
          int_search(next, input_order, indomain_min)
          ]) satisfy;

output [show(mapping), "\n",
	show(next), "\n",
	show(completion_time), "\n",
	show(steps), "\n",
	show2d(tokens), "\n",
	show(indegree)];
