package com.aql.querytree.expressions.functions;

import com.aql.querytree.ExprVisitor;
import com.aql.querytree.expressions.Expr;
import com.aql.querytree.expressions.ExprFunction;

public abstract class ExprFunction3 extends ExprFunction {
    protected final Expr expr1;
    protected final Expr expr2;
    protected final Expr expr3;

    protected ExprFunction3(Expr expr1, Expr expr2, Expr expr3, String fName) { this(expr1, expr2, expr3, fName, null); }

    protected ExprFunction3(Expr expr1, Expr expr2, Expr expr3, String fName, String opSign)
    {
        super(fName, opSign);
        this.expr1 = expr1;
        this.expr2 = expr2;
        this.expr3 = expr3;
    }

    public Expr getArg1() { return expr1; }
    public Expr getArg2() { return expr2; }
    public Expr getArg3() { return expr3; }

    @Override
    public Expr getArg(int i)
    {
        if ( i == 1 )
            return expr1;
        if ( i == 2 )
            return expr2;
        if ( i == 3 )
            return expr3;
        return null;
    }

    @Override
    public int numArgs() { return 3; }

    public abstract Expr copy(Expr arg1, Expr arg2, Expr arg3);

    @Override
    public void visit(ExprVisitor visitor) { visitor.visit(this); }

}
