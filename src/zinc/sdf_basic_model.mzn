include "globals.mzn";

set of int: sdf_actors; % this assumes that the sdf actors are flattened, i.r. 1,1,1,1,2,2,3,3,3... knowing the rep. vector.
int: n_sdf_actors = max(sdf_actors);
int: SINK = max(sdf_actors)+1;
int: SOURCE = 0;
set of int: sdf_actors_repeat = 1..SINK; 
set of int: sdf_events = 1..max(sdf_actors); % buffers AFTER firing each actor
set of int: sdf_events_repeat = 1..SINK;
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
array[SOURCE..SINK, sdf_channels] of var 0..sum(sdf_topology): tokens;
array[sdf_events] of var int: tag;

%% dependent metrics
array[processing_elems] of var int: local_throughput;
var int: global_throughput;

constraint forall(a in SOURCE..SINK) (next[a, a] = false);

constraint forall(a in SOURCE..SINK) (next[a, SOURCE] = false);

constraint forall(a in SOURCE..SINK) (next[SINK, a] = false);

% forking on different PEs if are next
constraint forall(a1 in SOURCE..n_sdf_actors, a2, a3 in sdf_actors) (
  next[a1, a2] /\ next[a1, a3] -> mapping[a2] != mapping[a3]
);

% netwwork flow of buffers
%% actors with no incoming flows must sum the initial tokens
% constraint tokens[SOURCE, ..] = initial_tokens;
%% continuity of buffers
% constraint forall(c in sdf_channels, a in sdf_actors) (
%  tokens[a, c] = sum(b in SOURCE..n_sdf_actors where next[b, a]) 
%   (tokens[b, c]) + sdf_topology[a, c]
% );
%% finalization
% constraint forall(c in sdf_channels) (
%  tokens[SINK, c] = sum(b in SOURCE..n_sdf_actors where next[b, SINK]) (tokens[b, c])
% );
% constraint tokens[SINK, ..] = initial_tokens;

%everyone must be executed at least once
constraint forall(a in SOURCE..n_sdf_actors) (
 sum(b in 1..SINK) (next[a, b]) > 0
);

constraint forall(b in 1..SINK) (
 sum(a in SOURCE..n_sdf_actors) (next[a, b]) > 0
);

% the source must hae at least one output
constraint sum(a in sdf_actors) (next[SOURCE, a]) > 0;

% at least one actor finishes at sink
constraint sum(a in sdf_actors) (next[a, SINK]) > 0;


%timing only positive
% constraint forall(actor in sdf_actors) (tag[actor, FIRE] >= 0);

constraint forall(actor in sdf_actors) (tag[actor] >= 0);

constraint forall(pe in processing_elems) (local_throughput[pe] >= 0);

% the tokens cannot be negative
constraint forall(a in SOURCE..SINK, channel in sdf_channels) (tokens[a, channel] >= 0);

% event and time bijection
% constraint forall(a1, a2 in sdf_actors, et1, et2 in event_type)
% (
%   tag[a1, et1] <= tag[a2, et2] <-> events[a1, et1] <= events[a2, et2]
% );


% from the moc perspective, the next paramters gives away from the buffers evolves
% constraint tokens[1, .., COMPLETE] = initial_tokens;
% constraint forall(event in sdf_events, channel in sdf_channels)
% (
%  tokens[event+1, channel] = tokens[event, channel] + 
%   sum(a in sdf_actors where events[a] = event) (sdf_topology[a, channel])
% );
% constraint tokens[REPEAT, .., COMPLETE] = initial_tokens;


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
	"next: ", show2d(next), "\n",
	"tag: ", show(tag), "\n",
	"tokens: ", show2d(tokens)];
