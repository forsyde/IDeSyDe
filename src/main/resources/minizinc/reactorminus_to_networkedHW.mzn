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
array[PlatformElems] of bool: isFixedPriorityElem;
array[PlatformElems] of bool: isTimeTriggeredElem;
array[PlatformElems] of bool: isRoundRobinElem;
array[PlatformElems] of bool: isProcessingElem;
array[PlatformElems] of bool: isCommunicationElem;
array[PlatformElems] of bool: isMemoryElem;
array[PlatformElems] of int: platformElemsSymmetryGroups;

% inferred sets
set of int: TimeTriggeredElems = {p | p in PlatformElems where isTimeTriggeredElem[p]};
set of int: FixedPriorityElems = {p | p in PlatformElems where isFixedPriorityElem[p]};
set of int: RoundRobinElems = {p | p in PlatformElems where isRoundRobinElem[p]};
set of int: ProcessingElems = {p | p in PlatformElems where isProcessingElem[p]};
set of int: CommunicationElems = {p | p in PlatformElems where isCommunicationElem[p]};
set of int: MemoryElems = {p | p in PlatformElems where isMemoryElem[p]};


% goal elements
int: nReactionChains;
set of int: ReactionChains = 1..nReactionChains;

% reactors
array[Reactors] of int: reactorSize;
% reactions
array[Reactions] of Reactors: containingReactor;
array[Reactions] of int: reactionDataSize;
array[Reactions, PlatformElems] of int: reactionWcet;
array[Reactions, PlatformElems] of int: reactionUtilization;
array[Reactions, PlatformElems] of bool: reactionCanBeExecuted;
% channels 
array[Channels] of int: channelSize;
array[Channels] of Reactions: channelSrc;
array[Channels] of Reactions: channelDst;
% jobs 
array[Jobs] of Reactions: originalReaction;
array[Jobs] of int: jobRelease;
array[Jobs] of int: jobDeadline;
array[Jobs, Jobs] of bool: jobHigherPriority; % jobHigherPriority[j, jj] <-> j >> jj <-> j has higher prio than jj
array[Jobs, Jobs] of bool: jobInterferes;
function int: jobDataSize(Jobs: job) = reactionDataSize[containingReactor[job]];
function float: jobWcet(Jobs: job, PlatformElems: p) = reactionWcet[originalReaction[job], p];
function int: jobUtilization(Jobs: job, PlatformElems: p) = reactionUtilization[originalReaction[job], p];
function bool: jobCanBeExecuted(Jobs: job, PlatformElems: p) = reactionCanBeExecuted[originalReaction[job], p];
%% the masks are defined as follows:
%%   if a job `srcJob` is the starting point og 
% job channels
array[JobChannels] of Jobs: jobChannelSrc;
array[JobChannels] of Jobs: jobChannelDst;
% for platform
% array[PlatformElems] of int: roundRobinElemsMinSlice;
array[PlatformElems] of int: roundRobinElemsMaxSlices;
array[PlatformElems, PlatformElems, PlatformElems] of int: platformPaths;
array[PlatformElems, PlatformElems] of int: allocatedBandwidth;
array[PlatformElems] of int: memoryMaximumCapacity;
% goal elements
% array[ReactionChains, Reactions] of bool: shouldSumResponseTime;
% array[ReactionChains] of int: reactionChainStaticLatency;

% main decision variables
array[Reactors] of var MemoryElems: reactorMapping;
array[Channels] of var MemoryElems: channelMapping;
array[Reactions] of var ProcessingElems: reactionExecution;
%% tighten bonds of reactionExecution
constraint forall(j in Reactions, p in ProcessingElems where reactionCanBeExecuted[j, p] = false) (
  reactionExecution[j] != p
);
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionResponseTime;
%% tighten bound of jobStartingTime:
constraint forall(j in Jobs, p in ProcessingElems) (
  0 <= reactionResponseTime[originalReaction[j], p] /\
  reactionResponseTime[originalReaction[j], p] <= jobDeadline[j] - jobRelease[j]
);
constraint forall(r in Reactions, p in PlatformElems where not(p in ProcessingElems)) (
  reactionResponseTime[r, p] = 0
);
array[Reactions, PlatformElems] of var 0..max(roundRobinElemsMaxSlices): reactionRRSlices;
%% tighten bound of reactionRRSlices:
constraint forall(r in Reactions, p in RoundRobinElems) (
   0 <= reactionRRSlices[r, p] /\ reactionRRSlices[r, p] <= roundRobinElemsMaxSlices[p]
  % reactionRRSlices[r, p] <= min([reactionWcet[r, p], roundRobinElemsMaxSlices[p]])
);
constraint forall(r in Reactions, p in PlatformElems where not(p in RoundRobinElems)) (
  reactionRRSlices[r, p] = 0
  % reactionRRSlices[r, p] <= min([reactionWcet[r, p], roundRobinElemsMaxSlices[p]])
);

% auxiliary decision variables
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionInstDuration;
%% tighten the bounds for reactionInstDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionInstDuration[r, pe]
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  reactionInstDuration[r, pe] <=
  max([
    sum(ce in CommunicationElems where 1 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (reactionDataSize[r] / allocatedBandwidth[ce, pe])
    | mem in MemoryElems
  ])
);

array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionDataDuration;
%% tighten the bounds for reactionDataDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionDataDuration[r, pe]
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  reactionDataDuration[r, pe] <=
  max(mem in MemoryElems) (
    sum(c in Channels, ce in CommunicationElems where channelDst[c] = r /\ 0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  )
);

array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionExeDuration;
%% tighten the bounds for reactionExeDuration
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionExeDuration[r, pe] /\ reactionExeDuration[r, pe] <= reactionWcet[r, pe]
);

array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionOutDuration;
%% tighten the bounds for reactionOutDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionOutDuration[r, pe]
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  reactionOutDuration[r, pe] <=
  max(mem in MemoryElems) (
    sum(c in Channels, ce in CommunicationElems where channelSrc[c] = r /\ 0 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  )
);
array[Reactions, PlatformElems] of var 0..hyperPeriod: reactionDuration;
%% make the equality and tighten the bounds for reactionDuration
constraint forall(r in Reactions, pe in ProcessingElems) (
  0 <= reactionDuration[r, pe] /\ reactionDuration[r, pe] <= min(j in Jobs where originalReaction[j] = r) (jobDeadline[j] - jobRelease[j])
);
constraint forall(r in Reactions, p in PlatformElems where not(p in ProcessingElems)) (
  reactionDuration[r, p] = 0
);

array[PlatformElems] of var 0..max(roundRobinElemsMaxSlices): roundRobinPeriod;
%% tighten the bounds for roundRobinPeriod
constraint forall(pe in RoundRobinElems) (
  0 <= roundRobinPeriod[pe]
  /\ roundRobinPeriod[pe] <= roundRobinElemsMaxSlices[pe]
);

% objectives
int: minUtilization = sum(j in Reactions) (
  min([reactionUtilization[j, p] | p in ProcessingElems])
);
int: maxUtilization = sum(j in Reactions) (
  max([reactionUtilization[j, p] | p in ProcessingElems])
);
var minUtilization..maxUtilization: totalUtilization;
% int: minLatency = sum(l in ReactionChains) (
%   min(reactionWcet[lastInChain[l], ..]) + jobRelease[lastInChain[l]] - jobDeadline[firstInChain[l]]
% );
% int: maxLatency = sum(l in ReactionChains) (
%   jobDeadline[lastInChain[l]] - jobRelease[firstInChain[l]] - max(reactionWcet[firstInChain[l], ..])
% );
% array[ReactionChains] of var int: latencies;
%% tighten the bounds
% constraint forall(l in ReactionChains) (
%   min(jobWcet[lastInChain[l], ..]) + jobRelease[lastInChain[l]] - jobDeadline[firstInChain[l]] <= latencies[l]  /\
%   latencies[l] <= jobDeadline[lastInChain[l]] - jobRelease[firstInChain[l]] - max(jobWcet[firstInChain[l], ..])
% );
% var int: totalLatencies;


% baseline constraints
%% memory contraints
constraint bin_packing_capa(
  memoryMaximumCapacity,
  reactorMapping ++ channelMapping,
  reactorSize ++ channelSize
);
constraint forall(r in Reactions, pe in ProcessingElems) (
  if (reactionExecution[r] = pe) then
    reactionExeDuration[r, pe] = reactionWcet[r, pe]
  else
    reactionExeDuration[r, pe] = 0
  endif
);
%% inferred ones
% constraint forall(r in Reactions, p in ProcessingElems) (
%   (reactionExecution[r] = p) * reactionWcet[r, p] / 10.0 <= reactionResponseTime[r, p]
% );
constraint forall(j in Jobs, p in ProcessingElems) (
  let {Reactions: r = originalReaction[j]} in
  if (reactionExecution[r] = p) then
    reactionResponseTime[r, p] <= (jobDeadline[j] - jobRelease[j])
  else
    reactionResponseTime[r, p] = 0
  endif
);

% data movements constraints
% instruction
constraint forall(r in Reactions, mem in MemoryElems, pe in ProcessingElems) (
  let {int: a = containingReactor[r]} in
  if (reactionExecution[r] = pe /\ reactorMapping[a] = mem) then
      reactionInstDuration[r, pe] = sum(ce in CommunicationElems where 
        0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
        (reactionDataSize[r] / allocatedBandwidth[ce, pe])
  else
    reactionInstDuration[r, pe] = 0
  endif
);
%% data
constraint forall(r in Reactions, mem in MemoryElems, pe in ProcessingElems) (
  let {int: a = containingReactor[r]} in
  if (reactionExecution[r] = pe  /\ reactorMapping[a] = mem ) then
    reactionDataDuration[r, pe] =  sum(c in Channels, ce in CommunicationElems where 
      channelDst[c] = r /\ 0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  else 
    reactionDataDuration[r, pe] = 0
  endif
);
%% write
constraint forall(r in Reactions, mem in MemoryElems, pe in ProcessingElems) (
  let {Reactors: a = containingReactor[r]} in
  if (reactionExecution[r] = pe /\ reactorMapping[a] = mem) then
    reactionOutDuration[r, pe] =  sum(c in Channels, ce in CommunicationElems where 
      channelSrc[c] = r /\ 0 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  else 
    reactionOutDuration[r, pe] = 0
  endif
);

% time-triggered constraints
% constraint forall(p in TimeTriggeredElems) (
%   disjunctive(
%   [jobStartingTime[j, p] | j in Jobs],
%   [reactionDuration[j, p] | j in Jobs])
% );
%% implied constraintjobTimeSlice
% constraint cumulative(
%   [jobStartingTime[j, p] | j in Jobs, p in TimeTriggeredElems], 
%   [reactionDuration[j, p] | j in Jobs, p in TimeTriggeredElems], 
%   [1 | j in Jobs, p in ProcessingElems], 
%   length(TimeTriggeredElems)
%   );
%% when to count execution
% constraint forall(r in Reactions, p in TimeTriggeredElems) (
%   if (reactionExecution[r] = p) then 
%     reactionExeDuration[r, p] = reactionWcet[r, p]
%   else
%     reactionExeDuration[r, p] = 0
%   endif
% );

% fixed-priority constraints
% constraint forall(j in Jobs, p in FixedPriorityElems) (
%   let {Reactions: r = originalReaction[j]} in
%   reactionResponseTime[r, p] <= reactionDuration[r, p] + hyperPeriod * (1 - (reactionExecution[r] = p))
%    % + sum(jj in Jobs where jobHigherPriority[jj, j] /\ jobInterferes[jj, j] /\ j != jj) (reactionDuration[originalReaction[jj], p])
% );
%% with dependencies
% array[Jobs, PlatformElems] of var float: jobResponseTime;
% constraint forall(j in Jobs, p in ProcessingElems) (
%   let {Reactions: r = originalReaction[j]} in
%   (reactionExecution[r] = p) * reactionWcet[r, p]  <= jobResponseTime[j, p]
% );
% constraint forall(j in Jobs, p in ProcessingElems) (
%   let {Reactions: r = originalReaction[j]} in
%   jobResponseTime[j, p] <= (jobDeadline[j] - jobRelease[j]) * (reactionExecution[r] = p)
% );
constraint forall(j in Reactions, p in FixedPriorityElems) (
  reactionDuration[j, p] = reactionInstDuration[j, p] + reactionDataDuration[j, p] + reactionExeDuration[j, p] + reactionOutDuration[j, p]
);
constraint forall(j in Jobs, p in FixedPriorityElems where not(exists(c in JobChannels) (jobChannelDst[c] = j))) (
  let {Reactions: r = originalReaction[j]} in
  if (reactionExecution[r] = p) then
    reactionDuration[r, p] <= reactionResponseTime[r, p]
  else
   reactionResponseTime[r, p] = 0
  endif
);
constraint forall(c in JobChannels, pp in FixedPriorityElems, p in ProcessingElems) (
  let {
    Jobs: j = jobChannelSrc[c]; 
    Jobs: jj = jobChannelDst[c]; 
    Reactions: r = originalReaction[j]; 
    Reactions: rr = originalReaction[jj]
  } in
  % if (reactionExecution[rr] = pp) then
  if (reactionExecution[rr] = pp /\ reactionExecution[r] = p) then
  reactionResponseTime[rr, pp] + (jobRelease[jj] - jobRelease[j])  >= reactionDuration[rr, pp] + reactionResponseTime[r, p]
  endif
);
% constraint forall(j in Jobs, p in ProcessingElems) (
%   let {Reactions: r = originalReaction[j]} in
%   jobResponseTime[j, p] <= reactionResponseTime[r, p]
% );

% round-robin constraints
% round-robin period

%% minimum time slice allocation
% constraint forall(r in Reactions, p in RoundRobinElems) (
%   (reactionExecution[r] = p) <= reactionRRSlices[r, p] /\ 
%   reactionRRSlices[r, p] <= (reactionExecution[r] = p) * roundRobinElemsMaxSlices[p]
% );
%% exe duration calculation
% constraint forall(r in Reactions, p in RoundRobinElems) (
%   if (reactionExecution[r] = p) then
%   reactionRRSlices[r, p] * reactionResponseTime[r, p] >= roundRobinPeriod[p] * reactionDuration[r, p] 
%   endif
% );
constraint forall(p in RoundRobinElems) (
  sum(reactionRRSlices[.., p]) <= roundRobinElemsMaxSlices[p]
);
constraint forall(r in Reactions, p in RoundRobinElems) (
  reactionDuration[r, p] * reactionRRSlices[r, p] >= (reactionInstDuration[r, p] + reactionDataDuration[r, p] + reactionExeDuration[r, p] + reactionOutDuration[r, p])
);
%% McCormick relaxation
% constraint forall(r in Reactions, p in RoundRobinElems) (
%   reactionDuration[r, p]
%   >=
%   (1.0 - reactionRRSlices[r, p]) * min(j in Jobs where originalReaction[j] = r) (jobDeadline[j] - jobRelease[j]) + 
%   reactionInstDuration[r, p] + reactionDataDuration[r, p] + reactionExeDuration[r, p] + reactionOutDuration[r, p] - hyperPeriod * (reactionExecution[r] != p)
% );
%% with dependencies
constraint forall(j in Jobs, p in RoundRobinElems where not(exists(c in JobChannels) (jobChannelDst[c] = j))) (
  let {Reactions: r = originalReaction[j]} in
  if (reactionExecution[r] = p) then
    reactionDuration[r, p] <= reactionResponseTime[r, p]
  else 
    reactionResponseTime[r, p] = 0
  endif
);
constraint forall(c in JobChannels, pp in RoundRobinElems, p in ProcessingElems) (
  let {
    Jobs: j = jobChannelSrc[c]; 
    Jobs: jj = jobChannelDst[c]; 
    Reactions: r = originalReaction[j]; 
    Reactions: rr = originalReaction[jj]
  } in
  % if (reactionExecution[rr] = pp) then
  if (reactionExecution[rr] = pp /\ reactionExecution[r] = p) then
    reactionResponseTime[rr, pp] + (jobRelease[jj] - jobRelease[j]) >= reactionDuration[rr, pp] + reactionResponseTime[r, p]
  endif
);

% constraint forall(j in Jobs, p in RoundRobinElems) (
% %   if (reactionExecution[r] = p) then
%   let {Reactions: r = originalReaction[j]} in
%   reactionRRSlices[r, p] * (jobDeadline[j] - jobRelease[j]) >= 
%   roundRobinElemsMaxSlices[p] * reactionDuration[r, p] + 
%   roundRobinPeriod[p] * (jobDeadline[j] - jobRelease[j]) -
%   roundRobinElemsMaxSlices[p] * (jobDeadline[j] - jobRelease[j]) - 
%   hyperPeriod * (reactionExecution[r] != p)
% %   endif
% );
% constraint forall(j in Jobs, pp in RoundRobinElems, p in ProcessingElems) (
% %   if (reactionExecution[r] = p) then
%   let {
%     Jobs: j = jobChannelSrc[c]; 
%     Jobs: jj = jobChannelDst[c]; 
%     Reactions: r = originalReaction[j]; 
%     Reactions: rr = originalReaction[jj]
%   } in
%   roundRobinElemsMaxSlices[pp] * reactionResponseTime[rr, pp] >= 
%   roundRobinElemsMaxSlices[pp] * reactionDuration[rr, pp] + 
%   roundRobinPeriod[p] * (jobDeadline[jj] - jobRelease[jj]) -
%   roundRobinElemsMaxSlices[p] * (jobDeadline[j] - jobRelease[j]) +
%   reactionResponseTime[r, p] - 
%   hyperPeriod * (reactionExecution[r] != p)
% %   endif
% );

%% with dependencies
% constraint forall(c in JobChannels, pp in RoundRobinElems, p in ProcessingElems) (
%   let {
%     Jobs: j = jobChannelSrc[c]; 
%     Jobs: jj = jobChannelDst[c]; 
%     Reactions: r = originalReaction[j]; 
%     Reactions: rr = originalReaction[jj]
%   } in
%   % if (reactionExecution[rr] = pp) then
%   reactionRRSlices[rr, pp] * reactionResponseTime[rr, pp] >= roundRobinPeriod[pp] * reactionDuration[rr, pp]
%   + (reactionResponseTime[r, p] - (jobRelease[jj] - jobRelease[j]))
%   % endif
% );

% precedence constraints
% set of int: JobPrecedenceChannels = JobPureChannels union JobInnerStateChannels;
% constraint forall(c in JobChannels, j, jj in Jobs, p, pp in ProcessingElems where jobChannelSrc[c] = j /\ jobChannelDst[c] = jj) (
%   let {Reactions: r = originalReaction[j], Reactions: rr = originalReaction[jj]} in
%   reactionResponseTime[rr, pp] >= (reactionDuration[rr, pp] + reactionResponseTime[r, p]) - (jobRelease[jj] - jobRelease[j])
% );

% objectives
% constraint totalUtilization = sum(p in ProcessingElems, r in Reactions where reactionExecution[r] = p) (reactionUtilization[r, p]);
% constraint totalLatencies = sum(l in ReactionChains) (latencies[l]);

% symmetry breaking
% constraint symmetry_breaking_constraint(forall(p, pp in ProcessingElems where platformElemsSymmetryGroups[p] > 0 /\ platformElemsSymmetryGroups[p] = platformElemsSymmetryGroups[pp] /\ p < pp) (
%   sum(r in Reactions where reactionExecution[r] = p) (reactionUtilization[r, p]) >=
%   sum(r in Reactions where reactionExecution[r] = pp) (reactionUtilization[r, pp])
% ));

% constraint symmetry_breaking_constraint(forall(p, pp in ProcessingElems where platformElemsSymmetryGroups[p] > 0 /\ platformElemsSymmetryGroups[p] = platformElemsSymmetryGroups[pp] /\ p < pp) (
%   sum(r in Reactions where reactionExecution[r] = p) (reactionUtilization[r, p]) =
%   sum(r in Reactions where reactionExecution[r] = pp) (reactionUtilization[r, pp]) <->
%   count(r in Reactions) (reactionExecution[r] = p) >= count(r in Reactions) (reactionExecution[r] = pp)
% ));

solve
  % :: warm_start(start, [i mod length(procs) | i in jobs])
  :: int_search(reactionResponseTime,  first_fail, indomain_min)
  :: int_search(reactionRRSlices, first_fail, indomain_max)
  % :: int_search(roundRobinPeriod, first_fail, indomain_min)
  :: int_search(reactionExecution, first_fail, indomain_min)
  :: int_search(reactionUtilization, first_fail, indomain_min)
  :: restart_luby(length(Reactions) * length(ProcessingElems))
  minimize totalUtilization ; %+ objLambda * totalLatencies;

output [
  "reactorMapping = ", show(reactorMapping), "\n", 
  "channelMapping = ", show(channelMapping), "\n", 
  "reactionExecution = ", show(reactionExecution), "\n", 
  "reactionResponseTime = ", show2d(reactionResponseTime), "\n", 
  "reactionRRSlices = ", show2d(reactionRRSlices), "\n", 
  "reactionDuration = ", show2d(reactionDuration), "\n", 
  "reactionExeDuration = ", show2d(reactionExeDuration), "\n", 
  "reactionInstDuration = ", show2d(reactionInstDuration), "\n", 
  "reactionDataDuration = ", show2d(reactionDataDuration), "\n", 
  "reactionOutDuration = ", show2d(reactionOutDuration)];
