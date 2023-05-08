module idesyde.common {
    requires transitive com.fasterxml.jackson.databind;
    requires transitive idesyde.core;
    requires transitive idesyde.blueprints;

    exports idesyde.common;
}