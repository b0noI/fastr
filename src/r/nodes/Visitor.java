package r.nodes;

public interface Visitor {
    void visit(If iff);
    void visit(Repeat repeat);
    void visit(While wh1le);
    void visit(Sequence sequence);

    void visit(Mult mult);
    void visit(Add add);

    void visit(Not n);

    void visit(Constant constant);
    void visit(VariableAccess readVariable);
    void visit(FieldAccess fieldAccess);

    void visit(SimpleAssignVariable assign);

    void visit(FunctionCall functionCall);
}