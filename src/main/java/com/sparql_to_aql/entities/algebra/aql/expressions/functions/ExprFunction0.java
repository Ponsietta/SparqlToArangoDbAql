package com.sparql_to_aql.entities.algebra.aql.expressions.functions;

import com.sparql_to_aql.entities.algebra.aql.expressions.Expr;
import com.sparql_to_aql.entities.algebra.aql.expressions.ExprFunction;

public abstract class ExprFunction0 extends ExprFunction {

    protected ExprFunction0(String fName) { this(fName, null); }

    protected ExprFunction0(String fName, String opSign)
    {
        super(fName, opSign);
    }

    @Override
    public Expr getArg(int i)       { return null; }

    /*@Override
    public int hashCode()           { return getFunctionSymbol().hashCode(); }*/

    @Override
    public int numArgs()            { return 0; }

    public abstract Expr copy();

    /*@Override
    public void visit(ExprVisitor visitor) { visitor.visit(this); }
    public Expr apply(ExprTransform transform) { return transform.transform(this); }*/
}
