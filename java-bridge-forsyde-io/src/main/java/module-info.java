module idesyde.forsyde.io {

    requires transitive idesyde.common;
    requires static idesyde.core.generator;

    requires transitive forsyde.io.core;
    requires transitive forsyde.io.libforsyde;
    requires transitive forsyde.io.java.sdfThree;
    requires org.jgrapht.core;

    exports idesyde.forsydeio;
}