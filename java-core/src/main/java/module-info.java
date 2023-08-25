module idesyde.core {
    requires java.base;

    requires transitive com.fasterxml.jackson.core;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires transitive com.fasterxml.jackson.dataformat.cbor;
    requires transitive  com.fasterxml.jackson.datatype.jdk8;
    exports idesyde.core.headers;
    exports idesyde.core;
}