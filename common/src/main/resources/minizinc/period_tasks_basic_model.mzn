set of int: tasks;
set of int: cpms;

array[tasks] of int: periods;
array[tasks] of int: deadlines;
array[tasks] of int: wcet;
array[tasks] of set of int: high_prio;

array[tasks] of var cpms: mapping;
array[tasks] of var 1..max(deadlines): response;

include "globals.mzn";

constraint forall(task in tasks) (
  exists(t in 1..deadlines[task]) (
    t >= wcet[task] + sum(task2 in high_prio[task] where mapping[task] = mapping[task2]) (ceil(t/periods[task2])*wcet[task2])
  )
);

constraint forall(task in tasks) (
  response[task] = arg_min(t in 1..deadlines[task]) (
    t < wcet[task] + sum(task2 in high_prio[task] where mapping[task] = mapping[task2]) (ceil(t/periods[task2])*wcet[task2])
  )
);


constraint forall(task in tasks) (response[task] <= deadlines[task]);

solve satisfy;

% output [show(true > false)];