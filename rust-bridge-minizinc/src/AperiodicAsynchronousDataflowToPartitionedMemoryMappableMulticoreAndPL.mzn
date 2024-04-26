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

array[Processes] of var Mappable: processesExecution;
array[Processes] of var Memories: processesMapping;
array[Buffers] of var Memories: buffersMapping;
array[Firings] of var 0..card(Firings): firingsOrdering;
array[Mappable, Communications] of var 0..max(slots): communicationReservation;

array[Processes] of var int: duration;

array[Firings] of var int: maxNext;
array[Firings, Firings] of var int: maxPath;

array[Processes] of var int: invThroughput;
var 0..card(Mappable): nUsedPEs;

constraint forall(p in Processes)(duration[p] >= min([executionTime[p, pe] | pe in Mappable where executionTime[p, pe] >= 0]));

constraint forall(p in Processes)(duration[p] = executionTime[p, processesExecution[p]]); % this must be improved later with communication

constraint forall(f, ff in Firings where f != ff /\ ff in follows[f]) (processesExecution[firingsActor[f]] == processesExecution[firingsActor[ff]] -> firingsOrdering[f] < firingsOrdering[ff]);

constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] == processesExecution[firingsActor[ff]] -> firingsOrdering[f] != firingsOrdering[ff]);

constraint forall(pe in Mappable, f in Firings) (processesExecution[firingsActor[f]] == pe <-> count_lt([processesExecution[firingsActor[ff]] | ff in Firings], pe, firingsOrdering[f]));

constraint forall(p, pp in Processes where p != pp) (processesExecution[p] == processesExecution[pp] -> invThroughput[p] = invThroughput[pp]);

constraint nvalue(nUsedPEs, processesExecution);

constraint forall(f in Firings) (maxPath[f, f] = duration[firingsActor[f]]);

constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] == processesExecution[firingsActor[ff]] /\ firingsOrdering[f] + 1 == firingsOrdering[ff] -> maxNext[f] = max(maxPath[ff, ..]));

constraint forall(f, ff in Firings where f != ff) (
  maxPath[f, ff] = duration[firingsActor[f]] + max([maxNext[f]] ++ [maxPath[next, ff] | next in follows[f] where next != f])
); % + max([maxPath[next, ff] | next in follows[f] where next != f]));

constraint forall(a in Processes) (
    invThroughput[a] >= min([executionTime[a, p] | p in Mappable where executionTime[a, p] >= 0])
);

constraint forall(f, ff in Firings where f != ff) (
  invThroughput[firingsActor[ff]] >= maxPath[f, ff]
);


solve satisfy;