package com.aql.querytree.expressions;

import com.aql.querytree.ExprVisitor;
import com.sparql_to_aql.exceptions.AqlExprTypeException;

import java.util.Map;

public abstract class Constant extends Expr
{
    protected Constant() { super(); }

    @Override
    public boolean isConstant() { return true; }

    @Override
    public Constant getConstant()     { return this; }

    // ----------------------------------------------------------------
    // ---- Subclass operations

    public boolean isBoolean() { return false; }
    public boolean isString() { return false; }
    public boolean isNumber() { return false; }
    public boolean isArray() { return false; }
    public boolean isObject() { return false; }
    public boolean isNull() { return false; }

    public boolean getBoolean() throws AqlExprTypeException {
        raise(new AqlExprTypeException("Not a boolean: " + this));
        return false;
    }

    public String getString() throws AqlExprTypeException {
        raise(new AqlExprTypeException("Not a string: " + this));
        return null;
    }
    public double getNumber() throws AqlExprTypeException {
        raise(new AqlExprTypeException("Not a double: " + this));
        return Double.NaN;
    }

    public Constant[] getArray() throws AqlExprTypeException {
        raise(new AqlExprTypeException("Not an array: " + this));
        return null;
    }

    public Map<String, Expr> getObject() throws AqlExprTypeException {
        raise(new AqlExprTypeException("Not an object: " +this)); return null;
    }

    // Point to catch all exceptions.
    public static void raise(AqlExprTypeException ex) throws AqlExprTypeException
    {
        throw ex;
    }

    @Override
    public void visit(ExprVisitor visitor) { visitor.visit(this); }

    @Override
    public String toString()
    {
        return null;
    }
}
