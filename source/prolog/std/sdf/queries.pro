:- module('std_sdf_queries', [sdf_actor/1, sdf_channel/1]).
:- use_module('std/types.pro').

sdf_actor(X) :- vertex(X, T1),
                is_type(T1, 'Process'),
                edge(Y, X, 'constructed', 'constructor', 'Constructs'),
                vertex(Y, T2),
                is_type(T2,  'SDFComb').

sdf_channel(Y) :- vertex(X, _),
                  sdf_actor(X),
                  edge(X, Y, _, _, 'Writes'),
                  vertex(Y, T2),
                  is_type(T2,  'Signal').
