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
% array[Firings] of set of Messages: sends;
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

array[Tiles, Tiles] of bool: hasInterconnectTo;
array[Tiles, Tiles] of set of Communications: interconnectTo;
array[Processes, Tiles] of int: executionTime;
array[Communications] of int: invBandwidthPerChannel;

array[Processes, Processes] of bool: connected;

int: nPareto;
array[1..nPareto, 1..(card(Processes) + 1)] of int: previousSolutions;

set of int: AllCycles = 0..2*card(Tiles)-1;
set of int: TileCycles = 0..card(Tiles)-1;
set of int: NICycles = card(Tiles)..2*card(Tiles)-1;

array[Processes] of var Tiles: processMapping;
array[Firings] of var 0..(card(Firings)-1): firingsOrdering;
array[Buffers] of var Tiles: buffersMapping;
% array[Messages] of var 0..(card(Messages)-1): messagesOrdering;
array[Tiles, Communications] of var 0..max(slots): communicationReservation;

array[Processes] of var int: duration;
array[Buffers] of var int: durationBufferMessage;

% array[Firings] of var opt Firings: orderNext;
% array[Messages] of var opt Messages: orderNextMessages;
array[Firings, Firings] of var int: maxPath;
array[Firings, Firings] of var int: maxPathMessages;

array[Processes] of var int: invThroughput;
var 0..card(Tiles): nUsedPEs;

constraint forall(p in Processes) (duration[p] >= min([e | e in executionTime[p, ..] where e >= 0]));
constraint forall(p in Processes) (duration[p] <= max([e | e in executionTime[p, ..] where e >= 0]));
constraint forall(b in Buffers) (durationBufferMessage[b] >= 0);
constraint forall(b in Buffers) (durationBufferMessage[b] <= max(src in Processes, mSrc, mDst in Tiles where mSrc != mDst /\ hasInterconnectTo[mSrc, mDst] /\ processesWriteBuffer[src, b] > 0) (
  let { set of int: path = interconnectTo[mSrc, mDst]; } in
  sum(ce in path) (frameSize[ce]) + processesWriteBuffer[src, b] * min(ce in path) (invBandwidthPerChannel[ce])
));
int: maxTh = sum(p in Processes) (max([e | e in executionTime[p, ..] where e >= 0]) + sum(b in Buffers, mSrc, mDst in Tiles where mSrc != mDst /\ hasInterconnectTo[mSrc, mDst] /\ processesWriteBuffer[p, b] > 0) (
  let { set of int: path = interconnectTo[mSrc, mDst]; } in
  sum(ce in path) (frameSize[ce]) + processesWriteBuffer[p, b] * min(ce in path) (invBandwidthPerChannel[ce])
));
constraint forall(p in Processes) (invThroughput[p] <= maxTh);
constraint forall(p in Processes) (invThroughput[p] >= min([e | e in executionTime[p, ..] where e >= 0]));
constraint forall(f, ff in Firings) (maxPath[f, ff] >= 0.0);
constraint forall(f, ff in Firings) (maxPathMessages[f, ff] >= 0.0);

constraint forall(p in Processes, pe in Tiles where executionTime[p, pe] < 0) (processMapping[p] != pe);

constraint forall(m in Tiles) (
  sum(p in Processes where processMapping[p] = m) (processesMemSize[p, processMapping[p]]) +
  sum(b in Buffers where buffersMapping[b] = m) (bufferSize[b])
  <= 
  memorySize[m]
);

constraint forall(p in Processes, b in Buffers where processesReadBuffer[p, b] > 0) (
  processMapping[p] = buffersMapping[b]
);

constraint forall(src, dst in Processes, mSrc, mDst in Tiles, b in Buffers where src != dst /\ mSrc != mDst /\ processesReadBuffer[dst, b] > 0 /\ processesWriteBuffer[src, b] > 0 /\ not hasInterconnectTo[mSrc, mDst]) (
  processMapping[src] != mSrc \/ processMapping[dst] != mDst
);

constraint forall(src, dst in Processes, mSrc, mDst in Tiles, b in Buffers where src != dst /\ mSrc != mDst /\ processesReadBuffer[dst, b] > 0 /\ processesWriteBuffer[src, b] > 0 /\ hasInterconnectTo[mSrc, mDst] 
    /\ processMapping[src] = mSrc /\ processMapping[dst] = mDst) (
  let { set of int: path = deopt(interconnectTo[mSrc, mDst]); } in
  forall(ce in path) (communicationReservation[mSrc, ce] > 0)
);

constraint forall(src, dst in Processes, mSrc, mDst in Tiles, b in Buffers where src != dst /\ mSrc != mDst /\ processesReadBuffer[dst, b] > 0 /\ processesWriteBuffer[src, b] > 0 /\ hasInterconnectTo[mSrc, mDst]
    /\ processMapping[src] = mSrc /\ processMapping[dst] = mDst) (
  let { set of int: path = deopt(interconnectTo[mSrc, mDst]); } in
  durationBufferMessage[b] = sum(ce in path) (frameSize[ce]) + processesWriteBuffer[src, b] * min(ce in path) (invBandwidthPerChannel[ce] div communicationReservation[mSrc, ce])
);

constraint forall(ce in Communications) (
  sum(pe in Tiles) (communicationReservation[pe, ce]) <= slots[ce]
);


constraint forall(p in Processes, pe in Tiles where executionTime[p, pe] < 0) (processMapping[p] != pe);

constraint forall(p in Processes)(duration[p] = executionTime[p, processMapping[p]]); % this must be improved later with communication

constraint forall(f, ff in Firings where f != ff /\ ff in follows[f]) (processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] -> firingsOrdering[f] < firingsOrdering[ff]);

% constraint forall(f, ff in Firings where f != ff) (processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] -> firingsOrdering[f] != firingsOrdering[ff]);
constraint diffn([processMapping[firingsActor[f]]| f in Firings], [firingsOrdering[f] | f in Firings], [1 | f in Firings], [1 | f in Firings]);
constraint forall(pe in Tiles, f in Firings) (processMapping[firingsActor[f]] = pe -> count_lt([processMapping[firingsActor[ff]] | ff in Firings], pe, firingsOrdering[f]));

constraint nvalue(nUsedPEs, processMapping);

% constraint forall(f in Firings) (orderNext[f] != f);
% constraint forall(f, ff in Firings where f != ff) (processMapping[firingsActor[f]] != processMapping[firingsActor[ff]] -> orderNext[f] != ff);
% constraint forall(f, ff in Firings where f != ff) (processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ firingsOrdering[f] + 1 = firingsOrdering[ff] <-> orderNext[f] = ff);

% constraint forall(m in Messages) (orderNextMessages[m] != m);
% constraint forall(m, mm in Messages where m != mm /\ buffersMapping[messageBuffer[sender[m]]] = buffersMapping[messageBuffer[sender[mm]]]) (messagesOrdering[m] != messagesOrdering[mm]);
% constraint diffn([buffersMapping[messageBuffer[m]] | m in Messages], [messagesOrdering[m] | m in Messages], [1 | m in Messages], [1 | m in Messages]);
% constraint forall(m, mm in Messages where m != mm /\ firingsOrdering[sender[m]] < firingsOrdering[sender[mm]]) (messagesOrdering[m] < messagesOrdering[mm]);
% constraint forall(m, mm in Messages where m != mm /\ firingsOrdering[sender[m]] = firingsOrdering[sender[mm]] /\ firingsOrdering[receiver[m]] < firingsOrdering[receiver[mm]]) (messagesOrdering[m] < messagesOrdering[mm]);
% constraint forall(m, mm in Messages where m != mm /\ processMapping[firingsActor[sender[m]]] = processMapping[firingsActor[sender[mm]]]) (messagesOrdering[m] + 1 = messagesOrdering[mm] <-> orderNextMessages[m] = mm);
% constraint forall(pe in Tiles, m in Messages) (buffersMapping[messageBuffer[m]] = pe -> count_lt([buffersMapping[messageBuffer[mm]] | mm in Messages], pe, messagesOrdering[m]));

% constraint forall(f in Firings, pe in Tiles where firingsOrdering[f] = 0 /\ processMapping[firingsActor[f]] = pe) (maxPath[f, pe] = duration[firingsActor[f]]);
% constraint forall(f in Firings, pe in Tiles where firingsOrdering[f] = 0 /\ processMapping[firingsActor[f]] = pe) (maxPathMessages[f, pe] = sum(m in Messages where sender[m] = f) (durationBufferMessage[messageBuffer[m]]));

% constraint forall(f in Firings, pe in Tiles where firingsOrdering[f] > 0 \/ processMapping[firingsActor[f]] != pe) (
%   let {
%     var int: incomingMessages = max([0] ++ [duration[firingsActor[f]] + maxPathMessages[ff, pe] | m in Messages, ff in Firings where receiver[m] = f /\ sender[m] = ff]);
%     var int: previousFirings = max([0] ++ [duration[firingsActor[f]] +  maxPath[ff, pe] | ff in Firings where processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ firingsOrdering[f] > firingsOrdering[ff]]);
%   } in
%   maxPath[f, pe] = max([incomingMessages, previousFirings])
% );
constraint forall(f in Firings) (
  maxPath[f, f] = duration[firingsActor[f]]
);
constraint forall(f in Firings) (
  maxPathMessages[f, f] = sum(b in Buffers where processesWriteBuffer[firingsActor[f], b] > 0) (durationBufferMessage[b])
);
constraint forall(fi, fj, fk in Firings where fi != fj /\ fk in follows[fi]) (
  maxPath[fi, fj] >= duration[firingsActor[fi]] + maxPathMessages[fk, fj]
);
constraint forall(fi, fj, fk in Firings where fi != fj /\ fi != fk /\ processMapping[firingsActor[fi]] = processMapping[firingsActor[fk]] /\ firingsOrdering[fk] > firingsOrdering[fi]) (
  maxPath[fi, fj] >= duration[firingsActor[fi]] + maxPath[fk, fj]
);

% constraint forall(f in Firings, pe in Tiles) ( % where firingsOrdering[f] > 0 \/ processMapping[firingsActor[f]] != pe) (
%   let {
%     var int: previousMessages = max([0] ++ [maxPathMessages[ff, pe] | ff in Firings, m in Messages where processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ firingsOrdering[f] > firingsOrdering[ff]]);
%   } in
%   maxPathMessages[f, pe] = sum(m in Messages where sender[m] = f) (durationBufferMessage[messageBuffer[m]]) + max([maxPath[f, pe], previousMessages])
% );
constraint forall(fi, fj in Firings, m in Messages where fi != fj /\ sender[m] = fi) (
  maxPathMessages[fi, fj] >= durationBufferMessage[messageBuffer[m]] + maxPath[receiver[m], fj]
);
constraint forall(fi, fj, fk in Firings where fi != fj /\ fi != fk /\ processMapping[firingsActor[fi]] = processMapping[firingsActor[fk]] /\ firingsOrdering[fk] > firingsOrdering[fi]) (
  maxPathMessages[fi, fj] >= sum(m in Messages where sender[m] = fi) (durationBufferMessage[messageBuffer[m]]) + maxPathMessages[fk, fj]
);


constraint forall(p, pp in Processes where p != pp) (processMapping[p] = processMapping[pp] -> invThroughput[p] = invThroughput[pp]);
constraint forall(p, pp in Processes where p != pp /\ connected[p, pp]) (invThroughput[p] = invThroughput[pp]);

% constraint forall(a in Processes) (
%   let {
%     var int: selfCycle = duration[a] + sum(b in Buffers where processesWriteBuffer[a, b] > 0) (durationBufferMessage[b]);
%     % var int: maxPathConnected = max(f in Firings, aa in Processes where firingsActor[f] = aa /\ connected[a, aa]) (maxPath[f, processMapping[a]]);
%     % var int: maxPathMessagesConnected = max(f in Firings, aa in Processes where firingsActor[f] = aa /\ connected[a, aa]) (maxPathMessages[f, processMapping[a]]);
%     var int: maxThConnected = max(aa in Processes where a != aa /\ connected[a, aa]) (invThroughput[aa]);
%     var int: maxPathSameTile = max(f in Firings, aa in Processes where firingsActor[f] = aa /\ processMapping[a] = processMapping[aa]) (maxPath[f, processMapping[a]]);
%     var int: maxPathMessageSameTile = max(f in Firings, aa in Processes where firingsActor[f] = aa /\ processMapping[a] = processMapping[aa]) (maxPathMessages[f, processMapping[a]]);
%   } in
%   maximum(invThroughput[a], [selfCycle, maxPathSameTile, maxPathMessageSameTile, maxThConnected])
%   % invThroughput[a] = max([selfCycle, maxPathSameTile, maxPathMessageSameTile, maxThConnected])
% );

constraint forall(a in Processes) (
  invThroughput[a] >= duration[a] + sum(b in Buffers where processesWriteBuffer[a, b] > 0) (durationBufferMessage[b])
);

constraint forall(f, ff in Firings where processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ f != ff) (
  invThroughput[firingsActor[f]] >= maxPath[f, ff]
);
constraint forall(f, ff in Firings where processMapping[firingsActor[f]] = processMapping[firingsActor[ff]] /\ f != ff) (
  invThroughput[firingsActor[f]] >= maxPathMessages[f, ff]
);


constraint forall(solNum in 1..nPareto) (
  (if previousSolutions[solNum, 1] > -1 then nUsedPEs < previousSolutions[solNum, 1] else false endif) \/
  exists(proc in 2..(card(Processes)+1) where previousSolutions[solNum, proc] > -1) (
    (invThroughput[proc - 2] < previousSolutions[solNum, proc])
  )
);


solve
  :: seq_search(
    [
      int_search(
        processMapping, first_fail, indomain_random
      ),
      int_search(
        firingsOrdering, first_fail, indomain_min
      ),
      int_search(
        buffersMapping, first_fail, indomain_random
      ),
      int_search(
        communicationReservation, first_fail, indomain_min
      ),
      int_search(
    duration, first_fail, indomain_min
  ),
  int_search(
    durationBufferMessage, first_fail, indomain_min
  ),
      int_search(
    maxPath, input_order, indomain_min
  ),
  int_search(
    maxPathMessages, input_order, indomain_min
  ),
      int_search(
            invThroughput, input_order, indomain_min
        )
    ]
  ) 
  :: warm_start(processMapping, [arg_min([executionTime[p, pe] | pe in Tiles where executionTime[p, pe] > 0]) | p in Processes]) 
  % :: restart_linear(card(Firings) * card(Tiles))
  satisfy;
