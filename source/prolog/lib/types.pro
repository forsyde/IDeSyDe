:- module('std_types', [is_type/2]).

allowed_type('DataType').
allowed_type('ConstructedProcess').
allowed_type('Signal').
allowed_type('NativeFunction').
allowed_type('Implementation').
allowed_type('SDFComb').
allowed_type('SDFPrefix').
allowed_type('SYComb').
allowed_type('SYPrefix').
allowed_type('DEPrefix').
allowed_type('DEValueComb').
allowed_type('DETagComb').
allowed_type('Implements').
allowed_type('Constructs').
allowed_type('Types').
allowed_type('Takes').
allowed_type('Writes').
allowed_type('Reads').
allowed_type('Process').
allowed_type('FIFOSignal').
allowed_type('Input').
allowed_type('Output').
allowed_type('Description').
allowed_type('Communication').
allowed_type('Computation').
allowed_type('Storage').
allowed_type('Interface').
allowed_type('Structure').
allowed_type('TimeDivisionMultiplexer').
allowed_type('ComputationalTile').
allowed_type('Goal').
allowed_type('Throughput').
allowed_type('SporadicTask').
allowed_type('TriggeredTask').
allowed_type('StaticCyclicSchedule').
allowed_type('FixedPriorityScheduler').
allowed_type('CustomScheduler').
allowed_type('RoundRobinScheduler').
allowed_type('Unknown').

type_refines('DataType', 'DataType').
type_refines('ConstructedProcess', 'ConstructedProcess').
type_refines('ConstructedProcess', 'Process').
type_refines('Signal', 'Signal').
type_refines('NativeFunction', 'NativeFunction').
type_refines('Implementation', 'Implementation').
type_refines('SDFComb', 'SDFComb').
type_refines('SDFPrefix', 'SDFPrefix').
type_refines('SYComb', 'SYComb').
type_refines('SYPrefix', 'SYPrefix').
type_refines('DEPrefix', 'DEPrefix').
type_refines('DEValueComb', 'DEValueComb').
type_refines('DETagComb', 'DETagComb').
type_refines('Implements', 'Implements').
type_refines('Constructs', 'Constructs').
type_refines('Types', 'Types').
type_refines('Takes', 'Takes').
type_refines('Writes', 'Writes').
type_refines('Reads', 'Reads').
type_refines('Process', 'Process').
type_refines('FIFOSignal', 'FIFOSignal').
type_refines('FIFOSignal', 'Signal').
type_refines('Input', 'Input').
type_refines('Output', 'Output').
type_refines('Description', 'Description').
type_refines('Communication', 'Communication').
type_refines('Computation', 'Computation').
type_refines('Storage', 'Storage').
type_refines('Interface', 'Interface').
type_refines('Structure', 'Structure').
type_refines('TimeDivisionMultiplexer', 'TimeDivisionMultiplexer').
type_refines('TimeDivisionMultiplexer', 'Communication').
type_refines('ComputationalTile', 'ComputationalTile').
type_refines('ComputationalTile', 'Computation').
type_refines('Goal', 'Goal').
type_refines('Throughput', 'Throughput').
type_refines('Throughput', 'Goal').
type_refines('SporadicTask', 'SporadicTask').
type_refines('TriggeredTask', 'TriggeredTask').
type_refines('StaticCyclicSchedule', 'StaticCyclicSchedule').
type_refines('FixedPriorityScheduler', 'FixedPriorityScheduler').
type_refines('CustomScheduler', 'CustomScheduler').
type_refines('RoundRobinScheduler', 'RoundRobinScheduler').

is_type(T, N) :- T = N, allowed_type(N).
is_type(T, N) :- allowed_type(T), 
                 allowed_type(N), 
                 allowed_type(T2), 
                 type_refines(T, T2),
                 type_refines(T2, N).
