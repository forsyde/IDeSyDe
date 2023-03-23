package idesyde.core;

import com.google.flatbuffers.Table;
import idesyde.core.headers.DecisionModelHeader;

import java.util.Objects;

public class DecisionModel<Body extends Table> {
    private DecisionModelHeader header;
    private Body body;

    public DecisionModelHeader getHeader() {
        return header;
    }

    public void setHeader(DecisionModelHeader header) {
        this.header = header;
    }

    public Body getBody() {
        return body;
    }

    public void setBody(Body body) {
        this.body = body;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DecisionModel)) return false;
        DecisionModel<?> that = (DecisionModel<?>) o;
        return getHeader().equals(that.getHeader());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getHeader());
    }
}
