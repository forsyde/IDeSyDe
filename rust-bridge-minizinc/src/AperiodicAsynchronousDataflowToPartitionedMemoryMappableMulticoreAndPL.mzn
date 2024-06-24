include "globals.mzn";

set of int: Processes;
set of int: Buffers;
set of int: Firings;
set of int: Memories;
set of int: Communications;
set of int: ListSchedulers;
set of int: LogicAreas;
set of int: LogicAreaRequirements;

array[Firings] of int: firingsActor;
array[Firings] of int: firingsNumber;
array[Firings] of set of Firings: follows; % equivalent to f_i < f_j is precedes[i, j] = true

array[Communications] of int: slots;
array[Memories] of int: memorySize;
array[LogicAreas, LogicAreaRequirements] of int: logicAreaCapacity;
array[Buffers] of int: bufferSize;
array[Processes, Mappable] of int: processesMemSize;
array[Processes, LogicAreas, LogicAreaRequirements] of int: processesAreaRequirements;
array[Processes, Buffers] of int: processesReadBuffer;
array[Processes, Buffers] of int: processesWriteBuffer;

set of int: Mappable = ListSchedulers union LogicAreas;

array[Mappable, Memories] of opt set of Communications: interconnectToMemories;
array[Memories, Mappable] of opt set of Communications: interconnectFromMemories;
array[Processes, Mappable] of int: executionTime;
array[Communications] of int: invBandwidthPerChannel;

array[Processes, Processes] of bool: connected;

int: nPareto;
array[1..nPareto, 1..(card(Processes) + 1)] of int: previousSolutions;

array[Processes] of var Mappable: processesExecution;
array[Processes] of var Memories: processesMapping;
array[Buffers] of var Memories: buffersMapping;
array[Firings] of var 0..(card(Firings)-1): firingsOrdering;
array[Mappable, Communications] of var 0..max(slots): communicationReservation;

array[Processes] of var int: duration;
array[Processes] of var int: durationFetch;
array[Processes] of var int: durationRead;
array[Processes] of var int: durationWrite;

array[Firings] of var opt Firings: orderNext;
array[Firings, Firings] of var int: maxPath;

array[Processes] of var int: invThroughput;
var 0..card(Mappable): nUsedPEs;

constraint forall(p in Processes) (durationFetch[p] >= 0);
constraint forall(p in Processes) (durationRead[p] >= 0);
constraint forall(p in Processes) (durationWrite[p] >= 0);
constraint forall(p in Processes) (duration[p] >= min([e | e in executionTime[p, ..] where e >= 0]));

constraint forall(p in Processes, pe in ListSchedulers, me in Memories where absent(interconnectFromMemories[me, pe])) (
  processesExecution[p] != pe \/ processesMapping[p] != me
);

constraint forall(b in Buffers, p in Processes, pe in Mappable, me in Memories where processesReadBuffer[p, b] > 0 /\ absent(interconnectFromMemories[me, pe])) (
  processesExecution[p] != pe \/ buffersMapping[b] != me
);
constraint forall(b in Buffers, p in Processes, pe in Mappable, me in Memories where processesWriteBuffer[p, b] > 0 /\ absent(interconnectToMemories[pe, me])) (
  processesExecution[p] != pe \/ buffersMapping[b] != me
);

constraint forall(p in Processes, pe in Mappable where executionTime[p, pe] < 0) (processesExecution[p] != pe);

constraint forall(m in Memories) (
  sum(p in Processes where processesMapping[p] = m) (processesMemSize[p, processesExecution[p]]) +
  sum(b in Buffers where buffersMapping[b] = m) (bufferSize[b])
  <= 
  memorySize[m]
);

constraint forall(pla in LogicAreas, req in LogicAreaRequirements where occurs(logicAreaCapacity[pla, req])) (
  sum(p in Processes where processesExecution[p] = pla) (processesAreaRequirements[p, pla, req])
  <= 
  logicAreaCapacity[pla, req]
);

constraint forall(pe in Mappable, me in Memories where occurs(interconnectFromMemories[me, pe])) (
  exists(p in Processes) (processesExecution[p] = pe /\ processesMapping[p] = me) -> 
  forall(ce in deopt(interconnectFromMemories[me, pe])) (communicationReservation[pe, ce] > 0)
);
constraint forall(pe in Mappable, me in Memories where occurs(interconnectFromMemories[me, pe])) (
  exists(p in Processes, b in Buffers where processesReadBuffer[p, b] > 0) (processesExecution[p] = pe /\ buffersMapping[b] = me) -> 
  forall(ce in deopt(interconnectFromMemories[me, pe])) (communicationReservation[pe, ce] > 0)
);
constraint forall(pe in Mappable, me in Memories where occurs(interconnectToMemories[pe, me])) (
  exists(p in Processes, b in Buffers where processesWriteBuffer[p, b] > 0) (processesExecution[p] = pe /\ buffersMapping[b] = me) -> 
  forall(ce in deopt(interconnectToMemories[pe, me])) (communicationReservation[pe, ce] > 0)
);

constraint forall(ce in Communications) (
  sum(pe in Mappable) (communicationReservation[pe, ce]) <= slots[ce]
);

constraint forall(p in Processes, pe in Mappable) (executionTime[p, pe] < 0 -> processesExecution[p] != pe);

constraint forall(p in Processes, pe in ListSchedulers, me in Memories where processesExecution[p] = pe /\ processesMapping[p] = me) (
    durationFetch[p] = 
        processesMemSize[p, pe] * sum(ce in deopt(interconnectFromMemories[pe, me])) (1) * min(ce in deopt(interconnectFromMemories[pe, me])) (invBandwidthPerChannel[ce] div max(communicationReservation[pe, ce], 1))
);
constraint forall(p in Processes, pe in LogicAreas where processesExecution[p] = pe) (
    durationFetch[p] = 0
);

constraint forall(p in Processes, pe in ListSchedulers where processesExecution[p] = pe) (
    durationRead[p] = sum(b in Buffers, me in Memories where processesReadBuffer[p, b] > 0 /\ me = buffersMapping[b]) (
        let { set of int: path = deopt(interconnectFromMemories[pe, me]); } in
        processesReadBuffer[p, b] * sum(ce in path) (1) * min(ce in path) (invBandwidthPerChannel[ce] div communicationReservation[pe, ce])
    )
);

constraint forall(p in Processes, pe in ListSchedulers where processesExecution[p] = pe) (
    durationWrite[p] = sum(b in Buffers, me in Memories where processesWriteBuffer[p, b] > 0 /\ me = buffersMapping[b]) (
        let { set of int: path = deopt(interconnectToMemories[pe, me]); } in
        processesWriteBuffer[p, b] * sum(ce in path) (1) * min(ce in path) (invBandwidthPerChannel[ce] div communicationReservation[pe, ce])
    )
);

constraint forall(p in Processes)(duration[p] = durationFetch[p] + executionTime[p, processesExecution[p]] + durationRead[p] + durationWrite[p]); % this must be improved later with communication

constraint forall(f, ff in Firings where f != ff /\ ff in follows[f]) (processesExecution[firingsActor[f]] = processesExecution[firingsActor[ff]] -> firingsOrdering[f] < firingsOrdering[ff]);

constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] = processesExecution[firingsActor[ff]] -> firingsOrdering[f] != firingsOrdering[ff]);

constraint forall(pe in ListSchedulers, f in Firings) (processesExecution[firingsActor[f]] = pe -> count_lt([processesExecution[firingsActor[ff]] | ff in Firings], pe, firingsOrdering[f]));

constraint nvalue(nUsedPEs, processesExecution);

constraint forall(f in Firings) (orderNext[f] != f);
constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] != processesExecution[firingsActor[ff]] -> orderNext[f] != ff);
constraint forall(f, ff in Firings where f != ff) (processesExecution[firingsActor[f]] = processesExecution[firingsActor[ff]] /\ firingsOrdering[f] + 1 = firingsOrdering[ff] <-> orderNext[f] = ff);


constraint forall(f in Firings) (maxPath[f, f] = duration[firingsActor[f]]);
constraint forall(f, ff in Firings where f != ff) (
  maxPath[f, ff] >= duration[firingsActor[f]] + max([0] ++ [maxPath[next, ff] | next in follows[f] where ff in follows[next]])
);
constraint forall(f, ff in Firings where f != ff) (
  let { var int: pe = processesExecution[firingsActor[f]] } in
  if pe in ListSchedulers then
    maxPath[f, ff] >= duration[firingsActor[f]] + maxPath[orderNext[f], ff]
  endif
);


constraint forall(p, pp in Processes where p != pp) (processesExecution[p] = processesExecution[pp] -> invThroughput[p] = invThroughput[pp]);
constraint forall(p, pp in Processes where p != pp) (connected[p, pp] -> invThroughput[p] = invThroughput[pp]);

constraint forall(a in Processes) (
    invThroughput[a] >= duration[a]
);

constraint forall(f, ff in Firings where f != ff /\ processesExecution[firingsActor[f]] = processesExecution[firingsActor[ff]] /\ firingsOrdering[f] < firingsOrdering[ff]) (
  invThroughput[firingsActor[f]] >= maxPath[f, ff]
);

constraint forall(solNum in 1..nPareto) (
  (previousSolutions[solNum, 1] > -1 -> nUsedPEs < previousSolutions[solNum, 1]) \/
  exists(proc in 2..(card(Processes)+1)) (
    (previousSolutions[solNum, proc] > -1 -> invThroughput[proc - 2] < previousSolutions[solNum, proc])
  )
);


solve 
  :: warm_start(processesExecution, [arg_min([executionTime[p, pe] | pe in Mappable where executionTime[p, pe] > 0]) | p in Processes]) 
  satisfy;
