include "globals.mzn";

set of int: Processes;
set of int: Buffers;
set of int: Firings;
set of int: Memories;
set of int: Communications;
set of int: ListSchedulers;
set of int: LogicAreas;

array[Firings] of int: firingsActor;
array[Firings] of int: firingsNumber;
array[Firings] of set of Firings: follows; % equivalent to f_i < f_j is precedes[i, j] = true

array[Communications] of int: slots;

set of int: Mappable = ListSchedulers union LogicAreas;

% array[Mappable, Memories] of set of Communications: interconnectToMemories;
% array[Memories, Mappable] of set of Communications: interconnectFromMemories;
array[Processes, Mappable] of int: executionTime;

array[Processes, Processes] of bool: connected;

int: nPareto;
array[1..nPareto, 1..(card(Processes) + 1)] of int: previousSolutions;

array[Processes] of var Mappable: processesExecution;
array[Processes] of var Memories: processesMapping;
array[Buffers] of var Memories: buffersMapping;
array[Firings] of var 0..(card(Firings)-1): firingsOrdering;
array[Mappable, Communications] of var 0..max(slots): communicationReservation;

array[Processes] of var int: duration;

array[Firings] of var opt Firings: orderNext;
array[Firings, Firings] of var int: maxPath;

array[Processes] of var int: invThroughput;
var 0..card(Mappable): nUsedPEs;

constraint forall(p in Processes, pe in Mappable) (executionTime[p, pe] < 0 -> processesExecution[p] != pe);

constraint forall(p in Processes)(duration[p] = executionTime[p, processesExecution[p]]); % this must be improved later with communication

constraint forall(f, ff in Firings where f != ff /\ ff in follows[f]) (processesExecution[firingsActor[f]] == processesExecution[firingsActor[ff]] -> firingsOrdering[f] < firingsOrdering[ff]);

constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] == processesExecution[firingsActor[ff]] -> firingsOrdering[f] != firingsOrdering[ff]);

constraint forall(pe in ListSchedulers, f in Firings) (processesExecution[firingsActor[f]] == pe -> count_lt([processesExecution[firingsActor[ff]] | ff in Firings], pe, firingsOrdering[f]));

constraint nvalue(nUsedPEs, processesExecution);

constraint forall(f in Firings) (orderNext[f] != f);
constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] != processesExecution[firingsActor[ff]] -> orderNext[f] != ff);
constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] == processesExecution[firingsActor[ff]] /\ firingsOrdering[f] + 1 == firingsOrdering[ff] <-> orderNext[f] = ff);


constraint forall(f in Firings) (maxPath[f, f] = duration[firingsActor[f]]);
constraint forall(f, ff in Firings where f != ff) (
%   let { var int: pe = processesExecution[firingsActor[f]] } in
  maxPath[f, ff] >= duration[firingsActor[f]] + max([0] ++ [maxPath[next, ff] | next in follows[f] where ff in follows[next]])
);
constraint forall(f, ff in Firings where f != ff) (
  let { var int: pe = processesExecution[firingsActor[f]] } in
  if pe in ListSchedulers then
    maxPath[f, ff] >= duration[firingsActor[f]] + maxPath[orderNext[f], ff]
  endif
);

constraint forall(p, pp in Processes where p != pp) (processesExecution[p] == processesExecution[pp] -> invThroughput[p] = invThroughput[pp]);
constraint forall(p, pp in Processes where p != pp) (connected[p, pp] -> invThroughput[p] = invThroughput[pp]);

constraint forall(a in Processes) (
    invThroughput[a] >= min([executionTime[a, p] | p in Mappable where executionTime[a, p] >= 0])
);

constraint forall(f, ff in Firings where f != ff /\ processesExecution[firingsActor[f]] == processesExecution[firingsActor[ff]] /\ firingsOrdering[f] < firingsOrdering[ff]) (
  invThroughput[firingsActor[f]] >= maxPath[f, ff]
);

constraint forall(solNum in 1..nPareto) (
  (previousSolutions[solNum, 1] > -1 -> nUsedPEs < previousSolutions[solNum, 1]) \/
  exists(proc in 2..(card(Processes)+1)) (
    (previousSolutions[solNum, proc] > -1 -> invThroughput[proc - 2] < previousSolutions[solNum, proc])
  )
);


solve :: warm_start(processesExecution, [arg_min([executionTime[p, pe] | pe in Mappable where executionTime[p, pe] > 0]) | p in Processes]) satisfy;
