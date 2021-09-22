include "globals.mzn";

% objectives
int: objLambda;

% model parameters
int: hyperPeriod;
int: nReactors;
int: nReactions;
int: nChannels;
int: nJobs;
int: nJobChannels;

set of int: Reactors = 1..nReactors;
set of int: Reactions = 1..nReactions;
set of int: Channels = 1..nChannels;
set of int: Jobs = 1..nJobs;
set of int: JobChannels = 1..nJobChannels;


% platform elements
int: nPlatformElems;
set of int: PlatformElems = 1..nPlatformElems;
array[PlatformElems] of bool: isTimeTriggeredElem;
array[PlatformElems] of bool: isRoundRobinElem;
array[PlatformElems] of bool: isProcessingElem;
array[PlatformElems] of bool: isCommunicationElem;
array[PlatformElems] of bool: isMemoryElem;

% inferred sets
set of int: TimeTriggeredElems = {p | p in PlatformElems where isTimeTriggeredElem[p]};
set of int: RoundRobinElems = {p | p in PlatformElems where isRoundRobinElem[p]};
set of int: ProcessingElems = {p | p in PlatformElems where isProcessingElem[p]};
set of int: CommunicationElems = {p | p in PlatformElems where isCommunicationElem[p]};
set of int: MemoryElems = {p | p in PlatformElems where isMemoryElem[p]};


% goal elements
int: nJobChains;
set of int: JobChains = 1..nJobChains;

% reactors
array[Reactors] of int: reactorSize;
% reactions
array[Reactions] of Reactors: containingReactor;
% channels 
array[Channels] of int: channelSize;
array[Channels] of Reactions: channelSrc;
array[Channels] of Reactions: channelDst;
% jobs 
array[Jobs] of Reactions: originalReaction;
array[Jobs] of int: jobRelease;
array[Jobs] of int: jobDeadline;
array[Jobs] of int: jobDataSize;
array[Jobs, PlatformElems] of int: jobWcet;
array[Jobs, PlatformElems] of int: jobUtilization;
array[Jobs, PlatformElems] of bool: jobCanBeExecuted;
%% the masks are defined as follows:
%%   if a job `srcJob` is the starting point og 
% job channels
array[JobChannels] of Jobs: jobChannelSrc;
array[JobChannels] of Jobs: jobChannelDst;
% for platform
array[PlatformElems] of int: roundRobinElemsMinSlice;
array[PlatformElems] of int: roundRobinElemsMaxSlice;
array[PlatformElems, PlatformElems, PlatformElems] of int: platformPaths;
array[PlatformElems, PlatformElems] of int: allocatedBandwidth;
array[PlatformElems] of int: memoryMaximumCapacity;
% goal elements
array[JobChains] of Jobs: firstInChain;
array[JobChains] of Jobs: lastInChain;

% main decision variables
array[Reactors] of var MemoryElems: reactorMapping;
array[Channels] of var MemoryElems: channelMapping;
array[Jobs] of var ProcessingElems: jobExecution;
%% tighten bonds of jobExecution
constraint forall(j in Jobs, p in ProcessingElems) (
  jobCanBeExecuted[j, p] = false -> jobExecution[j] != p
);
array[Jobs, PlatformElems] of var 0..hyperPeriod: jobStartingTime;
%% tighten bound of jobStartingTime:
constraint forall(j in Jobs, p in ProcessingElems) (
  jobRelease[j] <= jobStartingTime[j, p] /\ 
  jobStartingTime[j, p] <= jobDeadline[j]
);
array[Jobs, PlatformElems] of var 0..max(roundRobinElemsMaxSlice): jobTimeSlice;
%% tighten bound of jobTimeSlice:
constraint forall(j in Jobs, p in RoundRobinElems) (
%   0 <= jobTimeSlice[j, p] /\
  jobTimeSlice[j, p] <= min([jobWcet[j, p], roundRobinElemsMaxSlice[p]])
);

% auxiliary decision variables
array[Jobs, PlatformElems] of var int: jobInstDuration;
%% tighten the bounds for jobInstDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(j in Jobs, pe in ProcessingElems) (
  0 <= jobInstDuration[j, pe] /\
  jobInstDuration[j, pe] <=
  max([
    sum(ce in CommunicationElems where 1 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (jobDataSize[j] / allocatedBandwidth[ce, pe])
    | mem in MemoryElems
  ])
);

array[Jobs, PlatformElems] of var int: jobDataDuration;
%% tighten the bounds for jobDataDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(j in Jobs, c in Channels, pe in ProcessingElems where channelDst[c] = originalReaction[j]) (
  0 <= jobDataDuration[j, pe] /\
  jobDataDuration[j, pe] <=
  max([
    sum(ce in CommunicationElems where 1 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
    | mem in MemoryElems
  ])
);

array[Jobs, PlatformElems] of var int: jobExeDuration;
%% tighten the bounds for jobExeDuration
constraint forall(j in Jobs, pe in TimeTriggeredElems) (
  jobExeDuration[j, pe] in {0, jobWcet[j, pe]}
);
constraint forall(j in Jobs, pe in RoundRobinElems) (
  0 <= jobExeDuration[j, pe] %/\
%   jobExeDuration[j, pe] <= jobWcet[j, pe] * 
);

array[Jobs, PlatformElems] of var int: jobOutDuration;
%% tighten the bounds for jobOutDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(j in Jobs, c in Channels, pe in ProcessingElems where channelSrc[c] = originalReaction[j]) (
  0 <= jobOutDuration[j, pe] /\
  jobOutDuration[j, pe] <=
  max([
    sum(ce in CommunicationElems where 1 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
    | mem in MemoryElems
  ])
);
array[Jobs, PlatformElems] of var int: jobDuration;
%% make the equality and tighten the bounds for jobDuration
constraint forall(j in Jobs, p in ProcessingElems) (
  jobDuration[j, p] = jobInstDuration[j, p] + jobDataDuration[j, p] + jobExeDuration[j, p] + jobOutDuration[j, p]
);
% constraint forall(j in Jobs, p in ProcessingElems) (
%   jobRelease[j] + jobWcet[j, p] <= jobInstDuration[j, p] + jobExeDuration[j, p] + jobOutDuration[j, p]
% );

array[PlatformElems] of var int: roundRobinPeriod;
%% tighten the bounds for roundRobinPeriod
constraint forall(pe in RoundRobinElems) (
  0 <= roundRobinPeriod[pe]
%    /\ roundRobinPeriod[pe] <= sum(j in Jobs where jobCanBeExecuted[j, pe]) (min([jobWcet[j, pe], roundRobinElemsMaxSlice[pe]]))
);

% objectives
int: minUtilization = sum(j in Jobs) (
  min([jobUtilization[j, p] | p in ProcessingElems])
);
int: maxUtilization = sum(j in Jobs) (
  max([jobUtilization[j, p] | p in ProcessingElems])
);
var minUtilization..maxUtilization: totalUtilization;
% int: minLatency = sum(l in JobChains) (
%   min(jobWcet[lastInChain[l], ..]) + jobRelease[lastInChain[l]] - jobDeadline[firstInChain[l]]
% );
% int: maxLatency = sum(l in JobChains) (
%   jobDeadline[lastInChain[l]] - jobRelease[firstInChain[l]] - max(jobWcet[firstInChain[l], ..])
% );
% array[JobChains] of var int: latencies;
%% tighten the bounds
% constraint forall(l in JobChains) (
%   min(jobWcet[lastInChain[l], ..]) + jobRelease[lastInChain[l]] - jobDeadline[firstInChain[l]] <= latencies[l]  /\
%   latencies[l] <= jobDeadline[lastInChain[l]] - jobRelease[firstInChain[l]] - max(jobWcet[firstInChain[l], ..])
% );
% var int: totalLatencies;


% baseline constraints
%% memory contraints
% constraint bin_packing_capa(
%   memoryMaximumCapacity,
%   reactorMapping ++ channelMapping,
%   reactorSize ++ channelSize
% );

% data movements constraints
%% instruction
% constraint forall(j in Jobs, mem in MemoryElems, pe in ProcessingElems) (
%   let {int: r = originalReaction[j]; int: a = containingReactor[r]} in
%     if reactorMapping[a] = mem /\ jobExecution[j] = pe then
%       jobInstDuration[j, pe] = sum(ce in CommunicationElems where 1 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
%         (jobDataSize[j] / allocatedBandwidth[ce, pe])
%     else
%       jobInstDuration[j, pe] = 0
%     endif
% );
%% data
% constraint forall(j in Jobs, c in Channels, mem in MemoryElems, pe in ProcessingElems where channelDst[c] = originalReaction[j]) (
%   let {int: r = originalReaction[j]; int: a = containingReactor[r]} in
%   if reactorMapping[a] = mem /\ jobExecution[j] = pe then
%     jobDataDuration[j, pe] = sum(ce in CommunicationElems where 1 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
%       (channelSize[c] / allocatedBandwidth[ce, pe])
%   else
%     jobDataDuration[j, pe] = 0
%   endif
% );
%% write
% constraint forall(j in Jobs, c in Channels, mem in MemoryElems, pe in ProcessingElems where channelSrc[c] = originalReaction[j]) (
%   let {int: r = originalReaction[j]; int: a = containingReactor[r]} in
%   if reactorMapping[a] = mem /\ jobExecution[j] = pe then
%     jobOutDuration[j, pe] = sum(ce in CommunicationElems where 0 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem]) 
%       (channelSize[c] / allocatedBandwidth[ce, pe])
%   else
%     jobDataDuration[j, pe] = 0
%   endif
% );

% time-triggered constraints
% constraint forall(p in TimeTriggeredElems) (
%   disjunctive(
%   [jobStartingTime[j, p] | j in Jobs],
%   [jobDuration[j, p] | j in Jobs])
% );
%% implied constraint
% constraint cumulative(
%   [jobStartingTime[j, p] | j in Jobs, p in TimeTriggeredElems], 
%   [jobDuration[j, p] | j in Jobs, p in TimeTriggeredElems], 
%   [1 | j in Jobs, p in ProcessingElems], 
%   length(TimeTriggeredElems)
%   );
%% when to count execution
% constraint forall(j in Jobs, p in TimeTriggeredElems) (
%   if (jobExecution[j] = p) then 
%     jobExeDuration[j, p] = jobWcet[j, p]
%   else
%     jobExeDuration[j, p] = 0
%   endif
% );

% round-robin constraints
%% round-robin period
% constraint forall(p in RoundRobinElems) (
%   sum(jobTimeSlice[.., p]) <= roundRobinPeriod[p]
% );
%% minimum time slice allocation
% constraint forall(j in Jobs, p in RoundRobinElems) (
%   if(jobExecution[j] = p) then 
%     roundRobinElemsMinSlice[p] <= jobTimeSlice[j, p]
%   endif
% );
%% exe duration calculation
% constraint forall(j in Jobs, p in RoundRobinElems) (
%   if (jobExecution[j] = p) then
%   roundRobinPeriod[p] * jobWcet[j, p] = jobTimeSlice[j, p] * jobExeDuration[j, p]
%   endif
% );

% precedence constraints
% set of int: JobPrecedenceChannels = JobPureChannels union JobInnerStateChannels;
% constraint forall(c in JobChannels, j, jj in Jobs, p, pp in ProcessingElems where jobChannelSrc[c] = j /\ jobChannelDst[c] = jj) (
%   jobStartingTime[j, p] + jobDuration[j, p] <= jobDuration[jj, pp]
% );

% objectives
% constraint totalUtilization = sum(p in ProcessingElems, j in Jobs where jobExecution[j] = p) (jobUtilization[j, p]);
% constraint totalLatencies = sum(l in JobChains) (latencies[l]);


solve
  % :: warm_start(start, [i mod length(procs) | i in jobs])
  :: int_search(jobStartingTime, first_fail, indomain_min)
  :: int_search(jobTimeSlice, first_fail, indomain_max)
  :: int_search(roundRobinPeriod, first_fail, indomain_min)
  :: restart_luby(length(Jobs))
  minimize totalUtilization ; %+ objLambda * totalLatencies;

% output [show(max_makespan), "\n", show(objective), "\n", show2d(start), "\n", show(job_min_latency)];
