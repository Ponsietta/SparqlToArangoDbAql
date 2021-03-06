package com.sparql_to_aql.entities.algebra.transformers;

import com.sparql_to_aql.entities.algebra.OpDistinctProject;
import org.apache.jena.sparql.algebra.Op;
import org.apache.jena.sparql.algebra.TransformCopy;
import org.apache.jena.sparql.algebra.op.OpDistinct;
import org.apache.jena.sparql.algebra.op.OpProject;
import org.apache.jena.sparql.algebra.op.OpSlice;

public class OpProjectOverSliceTransformer extends TransformCopy {
    @Override
    public Op transform(OpSlice opSlice, Op subOp) {
        if(!(subOp instanceof OpProject) || subOp instanceof OpDistinctProject){
            return opSlice;
        }

        OpProject p = (OpProject) subOp;

        OpSlice newOpSlice = new OpSlice(p.getSubOp(), opSlice.getStart(), opSlice.getLength());

        return new OpProject(newOpSlice, p.getVars());
    }
}
