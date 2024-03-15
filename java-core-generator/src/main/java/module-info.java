module idesyde.core.generator {
    requires java.base;
    requires java.compiler;

    requires transitive idesyde.core;
    requires com.squareup.javapoet;
    exports idesyde.core.generator;
}