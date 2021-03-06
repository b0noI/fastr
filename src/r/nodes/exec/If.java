package r.nodes.exec;

import r.*;
import r.builtins.*;
import r.data.*;
import r.data.internal.*;
import r.errors.*;
import r.nodes.ast.*;
import r.runtime.*;


public class If extends BaseR {
    @Child RNode cond;
    @Child RNode trueBranch;
    @Child RNode falseBranch;

    private static final boolean DEBUG_IF = false;

    public If(ASTNode ast, RNode cond, RNode trueBranch, RNode falseBranch) {
        super(ast);
        this.cond = adoptChild(cond);
        this.trueBranch = adoptChild(trueBranch);
        this.falseBranch = adoptChild(falseBranch);
    }

    // The condition is treated as follows:
    //   - no special node for a 1-value logical argument
    //   - a special intermediate conversion node for multi-value logical argument, another for multi-value integer argument
    //   - a generic conversion node that can convert anything
    @Override
    public final Object execute(Frame frame) {
        int ifVal;

        assert Utils.check(getNewNode() == null);
        try {
            if (DEBUG_IF) Utils.debug("executing condition");
            ifVal = cond.executeScalarLogical(frame);
            if (DEBUG_IF) Utils.debug("condition got expected result");
        } catch (SpecializationException e) {
            RAny result = (RAny) e.getResult();
            if (getNewNode() != null) {
                return ((If)getNewNode()).executeWithFailedCond(frame, result);
            }
            return executeWithFailedCond(frame, result);

        }
        if (getNewNode() != null) {
            return ((If)getNewNode()).executeWithCond(frame, ifVal);
        }
        return executeWithCond(frame, ifVal);
    }

    public Object executeWithFailedCond(Frame frame, RAny result) {
        if (DEBUG_IF) Utils.debug("condition got unexpected result, inserting 2nd level cast node");

        ConvertToLogicalOne castNode = ConvertToLogicalOne.createAndInsertNode(cond, result);
        int ifVal = castNode.executeScalarLogical(result);
        if (getNewNode() != null) {
            return ((If)getNewNode()).executeWithCond(frame, ifVal);
        }
        return executeWithCond(frame, ifVal);
    }

    public Object executeWithCond(Frame frame, int ifVal) {
        if (ifVal == RLogical.TRUE) { // Is it the right ordering ?
            return trueBranch.execute(frame);
        } else if (ifVal == RLogical.FALSE) {
            return falseBranch.execute(frame);
        }
        throw RError.getUnexpectedNA(getAST());
    }

    @Override
    protected <N extends RNode> N replaceChild(RNode oldNode, N newNode) {
        assert oldNode != null;
        if (cond == oldNode) {
            cond = newNode;
            return adoptInternal(newNode);
        }
        if (trueBranch == oldNode) {
            trueBranch = newNode;
            return adoptInternal(newNode);
        }
        if (falseBranch == oldNode) {
            falseBranch = newNode;
            return adoptInternal(newNode);
        }
        return super.replaceChild(oldNode, newNode);
    }

    public static class IfNoElse extends BaseR {
        @Child RNode cond;
        @Child RNode trueBranch;

        public IfNoElse(ASTNode ast, RNode cond, RNode trueBranch) {
            super(ast);
            this.cond = adoptChild(cond);
            this.trueBranch = adoptChild(trueBranch);
        }

        @Override
        public final Object execute(Frame frame) {
            int ifVal;

            assert Utils.check(getNewNode() == null);
            try {
                ifVal = cond.executeScalarNonNALogical(frame);
            } catch (SpecializationException e) {
                RAny result = (RAny) e.getResult();
                RNode theCond = cond;
                ConvertToLogicalOne castNode = ConvertToLogicalOne.createAndInsertNode(cond, result);
                If ifnode = new If(ast, castNode, trueBranch, r.nodes.exec.Constant.getNull());
                return replace(theCond, result, ifnode, frame);
            }

            if (ifVal == RLogical.TRUE) { // Is it the right ordering ?
                return trueBranch.execute(frame);
            } else {
                return RNull.getNull();
            }
        }

        @Override
        protected <N extends RNode> N replaceChild(RNode oldNode, N newNode) {
            assert oldNode != null;
            if (cond == oldNode) {
                cond = newNode;
                return adoptInternal(newNode);
            }
            if (trueBranch == oldNode) {
                trueBranch = newNode;
                return adoptInternal(newNode);
            }
            return super.replaceChild(oldNode, newNode);
        }

    }

    public static class IfElse extends BaseR {
        @Child RNode cond;
        @Child RNode trueBranch;
        @Child RNode falseBranch;

        public IfElse(ASTNode ast, RNode cond, RNode trueBranch, RNode falseBranch) {
            super(ast);
            this.cond = adoptChild(cond);
            this.trueBranch = adoptChild(trueBranch);
            this.falseBranch = adoptChild(falseBranch);
        }

        @Override
        public final Object execute(Frame frame) {
            int ifVal;

            assert Utils.check(getNewNode() == null);
            try {
                ifVal = cond.executeScalarNonNALogical(frame);
            } catch (SpecializationException e) {
                RAny result = (RAny) e.getResult();
                RNode theCond = cond;
                ConvertToLogicalOne castNode = ConvertToLogicalOne.createAndInsertNode(cond, result);
                If ifnode = new If(ast, castNode, trueBranch, falseBranch);
                return replace(theCond, result, ifnode, frame);
            }

            if (ifVal == RLogical.TRUE) { // Is it the right ordering ?
                return trueBranch.execute(frame);
            } else {
                return falseBranch.execute(frame);
            }
        }

        @Override
        protected <N extends RNode> N replaceChild(RNode oldNode, N newNode) {
            assert oldNode != null;
            if (cond == oldNode) {
                cond = newNode;
                return adoptInternal(newNode);
            }
            if (trueBranch == oldNode) {
                trueBranch = newNode;
                return adoptInternal(newNode);
            }
            if (falseBranch == oldNode) {
                falseBranch = newNode;
                return adoptInternal(newNode);
            }
            return super.replaceChild(oldNode, newNode);
        }

    }

    // for expressions like  if (cond) { trueBranch ; return } ; restBranch
    //


    public abstract static class IfReturnRest extends BaseR {
        @Child RNode cond;
        @Child RNode trueBranch;
        @Child RNode returnCall;
        @Child RNode restBranch;

        protected RSymbol RETURN_SYMBOL = RSymbol.getSymbol("return");

        public IfReturnRest(ASTNode ast, RNode cond, RNode trueBranch, RNode returnCallArgument, RNode restBranch) {
            super(ast);
            this.cond = adoptChild(cond);
            this.trueBranch = trueBranch == null ? null : adoptChild(trueBranch);
            this.restBranch = adoptChild(restBranch);
            this.returnCall = adoptChild(returnCallArgument); // as long as return is not overridden, just return a value
        }

        @Override
        protected <N extends RNode> N replaceChild(RNode oldNode, N newNode) {
            assert oldNode != null;
            if (cond == oldNode) {
                cond = newNode;
                return adoptInternal(newNode);
            }
            if (trueBranch == oldNode) {
                trueBranch = newNode;
                return adoptInternal(newNode);
            }
            if (returnCall == oldNode) {
                returnCall = newNode;
                return adoptInternal(newNode);
            }
            if (restBranch == oldNode) {
                restBranch = newNode;
                return adoptInternal(newNode);
            }
            return super.replaceChild(oldNode, newNode);
        }

        public static class ReturnBuiltin extends IfReturnRest {

            public ReturnBuiltin(ASTNode ast, RNode cond, RNode trueBranch, RNode returnCallArgument, RNode restBranch) {
                super(ast, cond, trueBranch, returnCallArgument, restBranch);

                assert Utils.check(!RETURN_SYMBOL.builtinIsOverridden());
                final RNode lreturnCallArg = returnCall;
                final ASTNode lreturnCallAST = returnCall.getAST().getParent().getParent();
                SymbolChangeListener listener = new SymbolChangeListener() {
                    public boolean onChange(RSymbol symbol) {
                        RNode oldNode = lreturnCallArg;
                        while(oldNode.getNewNode() != null) {
                            oldNode = oldNode.getNewNode();
                        }
                        RNode[] newArgExprs = new RNode[] { oldNode };
                        IfReturnRest oldIfNode = (IfReturnRest) oldNode.getParent();

                            // we can ignore names because return ignores them
                        RNode fullReturnCall = r.nodes.exec.FunctionCall.FACTORY.create(lreturnCallAST, new RSymbol[]{null}, newArgExprs);
                        RNode newIfNode = new Overridden(lreturnCallAST, oldIfNode.cond, oldIfNode.trueBranch, fullReturnCall, oldIfNode.restBranch);
                        oldIfNode.replace(newIfNode);
                        return false;
                    }
                };
                RETURN_SYMBOL.addChangeListener(listener);
            }

            @Override
            public Object execute(Frame frame) {
                int ifVal;

                assert Utils.check(getNewNode() == null);
                try {
                    ifVal = cond.executeScalarNonNALogical(frame);
                } catch (SpecializationException e) {
                    RAny result = (RAny) e.getResult();
                    RNode theCond = cond;
                    ConvertToLogicalOne castNode = ConvertToLogicalOne.createAndInsertNode(cond, result);
                    IfReturnRest ifnode = new ReturnBuiltin(ast, castNode, trueBranch, returnCall, restBranch);
                    return replace(theCond, result, ifnode, frame);
                }
                assert Utils.check(getNewNode() == null); // TODO: handle recursive rewriting

                if (ifVal == RLogical.TRUE) { // Is it the right ordering ?
                    if (trueBranch != null) {
                        trueBranch.execute(frame);
                    }
                    assert Utils.check(getNewNode() == null);
                    return returnCall.execute(frame); // TODO: handle recursive rewriting
                } else {
                    return restBranch.execute(frame);
                }
            }
        }

        public static class Overridden extends IfReturnRest {

            public Overridden(ASTNode ast, RNode cond, RNode trueBranch, RNode returnCallArgument, RNode restBranch) {
                super(ast, cond, trueBranch, returnCallArgument, restBranch);
            }

            @Override
            public final Object execute(Frame frame) {
                int ifVal;

                assert Utils.check(getNewNode() == null);
                try {
                    ifVal = cond.executeScalarNonNALogical(frame);
                } catch (SpecializationException e) {
                    RAny result = (RAny) e.getResult();
                    RNode theCond = cond;
                    ConvertToLogicalOne castNode = ConvertToLogicalOne.createAndInsertNode(cond, result);
                    IfReturnRest ifnode = new Overridden(ast, castNode, trueBranch, returnCall, restBranch);
                    return replace(theCond, result, ifnode, frame);
                }
                assert Utils.check(getNewNode() == null); // TODO: handle recursive rewriting

                if (ifVal == RLogical.TRUE) { // Is it the right ordering ?
                    if (trueBranch != null) {
                        trueBranch.execute(frame);
                    }
                    assert Utils.check(getNewNode() == null);
                    returnCall.execute(frame); // TODO: handle recursive rewriting
                    assert Utils.check(getNewNode() == null);
                }
                return restBranch.execute(frame);
            }
        }

    }

    // scalar comparison against a constant
    public static class IfConst extends BaseR {
        @Child RNode cond;
        @Child RNode expr;
        @Child RNode trueBranch;
        @Child RNode falseBranch;
        final RAny constant;

        public IfConst(ASTNode ast, RNode cond, RNode expr, RNode trueBranch, RNode falseBranch, RAny constant) {
            super(ast);
            this.cond = adoptChild(cond);
            this.expr = adoptChild(expr);
            this.trueBranch = adoptChild(trueBranch);
            this.falseBranch = adoptChild(falseBranch);
            this.constant = constant;
        }

        @Override
        public final Object execute(Frame frame) {
            assert Utils.check(getNewNode() == null);
            RAny value = (RAny) expr.execute(frame);
            assert Utils.check(getNewNode() == null); // FIXME
            return execute(frame, value);
        }

        @Override
        protected <N extends RNode> N replaceChild(RNode oldNode, N newNode) {
            assert oldNode != null;
            if (cond == oldNode) {
                cond = newNode;
                return adoptInternal(newNode);
            }
            if (expr == oldNode) {
                expr = newNode;
                return adoptInternal(newNode);
            }
            if (trueBranch == oldNode) {
                trueBranch = newNode;
                return adoptInternal(newNode);
            }
            if (falseBranch == oldNode) {
                falseBranch = newNode;
                return adoptInternal(newNode);
            }
            return super.replaceChild(oldNode, newNode);
        }

        public Object execute(Frame frame, RAny value) {
            assert Utils.check(getNewNode() == null);
            try {
                throw new SpecializationException(null);
            } catch (SpecializationException e) {
                Specialized s = Specialized.create(ast, cond, expr, trueBranch, falseBranch, constant, value);
                if (s != null) {
                    replace(s, "install Specialized from IfConst");
                    return s.execute(frame);
                } else {
                    If in = new If(ast, cond, trueBranch, falseBranch);
                    replace(in, "install If from IfConst");
                    return in.execute(frame);
                }
            }
        }

        public abstract static class Comparison {
            public abstract int cmp(RAny value) throws SpecializationException;
        }

        public static class Specialized extends IfConst {
            final Comparison cmp;

            public Specialized(ASTNode ast, RNode cond, RNode expr, RNode trueBranch, RNode falseBranch, RAny constant, Comparison cmp) {
                super(ast, cond, expr, trueBranch, falseBranch, constant);
                this.cmp = cmp;
            }

            public static Specialized create(final ASTNode ast, RNode cond, RNode expr, RNode trueBranch, RNode falseBranch, RAny constant, RAny valueTemplate) {
                // FIXME: the comparison functions could treat the NAs directly

                if (valueTemplate instanceof ScalarDoubleImpl) {
                    if (!(constant instanceof ScalarDoubleImpl || constant instanceof ScalarIntImpl || constant instanceof ScalarLogicalImpl)) {
                        return null;
                    }
                    RDouble dc = constant.asDouble();
                    final double c = dc.getDouble(0);
                    final boolean cIsNAorNaN = RDouble.RDoubleUtils.isNAorNaN(c);
                    Comparison cmp = new Comparison() {
                        @Override
                        public int cmp(RAny value) throws SpecializationException {
                            if (!(value instanceof ScalarDoubleImpl)) {
                                throw new SpecializationException(null);
                            }
                            double v = ((ScalarDoubleImpl) value).getDouble();
                            if (!cIsNAorNaN && !RDouble.RDoubleUtils.isNAorNaN(v)) {
                                return (v == c) ? RLogical.TRUE : RLogical.FALSE;
                            } else {
                                throw RError.getUnexpectedNA(ast);
                            }
                        }
                    };
                    return new Specialized(ast, cond, expr, trueBranch, falseBranch, constant, cmp);
                }
                if (valueTemplate instanceof ScalarIntImpl) {
                    Comparison cmp = null;
                    if (constant instanceof ScalarDoubleImpl) {
                        final double c = ((ScalarDoubleImpl) constant).getDouble();
                        final boolean cIsNAorNaN = RDouble.RDoubleUtils.isNAorNaN(c);
                        cmp = new Comparison() {
                            @Override
                            public int cmp(RAny value) throws SpecializationException {
                                if (!(value instanceof ScalarIntImpl)) {
                                    throw new SpecializationException(null);
                                }
                                double v = Convert.int2double(((ScalarIntImpl) value).getInt());
                                if (!cIsNAorNaN && !RDouble.RDoubleUtils.isNAorNaN(v)) {
                                    return (v == c) ? RLogical.TRUE : RLogical.FALSE;
                                } else {
                                    throw RError.getUnexpectedNA(ast);
                                }
                            }
                        };
                    } else if (constant instanceof ScalarIntImpl || constant instanceof ScalarLogicalImpl) {
                        RInt ic = constant.asInt();
                        final int c = ic.getInt(0);
                        final boolean cIsNA = (c == RInt.NA);
                        cmp = new Comparison() {
                            @Override
                            public int cmp(RAny value) throws SpecializationException {
                                if (!(value instanceof ScalarIntImpl)) {
                                    throw new SpecializationException(null);
                                }
                                int v = ((ScalarIntImpl) value).getInt();
                                if (!cIsNA && v != RInt.NA) {
                                    return (v == c) ? RLogical.TRUE : RLogical.FALSE;
                                } else {
                                    throw RError.getUnexpectedNA(ast);
                                }
                            }
                        };
                    } else {
                        return null;
                    }
                    return new Specialized(ast, cond, expr, trueBranch, falseBranch, constant, cmp);
                }
                if (valueTemplate instanceof ScalarLogicalImpl) {
                    if (!(constant instanceof ScalarLogicalImpl || constant instanceof ScalarIntImpl)) {
                        return null;
                    }
                    final int c = constant.asLogical().getLogical(0);
                    final boolean cIsNA = (c == RLogical.NA);
                    Comparison cmp = new Comparison() {
                        @Override
                        public int cmp(RAny value) throws SpecializationException {
                            if (!(value instanceof ScalarLogicalImpl)) {
                                throw new SpecializationException(null);
                            }
                            int v = ((ScalarLogicalImpl) value).getLogical();
                            if (!cIsNA && v != RLogical.NA) {
                                return (v == c) ? RLogical.TRUE : RLogical.FALSE;
                            } else {
                                throw RError.getUnexpectedNA(ast);
                            }
                        }
                    };
                    return new Specialized(ast, cond, expr, trueBranch, falseBranch, constant, cmp);
                }
                // FIXME: add a generic comparison against constant?
                return null;
            }

            @Override
            public final Object execute(Frame frame, RAny value) {
                assert Utils.check(getNewNode() == null);
                try {
                    int ifVal = cmp.cmp(value);
                    if (ifVal == RLogical.TRUE) {
                        assert Utils.check(getNewNode() == null); // FIXME
                        return trueBranch.execute(frame);
                    } else {
                        assert Utils.check(getNewNode() == null); // FIXME
                        assert Utils.check(ifVal == RLogical.FALSE);
                        return falseBranch.execute(frame);
                    }
                 } catch (SpecializationException e) {
                     If in = new If(ast, cond, trueBranch, falseBranch);
                     replace(in, "install If from IfConst.Specialized");
                     return in.execute(frame);
                 }
            }

        }

    }
}
