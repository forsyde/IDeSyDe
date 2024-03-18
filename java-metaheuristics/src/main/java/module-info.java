module idesyde.java.metaheuristics {
    requires transitive idesyde.core;
    requires transitive idesyde.common;
    requires static idesyde.core.generator;

    // requires jmetal.core;
    // requires jmetal.algorithm;
    // requires jmetal.problem;
    // requires jmetal.parallel;

    requires org.jgrapht.core;

    requires io.jenetics.base;
    requires io.jenetics.ext;

    exports idesyde.metaheuristics;
    exports idesyde.metaheuristics.constraints;
}