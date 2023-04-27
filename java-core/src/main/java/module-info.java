module idesyde.core {
    requires java.base;

    requires transitive com.fasterxml.jackson.core;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.annotation;
    requires msgpack.core;
    requires jackson.dataformat.msgpack;
    exports idesyde.core.headers;
    exports idesyde.core;
}