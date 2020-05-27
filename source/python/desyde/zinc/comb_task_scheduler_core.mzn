%%
% 
%
% Current problems: Treats both global and partitioned cases equally. Ideally,
% scheduler on a single core would use more powerful tests tailored for single core
% execution.

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
  runnable_partition[r] in admissible_runnable_partition[r]
);

constraint forall(tau in mandatory_tasks) (
  task_mapping[tau] in admissible_task_mapping[tau]
);

constraint forall(s in mandatory_schedulers) (
  scheduler_mapping[s] in admissible_scheduler_mapping[s]
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
  task_priority[tau] = max(r in runnables where runnable_partition[r] = tau)
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

constraint count(b in scheduler_mapping) (b = true) > 0;

% predicate unicore_schedulibility(
%   mapped_tasks,
%   scheduler,
%   core
% ) =
%   forall(tau in mapped_tasks) (
%     exists(t in 1..task_deadline[tau]) (
%     let { var cores: c = scheduler_mapping[scheduler] 
%         } in
%       t 
%       >= 
%       task_wcet_per_core[tau, c] + sum(tau2 in mandatory_tasks where 
%         s = task_mapping[tau2] /\
%         task_priority[tau2] > task_priority[tau]) 
%         (task_wcet_per_core[tau2, c] * ceil(t / task_period[tau2]))
%     )
%   );

% The global test, as found in Davis, Robert I., and Alan Burns. ‘A Survey of Hard Real-
% Time Scheduling for Multiprocessor Systems’. ACM Comput. Surv. 43, no. 4 
% (October 2011): 35:1–35:44. https://doi.org/10.1145/1978802.1978814.
% at page 35:21
predicate max_workload(var mandatory_tasks: tau, int: interval, var cores: core) =
  let {
    int: wcet = task_wcet_per_core[tau, core],
    int: deadline = task_deadline[tau],
    int: period = task_period[tau],
    int: N = floor((interval + deadline - wcet)/period) } in
  N*wcet +  min([wcet, interval + deadline - wcet - N*period]);

constraint forall(s in mandatory_schedulers) (
  let { var cores: core = argmax(c in cores where scheduler_mapping[s,c]) 
          (task_wcet_per_core[..,c]),
        int: m = count(c in cores) (scheduler_mapping[s,c]) 
      } in
  forall(tau in mandatory_tasks where task_mapping[tau] = s) (
    sum(tau2 in mandatory_tasks where task_mapping[tau] = s /\ 
        task_priority[tau2] > task_priority[tau]) (
      min([max_workload(tau, task_deadline[tau2]), 
        task_deadline[tau2] - task_wcet_per_core[tau2, core] + 1])
    ) 
    <
    m * (task_deadline[tau2] - task_wcet_per_core[tau2, core] + 1)   
  )
);

% the test is for homogeneous global schedulers, so we enforce homogeneity
constraint forall(s in mandatory_schedulers) (
  forall(tau in mandatory_tasks) (
    forall(c1, c2 in cores) (
      task_mapping[tau] = s /\
      task_wcet_per_core[tau, c1] != task_wcet_per_core[tau, c2]
      ->
      scheduler_mapping[s, c1] != scheduler_mapping[s, c2]      
    )
  )
);
