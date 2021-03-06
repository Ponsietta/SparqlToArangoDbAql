package com.aql.querytree.expressions.functions;

import com.aql.querytree.AqlConstants;
import com.aql.querytree.expressions.Expr;

public class Expr_GreaterThanOrEqual extends ExprFunction2{
    private static final String functionName = "gte";
    private static final String symbol = AqlConstants.SYM_GTE;

    public Expr_GreaterThanOrEqual(Expr left, Expr right)
    {
        super(left, right, functionName, symbol);
    }

    @Override
    public Expr copy(Expr e1, Expr e2) {  return new Expr_GreaterThanOrEqual(e1 , e2); }

}
