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
% array[Processes, Mappable] of int: executionTime;

array[Processes] of var Mappable: processesExecution;
array[Processes] of var Memories: processesMapping;
array[Buffers] of var Memories: buffersMapping;
array[Mappable, Communications] of var 0..max(slots): communicationReservation;

array[Processes] of var int: invThroughput;
var int: nUsedPEs;

solve satisfy;