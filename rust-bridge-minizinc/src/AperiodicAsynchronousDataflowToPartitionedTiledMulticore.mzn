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
array[Communications] of int: frameSize;
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

int: AllCycles = 0..2*card(Tiles)-1;
set of int: TileCycles = 0..card(Tiles)-1;
set of int: NICycles = card(Tiles)..2*card(Tiles)-1;

array[Processes] of var Tiles: processMapping;
array[Buffers] of var Tiles: buffersMapping;
array[Firings] of var 0..(card(Firings)-1): firingsOrdering;
array[Tiles, Communications] of var 0..max(slots): communicationReservation;
array[Messages] of var 0..(card(Messages)-1): messagesOrdering;

array[Processes] of var int: duration;
array[Buffers] of var int: durationBufferMessage;

array[Firings] of var opt Firings: orderNext;
array[Messages] of var opt Messages: orderNextMessages;
array[Firings, AllCycles] of var int: maxPath;
array[Messages, AllCycles] of var int: maxPathMessages;

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
constraint forall(m, mm in Messages where m != mm /\ firingsOrdering[sender[m]] < firingsOrdering[sender[mm]]) (messagesOrdering[m] < messagesOrdering[mm]);
constraint forall(m, mm in Messages where m != mm /\ firingsOrdering[sender[m]] = firingsOrdering[sender[mm]] /\ firingsOrdering[receiver[m]] < firingsOrdering[receiver[mm]]) (messagesOrdering[m] < messagesOrdering[mm]);
constraint forall(m, mm in Messages where m != mm /\ processMapping[firingsActor[sender[m]]] = processMapping[firingsActor[sender[mm]]]) (messagesOrdering[m] + 1 = messagesOrdering[mm] <-> orderNextMessages[m] = mm);
constraint forall(pe in Tiles, m in Messages) (buffersMapping[messageBuffer[m]] = pe -> count_lt([buffersMapping[messageBuffer[mm]] | mm in Messages], pe, messagesOrdering[m]));

constraint forall(f in Firings, pe in TileCycles where firingsOrdering[f] = 0 /\ processMapping[firingsActor[f]] = pe) (maxPath[f, pe] = duration[firingsActor[f]]);
constraint forall(m in Messages, l in NICycles where messagesOrdering[m] = 0 /\ buffersMapping[messageBuffer[m]] = l - card(Tiles)) (maxPathMessages[m, l] = durationBufferMessage[messageBuffer[m]]);
constraint forall(f in Firings, pe in AllCycles where firingsOrdering[f] > 0 \/ processMapping[firingsActor[f]] != pe) (
  maxPath[f, pe] >= duration[firingsActor[f]] + max([0] ++ [maxPathMessages[pe, m] | m in Messages where receiver[m] = f])
);
constraint forall(f, ff in Firings, pe in AllCycles where f != ff /\ firingsOrdering[f] > 0 /\ processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ firingsOrdering[f] = firingsOrdering[ff] + 1) (
  maxPath[f, pe] >= duration[firingsActor[f]] + maxPath[ff, pe]
);
constraint forall(m in Messages, pe in AllCycles) (
  maxPathMessages[m, pe] >= durationBufferMessage[messageBuffer[m]] + maxPath[sender[m], pe]
);
constraint forall(m, mm in Messages, pe in AllCycles where m != mm /\ messagesOrdering[m] > 0 /\ buffersMapping[messageBuffer[m]] = buffersMapping[messageBuffer[mm]] /\ messagesOrdering[m] = messagesOrdering[mm] + 1) (
  maxPathMessages[m, pe] >= durationBufferMessage[messageBuffer[m]] + maxPathMessages[mm, pe]
);


constraint forall(p, pp in Processes where p != pp) (processMapping[p] = processMapping[pp] -> invThroughput[p] = invThroughput[pp]);
constraint forall(p, pp in Processes where p != pp) (connected[p, pp] -> invThroughput[p] = invThroughput[pp]);

constraint forall(a in Processes) (
    invThroughput[a] >= duration[a] + sum(b in Buffers where processesWriteBuffer[a, b] > 0) (durationBufferMessage[b])
);

constraint forall(f in Firings, pe in TileCycles) (
  invThroughput[firingsActor[f]] >= maxPath[f, pe]
);
constraint forall(m in Messages, pe in NICycles) (
  invThroughput[firingsActor[sender[m]]] >= maxPathMessages[m, pe]
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
