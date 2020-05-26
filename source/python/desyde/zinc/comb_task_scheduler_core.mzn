set of int: runnables;
set of int: mandatory_tasks;
set of int: mandatory_schedulers;
set of int: cores;

array[runnables] of set of int: admissible_runnable_partition;
array[mandatory_tasks] of set of int: admissible_task_mapping;
array[mandatory_schedulers] of set of int: admissible_scheduler_mapping;

array[runnables, cores] of int: runnable_wcet_per_core;
array[runnables] of int: runnable_deadline;
array[runnables] of int: runnable_period;
array[runnables] of int: runnable_priority;

array[runnables] of var mandatory_tasks: runnable_partition;
array[mandatory_tasks] of var mandatory_schedulers: task_mapping;
array[mandatory_schedulers, cores] of var bool: scheduler_mapping;

array[mandatory_tasks] of var int: task_deadline;
array[mandatory_tasks] of var int: task_period;
array[mandatory_tasks] of var int: task_priority;
array[mandatory_tasks, cores] of var int: task_wcet_per_core;

constraint forall(r in runnables) (
  runnable_partition[r] in admissible_runnable_partition[r];
);

constraint forall(tau in mandatory_tasks) (
  task_mapping[tau] in admissible_task_mapping[tau];
);

constraint forall(s in mandatory_schedulers) (
  scheduler_mapping[s] in admissible_scheduler_mapping[s];
);

constraint forall(tau in mandatory_tasks) (
  task_deadline[tau] = min(r in runnables where runnable_partition[r] = tau)
    (runnable_deadline[r])
);

constraint forall(tau in mandatory_tasks) (
  task_period[tau] = min(r in runnables where runnable_partition[r] = tau)
    (runnable_period[r])
);

constraint forall(tau in mandatory_tasks) (
  task_priority[tau] = min(r in runnables where runnable_partition[r] = tau)
    (runnable_priority[r])
);

constraint forall(tau in mandatory_tasks, c in cores) (
  sum(r in runnables where runnable_partition[r] = tau) (runnable_wcet_per_core[r, c])
  =
  task_wcet_per_core[tau, c]
);

constraint forall(s in mandatory_schedulers) (
  count(t in mandatory_tasks) (task_mapping[t] = s) > 0 
  ->
  count(c in cores) (scheduler_mapping[s, c] = true) > 0
);

predicate unicore_schedulibility() =
  forall(tau in mandatory_tasks) (
    exists(t in 1..task_deadline[tau]) (
    let { var mandatory_schedulers: s = task_mapping[tau],
          var opt cores: c = scheduler_mapping[task_mapping[tau]] 
        } in
      t 
      >= 
      task_wcet_per_core[tau, c] + sum(tau2 in mandatory_tasks where 
        s = task_mapping[tau2] and
        task_priority[tau2] > task_priority[tau]) 
        (task_wcet_per_core[tau2, c] * ceil(t / task_period[tau2]))
    )
  )

constraint forall(s in mandatory_schedulers) (
  if count(c in cores) (scheduler_mapping[s, c] = true) = 1 then
    unicore_schedulibility()
  else
);
