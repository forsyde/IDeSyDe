include "globals.mzn";

set of int: Processes;
set of int: Buffers;
set of int: Firings;
set of int: Messages;
set of int: Communications;
set of int: Tiles;

array[Firings] of int: firingsActor;
array[Firings] of int: firingsNumber;
array[Firings] of set of Firings: follows; % equivalent to f_i < f_j is precedes[i, j] = true
array[Firings] of set of Messages: sends;
array[Messages] of Firings: receiver;
array[Messages] of Firings: sender;
array[Messages] of Buffers: messageBuffer;

array[Communications] of int: slots;
array[Communications] of int frameSize;
array[Tiles] of int: memorySize;
array[Buffers] of int: bufferSize;
array[Processes, Tiles] of int: processesMemSize;
array[Processes, Buffers] of int: processesReadBuffer;
array[Processes, Buffers] of int: processesWriteBuffer;


array[Tiles, Tiles] of opt set of Communications: interconnectTo;
array[Processes, Tiles] of int: executionTime;
array[Communications] of int: invBandwidthPerChannel;

array[Processes, Processes] of bool: connected;

int: nPareto;
array[1..nPareto, 1..(card(Processes) + 1)] of int: previousSolutions;

int: numMaxPaths = card(Tiles)*(card(Tiles) + 1);

array[Processes] of var Tiles: processMapping;
array[Buffers] of var Memories: buffersMapping;
array[Firings] of var 0..(card(Firings)-1): firingsOrdering;
array[Messages] of var 0..(card(Messages)-1): messagesOrdering;
array[Tiles, Communications] of var 0..max(slots): communicationReservation;

array[Processes] of var int: duration;
array[Buffers] of var int: durationBufferMessage;

array[Firings] of var opt Firings: orderNext;
array[Messages] of var opt Messages: orderNextMessages;
array[Firings, numMaxPaths] of var int: maxPath;
array[Messages, numMaxPaths] of var int: maxPathMessages;

array[Processes] of var int: invThroughput;
var 0..card(Tiles): nUsedPEs;

constraint forall(p in Processes) (duration[p] >= min([e | e in executionTime[p, ..] where e >= 0]));

constraint forall(m in Tiles) (
  sum(p in Processes where processMapping[p] = m) (processesMemSize[p, processMapping[p]]) +
  sum(b in Buffers where buffersMapping[b] = m) (bufferSize[b])
  <= 
  memorySize[m]
);

constraint forall(src, dst in Processes, mSrc, mDst in Tiles, b in Buffers where src != dst /\ mSrc != mDst /\ processesReadBuffer[dst, b] > 0 /\ processesWriteBuffer[src, b] > 0 /\ absent(interconnectTo)) (
  processMapping[src] != mSrc \/ processMapping[dst] != mDst
);

constraint forall(src, dst in Processes, mSrc, mDst in Tiles, b in Buffers where src != dst /\ mSrc != mDst /\ processesReadBuffer[dst, b] > 0 /\ processesWriteBuffer[src, b] > 0 /\ occurs(interconnectTo) 
    /\ processMapping[src] = mSrc /\ processMapping[dst] = mDst) (
  let { set of int: path = deopt(interconnectTo[mSrc, mDst]); } in
  forall(ce in path) (communicationReservation[mSrc, ce] > 0)
);

constraint forall(src, dst in Processes, mSrc, mDst in Tiles, b in Buffers where src != dst /\ mSrc != mDst /\ processesReadBuffer[dst, b] > 0 /\ processesWriteBuffer[src, b] > 0 /\ occurs(interconnectTo) 
    /\ processMapping[src] = mSrc /\ processMapping[dst] = mDst) (
  let { set of int: path = deopt(interconnectTo[mSrc, mDst]); } in
  durationBufferMessage[b] = sum(ce in path) (frameSize[ce]) + processesWriteBuffer[src, b] * min(ce in path) (invBandwidthPerChannel[ce] div communicationReservation[pe, ce])
);

constraint forall(ce in Communications) (
  sum(pe in Mappable) (communicationReservation[pe, ce]) <= slots[ce]
);

constraint forall(p in Processes, pe in Mappable) (executionTime[p, pe] < 0 -> processMapping[p] != pe);

constraint forall(p in Processes)(duration[p] = executionTime[p, processMapping[p]]); % this must be improved later with communication

constraint forall(f, ff in Firings where f != ff /\ ff in follows[f]) (processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] -> firingsOrdering[f] < firingsOrdering[ff]);

constraint forall(f, ff in Firings where f != ff) (processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] -> firingsOrdering[f] != firingsOrdering[ff]);

constraint forall(pe in Tiles, f in Firings) (processMapping[firingsActor[f]] = pe -> count_lt([processMapping[firingsActor[ff]] | ff in Firings], pe, firingsOrdering[f]));

constraint nvalue(nUsedPEs, processMapping);

constraint forall(f in Firings) (orderNext[f] != f);
constraint forall(f, ff in Firings where f != ff) (processMapping[firingsActor[f]] != processMapping[firingsActor[ff]] -> orderNext[f] != ff);
constraint forall(f, ff in Firings where f != ff) (processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ firingsOrdering[f] + 1 = firingsOrdering[ff] <-> orderNext[f] = ff);

constraint forall(m in Messages) (orderNextMessages[m] != m);
constraint forall(m in Messages) (messagesOrdering[m] <= max(firingsOrdering[sender[m]], firingsOrdering[receiver[m]]));
constraint forall(m, mm in Messages where m != mm /\ firingsOrdering[sender[m]] < firingsOrdering[sender[mm]]) (messagesOrdering[m] < messagesOrdering[mm]);
constraint forall(m, mm in Messages where m != mm /\ firingsOrdering[sender[m]] = firingsOrdering[sender[mm]] /\ firingsOrdering[receiver[m]] < firingsOrdering[receiver[mm]]) (messagesOrdering[m] < messagesOrdering[mm]);
constraint forall(m, mm in Messages where m != mm /\ processMapping[firingsActor[sender[m]]] = processMapping[firingsActor[sender[mm]]] /\ processMapping[firingsActor[receiver[m]]] = processMapping[firingsActor[receiver[mm]]]) (messagesOrdering[m] + 1 = messagesOrdering[mm] <-> orderNextMessages[m] = mm);

constraint forall(f in Firings) (maxPath[f, f] = duration[firingsActor[f]]);
constraint forall(f, ff in Firings where f != ff) (
  maxPath[f, ff] >= duration[firingsActor[f]] + max([0] ++ [maxPath[next, ff] | next in follows[f] where ff in follows[next]])
);
constraint forall(f, ff in Firings where f != ff) (
  let { var int: pe = processMapping[firingsActor[f]] } in
  if pe in Tiles then
    maxPath[f, ff] >= duration[firingsActor[f]] + maxPath[orderNext[f], ff]
  endif
);


constraint forall(p, pp in Processes where p != pp) (processMapping[p] = processMapping[pp] -> invThroughput[p] = invThroughput[pp]);
constraint forall(p, pp in Processes where p != pp) (connected[p, pp] -> invThroughput[p] = invThroughput[pp]);

constraint forall(a in Processes) (
    invThroughput[a] >= duration[a]
);

constraint forall(f, ff in Firings where f != ff /\ processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ firingsOrdering[f] < firingsOrdering[ff]) (
  invThroughput[firingsActor[f]] >= maxPath[f, ff]
);

constraint forall(solNum in 1..nPareto) (
  (previousSolutions[solNum, 1] > -1 -> nUsedPEs < previousSolutions[solNum, 1]) \/
  exists(proc in 2..(card(Processes)+1)) (
    (previousSolutions[solNum, proc] > -1 -> invThroughput[proc - 2] < previousSolutions[solNum, proc])
  )
);


solve 
  :: warm_start(processMapping, [arg_min([executionTime[p, pe] | pe in Mappable where executionTime[p, pe] > 0]) | p in Processes]) 
  satisfy;
