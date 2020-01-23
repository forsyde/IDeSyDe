set of int: tasks;
set of int: iterations = 2..length(tasks);
set of int: cpms;

array[tasks] of int: periods;
array[tasks] of int: deadlines;
array[tasks] of int: wcet;
array[tasks] of set of int: high_prio;

array[tasks] of var cpms: mapping;
array[tasks, iterations] of var int: response;

constraint forall(task in tasks) (
  response[task, 1] = wcet[task] + sum(t in high_prio[task] where mapping[task] = mapping[t]) (wcet[t])
);

constraint forall(task in tasks, i in iterations) (
  response[task, i] = 
   wcet[task] + sum(t in high_prio[task] where mapping[t] = mapping[task]) (ceil(response[task,i-1]/periods[t])*wcet[t])
);

constraint forall(task in tasks) (response[task, max(iterations)] <= deadlines[task]);

solve satisfy;