include "globals.mzn";

enum event_type = {FIRE, COMPLETE};
set of int: sdf_actors; % this assumes that the sdf actors are flattened, i.r. 1,1,1,1,2,2,3,3,3... knowing the rep. vector.
int: n_sdf_actors = max(sdf_actors);
int: REPEAT = max(sdf_actors)+1;
set of int: sdf_actors_repeat = 1..REPEAT; 
set of int: sdf_events = 1..max(sdf_actors); % buffers AFTER firing each actor
set of int: sdf_events_repeat = 1..REPEAT;
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
array[sdf_events_repeat, sdf_channels, event_type] of var int: tokens;
array[sdf_events, event_type] of var int: tag;
array[sdf_events, event_type] of var sdf_actors: events;


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

constraint forall(a in sdf_actors)
(
 tag[a, COMPLETE] >= tag[a, FIRE] - sdf_actor_wcet[a, mapping[a]]
);

constraint forall(pe in processing_elems, a1, a2 in sdf_actors where a1 != a2)
(
 % all_different([next[a] | a in sdf_actors where mapping[a] = pe])
 mapping[a1] = mapping[a2] -> next[a1] != next[a2]
);

%timing only positive
constraint forall(actor in sdf_actors) (tag[actor, FIRE] >= 0);

constraint forall(actor in sdf_actors) (tag[actor, COMPLETE] >= 0);

constraint forall(pe in processing_elems) (local_throughput[pe] >= 0);

% the tokens cannot be negative
constraint forall(event in sdf_events_repeat, channel in sdf_channels) (tokens[event, channel, FIRE] >= 0);

constraint forall(event in sdf_events_repeat, channel in sdf_channels) (tokens[event, channel, COMPLETE] >= 0);

% event and time bijection
constraint forall(a1, a2 in sdf_actors, et1, et2 in event_type)
(
  tag[a1, et1] <= tag[a2, et2] <-> events[a1, et1] <= events[a2, et2]
);


% from the moc perspective, the next paramters gives away from the buffers evolves
constraint tokens[1, .., COMPLETE] = initial_tokens;
% constraint forall(event in sdf_events, channel in sdf_channels)
% (
%  tokens[event+1, channel] = tokens[event, channel] + 
%   sum(a in sdf_actors where events[a] = event) (sdf_topology[a, channel])
% );
constraint tokens[REPEAT, .., COMPLETE] = initial_tokens;


% constraint forall(a in sdf_actors, k in sdf_events, c in sdf_channels)
% (
%   events[a] = k -> (tokens[k, c] + sdf_topology[a, c] >= 0)
% );

% constraint forall(an in sdf_actors)
% (
%  start_time[an] >= max(a in sdf_actors where next[a] = an /\ mapping[a] = mapping[an]) (start_time[a] + sdf_actor_wcet[a, mapping[a]])
% );

% the time for the next completion time is always greater
% constraint forall(an in sdf_actors)
% (
%  start_time[an] >= max(a in sdf_actors where next[a] = an)
%   (completion_time[a] + sdf_channel_wcct[mapping[a], mapping[an]])
% );

% define the local throughput of each PE
% constraint forall(pe in processing_elems)
% (
%  local_throughput[pe] = max(actor in sdf_actors where mapping[actor] = pe) (completion_time[actor])
%  - min(actor in sdf_actors where mapping[actor] = pe) (start_time[actor])
% );

% define the global throughput
constraint global_throughput = max(local_throughput);

solve :: seq_search([
          int_search(mapping, input_order, indomain_min),
          int_search(next, input_order, indomain_min)
          ]) satisfy;

output ["mapping: ", show(mapping), "\n",
	"next: ", show(next), "\n",
	"tag: ", show2d(tag), "\n",
	"events: ", show2d(events), "\n",
	"tokens: ", show3d(tokens), "\n",
	"indegree: ", show(indegree)];
