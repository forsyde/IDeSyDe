module idesyde.blueprints {
    requires transitive idesyde.core;
    requires transitive info.picocli;
    requires transitive com.fasterxml.jackson.core;
    requires transitive com.fasterxml.jackson.databind;
    requires transitive com.fasterxml.jackson.dataformat.cbor;

    exports idesyde.blueprints;
}