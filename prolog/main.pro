:- module('desyder-logical', []).
:- use_module('lib/sdf/queries.pro').
:- use_module('lib/types.pro').

model(File) :- ensure_loaded(File).
