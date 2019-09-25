use std::cmp::Ordering;

pub trait Symmetric {
    fn symme<C: Symmetric>(c: C) -> bool;
}

pub trait Valued {
    fn value<V: Ord>() -> V;
}

struct SearchSet<X, C: Valued + Symmetric> {
    rank: u64,
    elements: Vec<(Vec<X>, C)>
}

struct SearchElement<X, C: Valued + Symmetric> {
    x: X,
    c: C
}

impl<X, C: Valued + Symmetric> Ord for SearchElement<X, C> {
    fn cmp(&self, other: &SearchElement<X, C>) -> Ordering {
        if self.x.len() != other.x.len() {
            self.c.value().cmp(self.c.value())
        } else {
            self.x.len().cmp(other.x.len())
        }
    }
}

impl<X, C: Valued + Symmetric> PartialOrd for SearchElement<X, C> {
    fn partial_cmp(&self, other: &SearchElement<X, C>) -> Option<Ordering> {
        Some(self.cmp(other))
    }
}

pub fn greedy_backtracking_exploration<X, C: Valued + Symmetric>(
    transitions: Vec<fn(c: C, x: X) -> Vec<C>>,
    decisions: Vec<X>,
    initial_configuration: C
    ) -> Option<C>
{
    let mut ranks = vec![]
    let mut open = vec![(decisions, initial_configuration)];
    let mut best = initial_configuration;
    while let Some((xs, c)) = open.pop() {
        for (idx, x) in xs.iter().enumerate() {
            for trans in transitions {
                for new_configuration in trans(x, c) {

                }
            }
        }
    }
    best
}
