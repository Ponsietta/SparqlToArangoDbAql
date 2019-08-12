package com.sparql_to_aql.entities.algebra.aql;

import java.util.*;

public class VarExprList {
    private List<Var> vars  ;
    private LinkedHashMap<Var, Expr> exprs;  // Preserve order.

    public VarExprList(List<Var> vars)
    {
        this.vars = new ArrayList<>(vars) ;
        this.exprs = new LinkedHashMap<>() ;
    }

    public VarExprList(VarExprList other)
    {
        this.vars = new ArrayList<>(other.vars) ;
        this.exprs = new LinkedHashMap<>(other.exprs) ;
    }

    public VarExprList()
    {
        this.vars = new ArrayList<>() ;
        this.exprs = new LinkedHashMap<>() ;
    }

    public VarExprList(Var var, Expr expr)
    {
        this() ;
        add(var, expr) ;
    }

    public List<Var> getVars()          { return vars ; }
    public Map<Var, Expr> getExprs()    { return exprs ; }

    /** Call the action for each (variable, expression) defined.
     *  Not called when there is no expression, just a variable.
     *  Not order preserving.
     */
    /*public void forEachExpr(BiConsumer<Var, Expr> action) {
        exprs.forEach(action);
    }*/

    /** Call the action for each variable, in order.
     *  The expression may be null.
     *  Not called when there is no expression, just a variable.
     */
    /*public void forEachVarExpr(BiConsumer<Var, Expr> action) {
        //*  See {@link #forEach}
        getVars().forEach((v) -> {
            // Maybe null.
            Expr e = exprs.get(v) ;
            action.accept(v, e);
        }) ;
    }*/

    /** Call the action for each variable, in order. */
    /*public void forEachVar(Consumer<Var> action) {
        getVars().forEach((v) -> {
            action.accept(v);
        }) ;
    }*/

    public boolean contains(Var var) { return vars.contains(var) ; }
    public boolean hasExpr(Var var) { return exprs.containsKey(var) ; }

    public Expr getExpr(Var var) { return exprs.get(var) ; }

    // Or Binding.get(var, NamedExprList)
    /*public Node get(Var var, Binding binding, FunctionEnv funcEnv)
    {
        Expr expr = exprs.get(var) ;
        if ( expr == null )
            return binding.get(var) ;

        try {
            NodeValue nv = expr.eval(binding, funcEnv) ;
            if ( nv == null )
                return null ;
            return nv.asNode() ;
        } catch (ExprEvalException ex)
        //{ Log.warn(this, "Eval failure "+expr+": "+ex.getMessage()) ; }
        { }
        return null ;
    }*/

    public void add(Var var)
    {
        // Checking here controls whether duplicate variables are allowed.
        // Duplicates with expressions are not allowed (add(Var, Expr))
        // See ARQ.allowDuplicateSelectColumns

        // Every should work either way round if this is enabled.
        // Checking is done in Query for adding result vars, and group vars.
        // if ( vars.contains(var) )
        vars.add(var) ;
    }

    //TODO change exceptions thrown below.. maybe custom exception?
    public void add(Var var, Expr expr)
    {
        if ( expr == null )
        {
            add(var) ;
            return ;
        }

        if ( var == null )
            throw new RuntimeException("Attempt to add a named expression with a null variable") ;
        if ( exprs.containsKey(var) )
            throw new RuntimeException("Attempt to assign an expression again") ;
        add(var) ;
        exprs.put(var, expr) ;
    }

    public void addAll(VarExprList other)
    {
        for ( Var v : other.vars )
        {
            Expr e = other.getExpr( v );
            add( v, e );
        }
    }

    public void remove(Var var) {
        vars.remove(var) ;
        exprs.remove(var) ;
    }

    public void clear() {
        vars.clear();
        exprs.clear();
    }

    public int size() { return vars.size() ; }
    public boolean isEmpty() { return vars.isEmpty() ; }

    @Override
    public int hashCode()
    {
        int x = vars.hashCode() ^ exprs.hashCode() ;
        return x ;
    }

    @Override
    public boolean equals(Object other)
    {
        if ( this == other) return true ;
        if ( ! ( other instanceof VarExprList ) )
            return false ;
        VarExprList x = (VarExprList)other ;
        return Objects.equals(vars, x.vars) && Objects.equals(exprs, x.exprs) ;
    }

    @Override
    public String toString()
    {
        return vars.toString() + " // "+exprs.toString();
    }
}
