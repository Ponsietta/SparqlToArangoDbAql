package com.sparql_to_aql.entities.algebra.aql.operators;

import com.sparql_to_aql.entities.algebra.aql.AqlConstants;
import com.sparql_to_aql.entities.algebra.aql.OpVisitor;

public class OpLimit extends OpModifier
{
    private long start;
    private long length;

    public OpLimit(Op subOp, long start, long length)
    {
        super(subOp);
        this.start = start;
        this.length = length;
    }

    public long getLength()         { return length; }

    public long getStart()          { return start; }

    public Op copy()
    {
        return null;
    }

    @Override
    public String getName()                 { return AqlConstants.keywordLimit; }

    @Override
    public void visit(OpVisitor opVisitor)  { opVisitor.visit(this); }

    @Override
    public Op1 copy(Op subOp)                { return new OpLimit(subOp, start, length); }

    /*@Override
    public Op apply(Transform transform, Op subOp)
    { return transform.transform(this, subOp); }

    @Override
    public int hashCode()
    {
        return getSubOp().hashCode() ^ (int)(start&0xFFFFFFFF) ^ (int)(length&0xFFFFFFFF);
    }

    @Override
    public boolean equalTo(Op other, NodeIsomorphismMap labelMap)
    {
        if ( ! (other instanceof OpSlice) ) return false;
        OpSlice opSlice = (OpSlice)other;
        if ( opSlice.start != start || opSlice.length != length )
            return false;
        return getSubOp().equalTo(opSlice.getSubOp(), labelMap);
    }*/
}
