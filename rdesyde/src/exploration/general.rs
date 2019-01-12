pub trait Symmetric {
    fn symme<C: Symmetric>(c: C) -> bool;
}

pub trait Valued {
    fn value<V>() -> V;
}

pub fn explore_complete_goal_driven<X, C: Valued + Symmetric>(
    transitions: Vec<fn(c: C, x: X) -> Vec<C>>,
    decisions: Vec<X>,
    initial_configuration: C
    ) -> C
{
    let mut open = vec![(decisions, initial_configuration)];
    let mut current : Vec<(Vec<X>, C)> = Vec::new();
    let mut best = initial_configuration;
    while let Some((xs, c)) = open.pop() {
        for x in xs {
            current.clear();
        }
    }
    best
}
