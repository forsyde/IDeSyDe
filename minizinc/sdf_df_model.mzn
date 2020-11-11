include "globals.mzn";

set of int: sdf_actors; % not flattened
set of int: sdf_channels;
set of int: processing_elems;

int: max_tokens;
int: max_steps;

set of int: steps = 0..max_steps;

array[processing_elems, sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_actors, processing_elems] of int: sdf_actor_wcet;
array[sdf_actors, sdf_channels] of int: sdf_topology;
array[processing_elems, processing_elems] of int: sdf_channel_wcct;


array[sdf_channels, steps, processing_elems] of var 0..max_tokens: buffer;
array[steps, sdf_actors, processing_elems] of var bool: mapped;
array[steps, steps, processing_elems, processing_elems, sdf_channels] of var 0..max_tokens: comm;
array[steps, processing_elems] of var 0..sum(sdf_actor_wcet): time;

constraint forall(p in processing_elems) (
  buffer[.., 0, p] = initial_tokens[p, ..]
);

constraint forall(p in processing_elems) (
  buffer[.., max_steps, p] = initial_tokens[p, ..]
);


constraint forall(c in sdf_channels, t in steps, p in processing_elems) (
  buffer[c, t, p] >= 0
);

constraint forall(c in sdf_channels, t in steps where t > 0, p in processing_elems) (
  buffer[c, t, p] = sum(a in sdf_actors) (sdf_topology[a, c]*mapped[t-1, a, p]) + buffer[c, t-1, p]
     + sum(p2 in processing_elems where p != p2, t2 in steps) 
        (comm[t2, t-1, p2, p, c] - comm[t, t2, p, p2, c])
);

% SDF event counting
constraint forall(a in sdf_actors) (
  sum(p in processing_elems, t in steps) (mapped[t,a,p]) = activations[a]
);

% no self send:
constraint forall(p in processing_elems, c in sdf_channels, t in steps where t > 0) (
  comm[t-1, t, p, p, c] = 0
);


% startup does not admit any event
constraint forall(p in processing_elems, a in sdf_actors) (
  mapped[0, a, p] = false
);
constraint forall(p, p2 in processing_elems, c in sdf_channels) (
  comm[0, 0, p, p2, c] = 0
);

% each slot can only admit one event
constraint forall(p in processing_elems, t in steps) (
  sum(a in sdf_actors) (mapped[t, a, p]) <= 1
);

constraint forall(p in processing_elems, t in steps where t > 0) (
  time[t, p] >= sum(a in sdf_actors) (sdf_actor_wcet[a, p]*mapped[t, a, p]) + time[t-1, p]
);

constraint forall(p in processing_elems) (
  time[0, p] = 0
);

constraint forall(p in processing_elems, t in steps where t > 0) (
  time[t, p] >= sum(p2 in processing_elems where p2 != p, t2 in steps, c in sdf_channels) 
    (sdf_channel_wcct[p2, p]*comm[t2, t, p2, p, c]) + time[t-1, p]
);

solve minimize max(time);

output [
  "buffer: ", show2d(buffer[.., 1, ..])
%   "time: ", show2d(time)
];