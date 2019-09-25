include "globals.mzn";

set of int: sdf_actors; % this assumes that the sdf actors are flattened, i.r. 1,1,1,1,2,2,3,3,3... knowing the rep. vector.
set of int: sdf_actors_repeat = 1..max(sdf_actors)+1; 
set of int: sdf_steps = 1..max(sdf_actors); % buffers AFTER firing each actor
set of int: sdf_steps_zero = 0..max(sdf_actors); % buffers AFTER firing each actor
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
array[sdf_steps_zero, sdf_channels] of var int: tokens;
array[sdf_actors] of var int: completion_time;
array[sdf_actors] of var sdf_steps: steps;

%% dependent metrics
array[processing_elems] of var int: local_throughput;
var int: global_throughput;

% since the sdf is flattened, the next (order) should be distincint
% they cannot fire to themselves, as they are flattened
constraint forall(actor in sdf_actors) (next[actor] != actor);
% one, and only one of them must point to the repeat order
constraint sum(actor in sdf_actors) (next[actor] = max(sdf_actors)+1) = 1;

%timing only positive
constraint forall(actor in sdf_actors)
(
 completion_time[actor] >= 0
);

% the tokens cannot be negative
constraint forall(step in sdf_steps, channel in sdf_channels)
(
 tokens[step, channel] >= 0
);

% step and time bijection
constraint forall(a1 in sdf_actors, a2 in sdf_actors)
(
  completion_time[a1] <= completion_time[a2] <-> steps[a1] <= steps[a2]
);

% from the moc perspective, the next paramters gives away from the buffers evolves
constraint tokens[0, ..] = initial_tokens;
constraint forall(step in sdf_steps, channel in sdf_channels)
(
 tokens[step, channel] = tokens[step-1, channel] + 
  sum(a in sdf_actors where steps[a] = step-1) (sdf_topology[a, channel])
);

% implied important constraints
constraint forall(a1 in sdf_actors, a2 in sdf_actors where a1 != a2)
(
 next[a1] = a2 <-> completion_time[a1] < completion_time[a2]
);

constraint forall(an in sdf_actors)
(
 completion_time[an] >= max(a in sdf_actors where mapping[a] = mapping[an]) (completion_time[a] + sdf_actor_wcet[a, mapping[a]])
);


% the time for the next completion time is always greater
constraint forall(an in sdf_actors)
(
 completion_time[an] >= max(a in sdf_actors where next[a] = an)
 (completion_time[a] + sdf_actor_wcet[a, mapping[a]] + sdf_channel_wcct[mapping[a], mapping[an]])
);

% define the local throughput of each PE
% constraint forall(pe in processing_elems)
% (
%  local_throughput[pe] = max(actor in sdf_actors where mapping[actor] = pe) (completion_time[actor])
%  - min(actor in sdf_actors where mapping[actor] = pe) (completion_time[actor] - sdf_actor_wcet[actor, mapping[actor]])
% );

% define the global throughput
constraint global_throughput = max(local_throughput);

solve :: seq_search([
          int_search(mapping, input_order, indomain_min),
          int_search(next, input_order, indomain_min)
          ]) satisfy;

