package r.nodes;

import com.oracle.truffle.runtime.*;

import r.*;
import r.data.*;

public abstract class Node {

    Node parent;

    public abstract void accept(Visitor v);

    public abstract void visit_all(Visitor v);

    public int getPrecedence() {
        Class< ? > clazz = getClass();
        Precedence prec = clazz.getAnnotation(Precedence.class);
        return prec == null ? Precedence.MIN : prec.value();
    }

    public Node getParent() {
        return parent;
    }

    public void setParent(Node node) {
        parent = node;
    }

    protected <T extends Node> T[] updateParent(T[] children) {
        for (T node : children) {
            updateParent(node);
        }
        return children;
    }

    protected <T extends Node> T updateParent(T child) {
        if (child != null) {
            child.setParent(this);
        }
        return child;
    }

    // FIXME should be abstract ... but I'm to lazy
    public RAny execute(RContext global, Frame frame) {
        return RNull.getNull();
    }
}
