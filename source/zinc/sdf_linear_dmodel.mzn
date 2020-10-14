include "globals.mzn";

set of int: sdf_actors; % not flattened
set of int: sdf_channels;

int: max_tokens;
int: max_steps;

set of int: steps0 = 0..max_steps;
set of int: steps = 1..max_steps;

array[sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_actors, sdf_channels] of int: sdf_topology;

array[sdf_channels, steps0] of var 0..max_tokens: buffer;
array[sdf_actors, steps] of var 0..max(activations): mapped;

constraint buffer[.., 0] = initial_tokens;

constraint buffer[.., max_steps] = initial_tokens;

constraint forall(t in steps, c in sdf_channels) (
  buffer[c, t] = sum(a in sdf_actors) (sdf_topology[a, c]*mapped[a, t]) + buffer[c, t-1]
);

constraint forall(c in sdf_channels, t in steps0) (
  buffer[c, t] >= 0
);

constraint forall(a in sdf_actors) (
  sum(t in steps) (mapped[a, t]) = activations[a]
);

% each slot can only admit one event
constraint forall(t in steps) (
  sum(a in sdf_actors) (mapped[a, t]) <= 1
);

solve satisfy;

output [
  "buffer: ", show2d(buffer),
  "mapped: ", show2d(mapped)
%   "time: ", show2d(time)
];
