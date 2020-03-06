include "globals.mzn";

set of int: sdf_actors; % not flattened
set of int: sdf_channels;
set of int: processing_elems;

int: max_tokens;
int: max_steps;

set of int: steps_nf = 0..max_steps-1;
set of int: steps = 0..max_steps;

array[processing_elems, sdf_channels] of int: initial_tokens;
array[sdf_actors] of int: activations;
array[sdf_actors, processing_elems] of int: sdf_actor_wcet;
array[sdf_actors, sdf_channels] of int: sdf_topology;
array[processing_elems, processing_elems] of int: sdf_channel_wcct;


array[sdf_channels, steps, processing_elems] of var 0..max_tokens: buffer;
array[steps, processing_elems] of var sdf_actors: mapped;
array[steps, processing_elems, processing_elems] of var 0..max_tokens: comm;

constraint forall(p in processing_elems) (
  buffer[.., 0, p] = initial_tokens[p, ..]
);

constraint forall(c in sdf_channels, t in steps, p in processing_elems) (
  buffer[c, t, p] >= 0
);

constraint forall(c in sdf_channels, t in steps_nf, p in processing_elems) (
  buffer[c, t+1, p] = sdf_topology[mapped[t, p], c] + buffer[c, t, p]
    - sum(pp in processing_elems where p != pp) (comm[t, p, pp])
    + sum(pp in processing_elems where p != pp) (comm[t, pp, p])
);

constraint forall(a in sdf_actors) (
  sum(p in processing_elems, t in steps) (mapped[t,p] = a) = activations[a]
);