package com.sparql_to_aql.entities.algebra.aql.operators;

import com.sparql_to_aql.entities.algebra.aql.AqlConstants;
import com.sparql_to_aql.entities.algebra.aql.OpVisitor;
import com.sparql_to_aql.entities.algebra.aql.SortCondition;

import java.util.List;

public class OpSort extends OpModifier
{
    private List<SortCondition> conditions;

    public OpSort(Op subOp, List<SortCondition> conditions)
    {
        super(subOp);
        this.conditions = conditions;
    }

    public List<SortCondition> getConditions() { return conditions; }

    @Override
    public String getName()                 { return AqlConstants.keywordSort; }
    @Override
    public void visit(OpVisitor opVisitor)  { opVisitor.visit(this); }
    @Override
    public Op1 copy(Op subOp)                { return new OpSort(subOp, conditions); }

    /*@Override
    public Op apply(Transform transform, Op subOp)
    { return transform.transform(this, subOp) ; }

    @Override
    public int hashCode()
    {
        return conditions.hashCode() ^ getSubOp().hashCode() ;
    }

    @Override
    public boolean equalTo(Op other, NodeIsomorphismMap labelMap)
    {
        if ( ! (other instanceof OpOrder) ) return false ;
        OpOrder opOrder = (OpOrder)other ;

        if ( ! opOrder.getConditions().equals(this.getConditions()) )
            return false ;

        //
        return getSubOp().equalTo(opOrder.getSubOp(), labelMap) ;
    }*/
}
