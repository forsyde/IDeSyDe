include "globals.mzn";
include "gecode.mzn";

% objectives
int: objPercentage;

% model parameters
int: hyperPeriod;
int: nReactors;
int: nReactions;
int: nChannels;
% int: nJobs;
% int: nJobChannels;

% aux model parameters
int: maxReactionInterferences;

set of int: Reactors = 1..nReactors;
set of int: Reactions = 1..nReactions;
set of int: Channels = 1..nChannels;
% set of int: Jobs = 1..nJobs;
% set of int: JobChannels = 1..nJobChannels;


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
int: minProcessingCores;

% reactors
array[Reactors] of int: reactorSize;
% reactions
array[Reactions] of Reactors: containingReactor;
array[Reactions] of int: reactionDataSize;
array[Reactions] of int: reactionLatestRelease;
array[Reactions] of int: reactionRelativeDeadline;
array[Reactions, Reactions] of int: reactionInitialRelativeOffset;
array[Reactions, Reactions] of bool: reactionPriorityLTEQ;
array[Reactions, Reactions] of bool: reactionReachesLeftToRight;
array[Reactions, Reactions] of int: reactionMaxNumberInterferences;
array[Reactions, Reactions, 1..maxReactionInterferences] of int: reactionInterferencesStart;
array[Reactions, PlatformElems] of int: reactionWcet;
array[Reactions, PlatformElems] of int: reactionUtilization;
array[Reactions, PlatformElems] of bool: reactionCanBeExecuted;
% channels 
array[Channels] of int: channelSize;
array[Channels] of Reactions: channelSrc;
array[Channels] of Reactions: channelDst;

% for platform
% array[PlatformElems] of int: roundRobinElemsMinSlice;
array[PlatformElems] of int: roundRobinElemsMaxSlices;
array[PlatformElems, PlatformElems, PlatformElems] of int: platformPaths;
array[PlatformElems, PlatformElems] of int: allocatedBandwidth;
array[PlatformElems] of int: memoryMaximumCapacity;
% goal elements
array[ReactionChains] of Reactions: reactionChainStart;
array[ReactionChains] of Reactions: reactionChainEnd;
array[ReactionChains] of int: reactionChainFixedLatency;

% additional sets
set of int: IndependentReactions = {
  rr | rr in Reactions where not(exists(r in Reactions) (reactionInitialRelativeOffset[r, rr] >= 0))
};
set of int: DependentReactions = {r | r in Reactions where not(r in IndependentReactions)};

% main decision variables
array[Reactors] of var MemoryElems: reactorMapping;
array[Channels] of var MemoryElems: channelMapping;
array[Reactions] of var ProcessingElems: reactionExecution;
array[Reactions, PlatformElems] of var 0..max(roundRobinElemsMaxSlices): reactionRRSlices;
%% tighten bonds of reactionExecution
constraint forall(j in Reactions, p in ProcessingElems where reactionCanBeExecuted[j, p] = false) (
  reactionExecution[j] != p
);
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
array[Reactions] of var 0..max(reactionRelativeDeadline): reactionInstDuration;
array[Reactions] of var 0..max(reactionRelativeDeadline): reactionDataDuration;
array[Reactions] of var min(reactionWcet)..max(reactionRelativeDeadline): reactionExeDuration;
array[Reactions] of var 0..max(reactionRelativeDeadline): reactionOutDuration;
array[Reactions] of var min(reactionWcet)..max(reactionRelativeDeadline): reactionDuration;
array[Reactions] of var 0..max(reactionRelativeDeadline): reactionOffsetTime;
array[Reactions, Reactions] of var 0..maxReactionInterferences: reactionInterferences;
array[Reactions] of var min(reactionWcet)..max(reactionRelativeDeadline): reactionResponseTime;
%% tighten bound of jobStartingTime:
constraint forall(r in Reactions) (
    min(p in ProcessingElems) (reactionWcet[r, p]) <= reactionResponseTime[r] /\ reactionResponseTime[r] <= reactionRelativeDeadline[r] 
);
% array[PlatformElems] of var 0..max(roundRobinElemsMaxSlices): roundRobinPeriod;
%% tighten the bounds for reactionInstDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
% constraint forall(r in Reactions, pe in ProcessingElems) (
%   0 <= reactionInstDuration[r, pe]
% );
constraint forall(r in Reactions) (
  reactionInstDuration[r] <=
  max([
    sum(ce in CommunicationElems where 1 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (reactionDataSize[r] / allocatedBandwidth[ce, pe])
    | mem in MemoryElems, pe in ProcessingElems
  ])
);

%% tighten the bounds for reactionDataDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions) (
  reactionDataDuration[r] <=
  max(mem in MemoryElems, pe in ProcessingElems) (
    sum(c in Channels, ce in CommunicationElems where channelDst[c] = r /\ 0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  )
);
%% tighten the bounds for reactionExeDuration
constraint forall(r in Reactions) (
  min(p in ProcessingElems) (reactionWcet[r, p]) <= reactionExeDuration[r] /\ reactionExeDuration[r] <= max(p in ProcessingElems) (reactionWcet[r, p])
);
%% tighten the bounds for reactionOutDuration
%% this line is lengthy, but one must remember that platformPaths starts from 1 until the length of the path.
%% So this computation takes into account only CEs between the ME and the PE
constraint forall(r in Reactions) (
  reactionOutDuration[r] <=
  max(mem in MemoryElems, pe in ProcessingElems) (
    sum(c in Channels, ce in CommunicationElems where channelSrc[c] = r /\ 0 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem]) 
      (channelSize[c] / allocatedBandwidth[ce, pe])
  )
);
%% make the equality and tighten the bounds for reactionDuration
constraint forall(r in Reactions) (
  min(p in ProcessingElems) (reactionWcet[r, p]) <= reactionDuration[r] /\ reactionDuration[r] <= reactionRelativeDeadline[r] %min(j in Jobs where originalReaction[j] = r) (jobDeadline[j] - jobRelease[j])
);

%% tighten the bounds for reactionInterferences
constraint forall(r, rr in Reactions) (
  reactionInterferences[r, rr] <= reactionMaxNumberInterferences[r, rr]
);
%% tighten the bounds for reactionOffsetTime
constraint forall(r in DependentReactions) (
  min(rr in Reactions where reactionInitialRelativeOffset[rr, r] >= 0) (
    min(p in ProcessingElems where reactionCanBeExecuted[rr, p]) (reactionWcet[rr, p]) - reactionInitialRelativeOffset[rr, r]
  ) 
  <= reactionOffsetTime[r]
);


% objectives
int: minUtility = minProcessingCores;
int: maxUtility = length(ProcessingElems);
var minUtility..maxUtility: totalUtility;
int: maxLatency = max(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  reactionRelativeDeadline[lst] %+ reactionLatestRelease[lst] - reactionChainFixedLatency[l]
);
int: minTotalLatencies = sum(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  min(p in ProcessingElems where reactionCanBeExecuted[lst, p]) (reactionWcet[lst, p])
);
int: weightedMinTotalLatencies = sum(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  min(p in ProcessingElems where reactionCanBeExecuted[lst, p]) (reactionWcet[lst, p] * (maxLatency div reactionRelativeDeadline[lst]))
);

int: maxTotalLatencies = sum(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  reactionRelativeDeadline[lst] %+ reactionLatestRelease[lst] - reactionChainFixedLatency[l]
);
int: weightedMaxTotalLatencies = sum(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  maxLatency
);
array[ReactionChains] of var 0..max(reactionRelativeDeadline): latenciesVariation;
var weightedMinTotalLatencies..weightedMaxTotalLatencies: weightedTotalLatencies;
% %% tighten the bounds
% constraint forall(l in ReactionChains) (
%   0 <= latenciesVariation[l]
% );
constraint forall(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  min(p in ProcessingElems where reactionCanBeExecuted[lst, p]) (reactionWcet[lst, p] - reactionInitialRelativeOffset[fst, lst]) <= latenciesVariation[l]
);
constraint forall(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  latenciesVariation[l] <= reactionRelativeDeadline[lst]
);


% baseline constraints
%% memory contraints
constraint bin_packing_capa(
  memoryMaximumCapacity,
  reactorMapping ++ channelMapping,
  reactorSize ++ channelSize
);
constraint forall(r in IndependentReactions) (
  reactionOffsetTime[r] = 0
);
constraint forall(r in DependentReactions, rr in Reactions where r != rr /\ reactionInitialRelativeOffset[rr, r] >= 0) (
  reactionOffsetTime[r] + reactionInitialRelativeOffset[rr, r] >= reactionResponseTime[rr]
);
constraint forall(r in Reactions) (
  reactionExeDuration[r] = sum(p in ProcessingElems where reactionExecution[r] = p) (reactionWcet[r, p])
);


% data movements constraints
% instruction
constraint forall(r in Reactions) (
  let {int: a = containingReactor[r]} in
  reactionInstDuration[r] = sum(ce in CommunicationElems, mem in MemoryElems, pe in ProcessingElems where 
    0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe] /\ reactorMapping[a] = mem /\ reactionExecution[r] = pe) 
    (reactionDataSize[r] div allocatedBandwidth[ce, pe])
);
%% data
constraint forall(r in Reactions) (
  let {int: a = containingReactor[r]} in
  reactionDataDuration[r] =  sum(c in Channels, ce in CommunicationElems, mem in MemoryElems, pe in ProcessingElems where 
    channelDst[c] = r /\ 0 < platformPaths[mem, pe, ce] /\ platformPaths[mem, pe, ce] < platformPaths[mem, pe, pe] /\ channelMapping[c] = mem /\ reactionExecution[r] = pe) 
    (channelSize[c] div allocatedBandwidth[ce, pe])
);
%% write
constraint forall(r in Reactions) (
  let {Reactors: a = containingReactor[r]} in
  reactionDataDuration[r] =  sum(c in Channels, ce in CommunicationElems, mem in MemoryElems, pe in ProcessingElems where 
    channelSrc[c] = r /\ 0 < platformPaths[pe, mem, ce] /\ platformPaths[pe, mem, ce] < platformPaths[pe, mem, mem] /\ channelMapping[c] = mem /\ reactionExecution[r] = pe) 
    (channelSize[c] div allocatedBandwidth[ce, pe])
);


% fixed-priority constraints
constraint forall(r, rr in Reactions) (
  if (r != rr /\ reactionExecution[r] = reactionExecution[rr] /\ reactionPriorityLTEQ[rr, r] /\ not(reactionReachesLeftToRight[r, rr])) then
    reactionInterferences[r, rr] = count(i in 1..reactionMaxNumberInterferences[r,rr]) (
      reactionOffsetTime[rr] <= reactionInterferencesStart[r, rr, i]
    )
  else
    reactionInterferences[r, rr] = 0
  endif
);
constraint forall(j in Reactions, p in FixedPriorityElems) (
  if (reactionExecution[j] = p) then
    reactionDuration[j] = reactionInstDuration[j] + reactionDataDuration[j] + reactionExeDuration[j] + reactionOutDuration[j]
  endif
);
constraint forall(r in Reactions, p in FixedPriorityElems) (
  if (reactionExecution[r] = p) then
    sum(rr in Reactions where reactionExecution[rr] = p /\ rr != r /\ reactionPriorityLTEQ[r, rr] /\ not(reactionReachesLeftToRight[rr, r])) (reactionDuration[rr] * reactionInterferences[rr, r])  +
    reactionDuration[r] + reactionOffsetTime[r]
    <= reactionResponseTime[r]
  endif
);
% inferred
constraint forall(r in Reactions, p in FixedPriorityElems) (
  if (reactionExecution[r] = p) then
    reactionDuration[r] + reactionOffsetTime[r]
    <= reactionResponseTime[r]
  endif
);
% constraint forall(r in Reactions, p in FixedPriorityElems) (
%   if (reactionExecution[r] = p) then
%     sum(rr in Reactions where reactionExecution[rr] = p /\ rr != r /\ reactionPriorityLTEQ[r, rr] /\ not(reactionReachesLeftToRight[rr, r])) (reactionDuration[rr] * reactionMaxNumberInterferences[rr, r])  +
%     reactionDuration[r] + reactionOffsetTime[r]
%     >= reactionResponseTime[r]
%   endif
% );



% round-robin constraints

%% minimum time slice allocation
constraint forall(r in Reactions, p in RoundRobinElems) (
  if (reactionExecution[r] = p) then
   0 < reactionRRSlices[r, p]
  else
   reactionRRSlices[r, p] = 0
  endif
);
constraint forall(p in RoundRobinElems) (
  sum(reactionRRSlices[.., p]) <= roundRobinElemsMaxSlices[p]
);
constraint forall(r in Reactions, p in RoundRobinElems) (
  if (reactionExecution[r] = p) then
    reactionDuration[r] * reactionRRSlices[r, p] =
    roundRobinElemsMaxSlices[p] * (reactionInstDuration[r] + reactionDataDuration[r] + reactionExeDuration[r] + reactionOutDuration[r])
  endif
);

% with dependencies
constraint forall(r in Reactions, pp in RoundRobinElems) (
  if (reactionExecution[r] = pp) then
    reactionResponseTime[r] = reactionDuration[r] + reactionOffsetTime[r]
  endif
);


% objectives
constraint totalUtility = sum(p in ProcessingElems) (count(r in Reactions) (reactionExecution[r] = p) > 0);

constraint forall(l in ReactionChains) (
  let {Reactions: lst = reactionChainEnd[l]; Reactions: fst = reactionChainStart[l]} in
  latenciesVariation[l] >= reactionResponseTime[lst] - (reactionOffsetTime[fst])
);
constraint weightedTotalLatencies = sum(l in ReactionChains) (
  let {
    Reactions: lst = reactionChainEnd[l];
    Reactions: fst = reactionChainStart[l];
  } in
  latenciesVariation[l] * ceil(maxLatency / reactionRelativeDeadline[lst])
);

var min([minUtility, weightedMinTotalLatencies])..200*max([maxUtility, weightedMaxTotalLatencies]): goal;
constraint goal = 
  if (objPercentage <= 0) then
    totalUtility
  elseif (objPercentage >= 100) then
    weightedTotalLatencies
  else
      (100 - objPercentage) * (totalUtility - minUtility) * ceil((weightedMaxTotalLatencies - weightedMinTotalLatencies) / (maxUtility - minUtility)) + 
      objPercentage * (weightedTotalLatencies - weightedMinTotalLatencies)
  endif;

% symmetry breaking
% constraint symmetry_breaking_constraint(forall(p, pp in ProcessingElems where platformElemsSymmetryGroups[p] > 0 /\ platformElemsSymmetryGroups[pp] > 0 /\ platformElemsSymmetryGroups[p] = platformElemsSymmetryGroups[pp] /\ p < pp) (
%   count(r in Reactions) (reactionExecution[r] = p) = count(r in Reactions) (reactionExecution[r] = pp) <->
%   sum(r in Reactions where reactionExecution[r] = p) (reactionUtilization[r, p]) >=
%   sum(r in Reactions where reactionExecution[r] = pp) (reactionUtilization[r, pp])
% ));

constraint symmetry_breaking_constraint(forall(p, pp in ProcessingElems where platformElemsSymmetryGroups[p] > 0 /\ platformElemsSymmetryGroups[pp] > 0 /\ platformElemsSymmetryGroups[p] = platformElemsSymmetryGroups[pp] /\ p < pp) (
  count(r in Reactions) (reactionExecution[r] = p) >= count(r in Reactions) (reactionExecution[r] = pp)
));

% solve
%   :: warm_start(reactionExecution, [arg_min(p in ProcessingElems where reactionCanBeExecuted[r, p]) (reactionWcet[r, p]) | r in Reactions])
%   :: seq_search([
%     int_search(reactionExecution, input_order, indomain_min),
%     int_search(reactionResponseTime,  smallest, indomain_min),
%     int_search(reactionOffsetTime,  smallest, indomain_min),
%     int_search(reactionRRSlices, first_fail, indomain_max),
%     int_search(reactionUtilization, first_fail, indomain_min),
%     int_search(latenciesVariation, first_fail, indomain_min)
%   ])
%   :: restart_luby(length(Reactions) * length(ProcessingElems))
%   :: relax_and_reconstruct(reactionExecution, 20)
%   minimize goal;

% output [
%   "reactorMapping = ", show(reactorMapping), "\n", 
%   "channelMapping = ", show(channelMapping), "\n", 
%   "reactionExecution = ", show(reactionExecution), "\n", 
%   "reactionResponseTime = ", show(reactionResponseTime), "\n", 
%   "reactionOffsetTime = ", show(reactionOffsetTime), "\n", 
%   "reactionRRSlices = ", show2d(reactionRRSlices), "\n", 
%   "reactionDuration = ", show(reactionDuration), "\n", 
%   "reactionExeDuration = ", show(reactionExeDuration), "\n", 
%   "reactionInstDuration = ", show(reactionInstDuration), "\n", 
%   "reactionDataDuration = ", show(reactionDataDuration), "\n", 
%   "reactionOutDuration = ", show(reactionOutDuration), "\n", 
%   "reactionInterferences = ", show2d(reactionInterferences), "\n", 
%   "totalUtility = ", show(totalUtility), "\n",
%   "latenciesVariation = ", show(latenciesVariation)];
