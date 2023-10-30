module idesyde.java.metaheuristics {
    requires transitive idesyde.core;
    requires transitive idesyde.common;
    requires transitive idesyde.blueprints;

    // requires jmetal.core;
    // requires jmetal.algorithm;
    // requires jmetal.problem;
    // requires jmetal.parallel;

    requires io.jenetics.base;
    requires io.jenetics.ext;

    exports idesyde.metaheuristics;
    exports idesyde.metaheuristics.constraints;
}