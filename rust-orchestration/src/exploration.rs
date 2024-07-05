use std::{
    cmp::Ordering,
    collections::{HashMap, HashSet},
    sync::{Arc, Mutex},
    time::{Duration, Instant},
};

use crossbeam::channel::Receiver;
use idesyde_core::{
    DecisionModel, ExplorationBid, ExplorationConfiguration, ExplorationSolution, Explorer,
};
use serde::{Deserialize, Serialize};

// use rayon::prelude::*;

#[derive(Deserialize, Serialize, PartialEq, Clone)]
pub struct ExplorerBidding {
    explorer_unique_identifier: String,
    can_explore: bool,
    properties: HashMap<String, f64>,
}

impl TryFrom<&str> for ExplorerBidding {
    type Error = serde_json::Error;

    fn try_from(value: &str) -> Result<Self, Self::Error> {
        serde_json::from_str(value)
    }
}

#[derive(PartialEq, Eq, Copy, Clone, Debug)]
pub enum ExplorationStatus {
    Optimal,
    Dominated,
    Unknown,
}

pub fn explore_level_non_blocking(
    explorers_and_models: &[(Arc<dyn Explorer>, Arc<dyn DecisionModel>)],
    biddings: &[ExplorationBid],
    configuration: &ExplorationConfiguration,
    solutions: &HashSet<ExplorationSolution>,
) -> (Arc<Mutex<ExplorationStatus>>, Receiver<ExplorationSolution>) {
    let status = Arc::new(Mutex::new(ExplorationStatus::Unknown));
    let (level_tx, level_rx) = crossbeam::channel::unbounded::<ExplorationSolution>();
    for ((explorer, model), b) in explorers_and_models.iter().zip(biddings.iter()) {
        let explorer = explorer.clone();
        let model = model.clone();
        let conf = configuration.to_owned();
        let current_solutions = solutions.clone();
        let level_tx = level_tx.clone();
        let this_status = status.clone();
        let is_exact = b.is_exact;
        let time_out_duration = if configuration.improvement_timeout > 0 {
            Some(Duration::from_secs(configuration.improvement_timeout))
        } else {
            None
        };
        rayon::spawn(move || {
            let iter_mutex =
                explorer.explore(model.to_owned(), &current_solutions, conf.to_owned());
            let start = Instant::now();
            if let Ok(mut iter) = iter_mutex.lock() {
                while let Some(event) = iter.next() {
                    let is_complete = event.optimality_proved;
                    if let Some(sol) = event.solution {
                        if current_solutions
                            .iter()
                            .all(|cur| cur.partial_cmp(&sol) != Some(Ordering::Less))
                            && !current_solutions.contains(&sol)
                        {
                            match level_tx.send(sol) {
                                Ok(_) => (),
                                Err(_) => return,
                            }
                        }
                        if let Some(duration) = time_out_duration {
                            if start.elapsed() >= duration {
                                return;
                            }
                        }
                    }
                    if this_status
                        .lock()
                        .map(|x| *x != ExplorationStatus::Unknown)
                        .unwrap_or(false)
                    {
                        // println!("Finished exploration with domination or optimality.");
                        return;
                    } else if is_complete && is_exact {
                        let _ = this_status
                            .lock()
                            .map(|mut x| *x = ExplorationStatus::Optimal);
                        // println!("Finished exploration with optimal solution.");
                        return;
                    }
                }
                // println!("Finishing without further solutions.");
            };
            // } else {
            //     return;
            // }
        });
    }
    (status, level_rx)
}

pub struct MultiLevelCombinedExplorerIterator {
    explorers_and_models: Vec<(Arc<dyn Explorer>, Arc<dyn DecisionModel>)>,
    biddings: Vec<ExplorationBid>,
    exploration_configuration: ExplorationConfiguration,
    // levels: Vec<CombinedExplorerIterator>,
    // levels_tuple: (Option<CombinedExplorerIterator>, CombinedExplorerIterator),
    current_solutions: HashSet<ExplorationSolution>,
    levels_streams: Vec<Receiver<ExplorationSolution>>,
    levels_status: Vec<Arc<Mutex<ExplorationStatus>>>,
    levels_start: Vec<Instant>,
    num_found: u64,
    // converged_to_last_level: bool,
    start: Instant,
}

impl Iterator for MultiLevelCombinedExplorerIterator {
    type Item = ExplorationSolution;

    fn next(&mut self) -> Option<Self::Item> {
        loop {
            if self.exploration_configuration.total_timeout > 0
                && self.start.elapsed()
                    > Duration::from_secs(self.exploration_configuration.total_timeout)
            {
                return None;
            }
            if self.levels_streams.len() == 0 {
                return None;
            }
            while self.levels_streams.len() > 2 {
                let _ = self.levels_status[0]
                    .lock()
                    .map(|mut x| *x = ExplorationStatus::Dominated);
                self.levels_status.remove(0);
                self.levels_streams.remove(0);
                self.levels_start.remove(0);
            }
            for i in (0..self.levels_streams.len()).rev() {
                if let Some(level) = self.levels_streams.get(i) {
                    match level.recv_timeout(Duration::from_millis(500)) {
                        Ok(solution) => {
                            if !self.current_solutions.iter().any(|s| {
                                s.partial_cmp(&solution) == Some(Ordering::Less)
                                    || s.partial_cmp(&solution) == Some(Ordering::Equal)
                            }) {
                                self.num_found += 1;
                                // let sol_dominates = self.current_solutions.is_empty()
                                //     || self.current_solutions.iter().any(|cur_sol| {
                                //         solution.partial_cmp(cur_sol) == Some(Ordering::Less)
                                //     });
                                self.current_solutions.insert(solution.clone());
                                self.current_solutions.retain(|cur_sol| {
                                    solution.partial_cmp(cur_sol) != Some(Ordering::Less)
                                });
                                // println!(
                                //     "Creating new level. Levels are: {}",
                                //     self.levels_status
                                //         .iter()
                                //         .map(|x| format!("{:?}", x.lock().unwrap()))
                                //         .collect::<Vec<String>>()
                                //         .join(", ")
                                // );
                                let (is_dominated, new_level) = explore_level_non_blocking(
                                    &self.explorers_and_models,
                                    self.biddings.as_slice(),
                                    &self.exploration_configuration,
                                    &self.current_solutions,
                                );
                                self.levels_streams.push(new_level);
                                self.levels_status.push(is_dominated);
                                self.levels_start.push(Instant::now());
                                // if sol_dominates {
                                // }
                                return Some(solution);
                            }
                        }
                        Err(crossbeam::channel::RecvTimeoutError::Timeout) => {
                            let improv_timed_out =
                                if self.exploration_configuration.improvement_timeout > 0 {
                                    self.levels_start[i].elapsed().as_secs()
                                        >= self.exploration_configuration.improvement_timeout
                                } else {
                                    false
                                };
                            let is_optimal = self.levels_status[i]
                                .lock()
                                .map(|x| *x == ExplorationStatus::Optimal)
                                .unwrap_or(false);
                            if improv_timed_out || is_optimal {
                                self.levels_streams.remove(i);
                                self.levels_status.remove(i);
                                self.levels_start.remove(i);
                                break;
                            }
                        }
                        Err(crossbeam::channel::RecvTimeoutError::Disconnected) => {
                            // let optimal = self.levels_status[i]
                            // .lock()
                            // .map(|x| *x == ExplorationStatus::Optimal)
                            // .unwrap_or(false);
                            // let all_optimal_finished = (0..self.levels_streams.len())
                            //     .filter(|i| self.levels_is_exact[*i])
                            //     .all(|i| {
                            //         self.levels_status[i]
                            //             .lock()
                            //             .map(|x| *x == ExplorationStatus::Optimal)
                            //             .unwrap_or(false)
                            //     });
                            self.levels_streams.remove(i);
                            self.levels_status.remove(i);
                            self.levels_start.remove(i);
                            // if all_optimal_finished {
                            //     return None;
                            // }
                            break;
                        }
                    }
                }
            }
        }
    }
}

pub fn compute_pareto_solutions(sols: Vec<ExplorationSolution>) -> Vec<ExplorationSolution> {
    sols.iter()
        .filter(|x| {
            !sols
                .iter()
                .filter(|y| x != y)
                .any(|y| y.partial_cmp(x) == Some(Ordering::Less))
        })
        .map(|x| x.to_owned())
        .collect()
}

pub fn explore_cooperatively(
    explorers_and_models: &[(Arc<dyn Explorer>, Arc<dyn DecisionModel>)],
    biddings: &[ExplorationBid],
    current_solutions: &HashSet<ExplorationSolution>,
    exploration_configuration: &ExplorationConfiguration,
    // solution_inspector: F,
) -> MultiLevelCombinedExplorerIterator {
    let (is_dominated, new_level) = explore_level_non_blocking(
        explorers_and_models,
        biddings,
        exploration_configuration,
        current_solutions,
    );
    // let combined_explorer = CombinedExplorerIterator2::create(
    //     explorers_and_models,
    //     biddings,
    //     currrent_solutions,
    //     exploration_configuration,
    // );
    // let mut deque = VecDeque::new();
    // deque.push_front(combined_explorer);
    MultiLevelCombinedExplorerIterator {
        explorers_and_models: Vec::from(explorers_and_models),
        biddings: biddings.to_owned(),
        current_solutions: current_solutions.clone(),
        exploration_configuration: exploration_configuration.to_owned(),
        start: Instant::now(),
        num_found: 0,
        levels_streams: vec![new_level],
        levels_status: vec![is_dominated],
        levels_start: vec![Instant::now()],
    }
}
