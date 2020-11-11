:- module('std_sdf_queries', [
                              sdf_actor/1,
                              sdf_channel/1,
                              sdf_actor_produces/2,
                              sdf_actor_consumes/2
                             ]).
:- use_module('lib/types.pro').

:- dynamic(vertex/2).
:- dynamic(port/3).
:- dynamic(prop/3).
:- dynamic(edge/5).

sdf_actor(X) :- vertex(X, T1),
                is_type(T1, 'Process'),
                edge(Y, X, 'constructed', 'constructor', 'Constructs'),
                vertex(Y, T2),
                is_type(T2,  'SDFComb').

sdf_actor_constructor(X, Y) :- sdf_actor(X),
                                edge(Y, X, 'constructed', 'constructor', 'Constructs'),
                                vertex(Y, T),
                                is_type(T, 'SDFComb').

sdf_channel(Y) :- sdf_actor(X),
                  edge(X, Y, _, _, 'Writes'),
                  vertex(Y, T2),
                  is_type(T2,  'Signal').

sdf_channel(Y) :- sdf_actor(X),
                  edge(X, Y, _, _, 'Reads'),
                  vertex(Y, T2),
                  is_type(T2,  'Signal').

sdf_actor_produces(X, KV) :- sdf_actor(X),
                             prop(X, 'production', KV).
                           
sdf_actor_produces(X, KV) :- sdf_actor(X),
                             sdf_actor_constructor(X, Y),
                             prop(Y, 'production', KV).


sdf_actor_consumes(X, KV) :- sdf_actor(X),
                             prop(X, 'consumption', KV).
                           
sdf_actor_consumes(X, KV) :- sdf_actor(X),
                             sdf_actor_constructor(X, Y),
                             prop(Y, 'consumption', KV).

