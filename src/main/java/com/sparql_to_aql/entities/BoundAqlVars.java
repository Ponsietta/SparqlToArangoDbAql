package com.sparql_to_aql.entities;

import com.aql.querytree.expressions.Expr;
import com.aql.querytree.expressions.ExprList;
import com.aql.querytree.expressions.ExprVar;
import com.aql.querytree.expressions.functions.Expr_NotNull;
import com.sparql_to_aql.utils.AqlUtils;
import java.util.ArrayList;
import java.util.List;


/**
 * Due to OPTIONAL or VALUES clauses, a SPARQL variable might not be bound (canBeNull = true) and the variable could be bound to one of multiple values.
 * We use this class to store all the AQL variables that can be bound to the SPARQL variable
 */
public class BoundAqlVars {
    private List<BoundAqlVarDetails> possibleAqlVars;

    // consider renaming canBeNull to something else.. reason being that null and undefined/unbound are not the same in SPARQL
    // when computing the query.. if a variable is bound to a null value and we join it in SPARQL, it's not treated as an optional variable and an equality condition must be satisfied
    // what if we rename it to canBeUnbound?? that way we know that obviously when a projection occurs, the variable HAS to be bound (even if to a null value)
    private boolean canBeNull;

    public BoundAqlVars(List<BoundAqlVarDetails> possibleAqlVars){
        this.possibleAqlVars = possibleAqlVars;
    }

    public BoundAqlVars(BoundAqlVarDetails possibleAqlVars){
        this.possibleAqlVars = new ArrayList<>();
        addVar(possibleAqlVars);
    }

    public BoundAqlVars(String aqlVarName){
        this.possibleAqlVars = new ArrayList<>();
        addVar(aqlVarName);
    }

    public BoundAqlVars(String aqlVarName, boolean canBeNull){
        this.possibleAqlVars = new ArrayList<>();
        this.canBeNull = canBeNull;
        addVar(aqlVarName);
    }

    public List<BoundAqlVarDetails> getVars(){
        return possibleAqlVars;
    }

    public String getFirstVarName(){
        return possibleAqlVars.get(0).getAqlVarName();
    }

    public void addVar(BoundAqlVarDetails varDetails){
        possibleAqlVars.add(varDetails);
    }

    public void addVar(String aqlVarName){
        possibleAqlVars.add(new BoundAqlVarDetails(aqlVarName));
    }

    public void addVar(String aqlVarName, boolean canBeNull){
        this.canBeNull = canBeNull;
        possibleAqlVars.add(new BoundAqlVarDetails(aqlVarName));
    }

    public void addVars(List<BoundAqlVarDetails> varsDetails){
        possibleAqlVars.addAll(varsDetails);
    }

    public Expr asExpr(){
        if(possibleAqlVars.size() == 1){
            return new ExprVar(getFirstVarName());
        }

        ExprList exprList = new ExprList();
        for(BoundAqlVarDetails v: possibleAqlVars) {
            exprList.add(new ExprVar(v.getAqlVarName()));
        }

        return new Expr_NotNull(exprList);
    }

    /**
     * Get the variable information in expression form
     * We use the NOT_NULL function in AQL (like COALESCE in SQL) - useful for when we have optional variables in multiple optional patterns or in some values clause and we want to keep the first non-null variable
     * @param subAttribute
     * @return
     */
    public Expr asExpr(String subAttribute){
        if(possibleAqlVars.size() == 1){
            return new ExprVar(AqlUtils.buildVar(getFirstVarName(), subAttribute));
        }

        ExprList exprList = new ExprList();
        for(BoundAqlVarDetails v: possibleAqlVars) {
            exprList.add(new ExprVar(AqlUtils.buildVar(v.getAqlVarName(), subAttribute)));
        }

        return new Expr_NotNull(exprList);
    }

    public boolean canBeNull(){
        return canBeNull;
    }

    public void updateCanBeNull(boolean canBeNull){
        this.canBeNull = canBeNull;
    }

    public void removeAllVars(){
        possibleAqlVars = new ArrayList<>();
    }
}
