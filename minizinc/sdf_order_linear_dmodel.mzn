include "globals.mzn";

set of int: sdf_actors; % not flattened
set of int: sdf_channels;
set of int: static_orders;

int: max_tokens;
int: max_steps;

set of int: steps0 = 0..max_steps;
set of int: steps = 1..max_steps;

array[sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_actors, sdf_channels] of int: sdf_topology;

array[sdf_channels, static_orders, steps0] of var 0..max_tokens: buffer;
array[static_orders, static_orders, steps0] of var 0..max_tokens: send;
array[sdf_actors, static_orders, steps] of var 0..max(activations): mapped;

constraint sum(o, oo in static_orders) (send[o, oo, 0]) = 0;

constraint forall(a in sdf_actors) (
  sum(mapped[a, .., ..]) = activations[a]
);

constraint forall(c in sdf_channels) (
  sum(o in static_orders) (buffer[c, o, 0]) = initial_tokens[c]
);

constraint forall(c in sdf_channels) (
  sum(o in static_orders) (buffer[c, o, max_steps]) = initial_tokens[c]
);


constraint forall(t in steps, c in sdf_channels, o in static_orders) (
  buffer[c, o, t] = sum(a in sdf_actors) (sdf_topology[a, c]*mapped[a, o, t]) + buffer[c, o, t-1] 
  + sum(oo in sdf_channels where o != oo) (send[oo, o, t-1])
  - sum(oo in sdf_channels where o != oo) (send[o, oo, t])
);

constraint forall(c in sdf_channels, t in steps0, o in static_orders) (
  buffer[c, o, t] >= 0
);

constraint forall(a in sdf_actors) (
  sum(t in steps, o in static_orders) (mapped[a, o, t]) = activations[a]
);

% each slot can only admit one event
constraint forall(t in steps) (
  sum(a in sdf_actors, o in static_orders) (mapped[a, o, t]) <= 1
);

solve 
  :: int_search(mapped, first_fail, indomain_min, complete)
  :: int_search(buffer, first_fail, indomain_min, complete)
  :: int_search(send, first_fail, indomain_min, complete)
  satisfy;

output [
  "buffer: ", show3d(buffer),
  "send: ", show3d(send),
  "mapped: ", show3d(mapped)
%   "time: ", show2d(time)
];
