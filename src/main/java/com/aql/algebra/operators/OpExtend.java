package com.aql.algebra.operators;

import com.aql.algebra.OpVisitor;

import java.util.ArrayList;
import java.util.List;

//TODO consider removing this if we're going to use OpNest
//difference between this Op and OpAssign is that OpExtend is used for nested LETs and OpAssign
//is used to assign a whole subOp or expression into a variable
public class OpExtend extends Op1{

    //keep a list of assignments instead of one, to reduce nested operations
    private List<OpAssign> assignments;

    public OpExtend(Op sub, OpAssign assignment) {
        super(sub);
        this.assignments = new ArrayList<>();
        this.assignments.add(assignment);
    }

    public OpExtend(Op sub, List<OpAssign> assignments) {
        super(sub);
        this.assignments = assignments;
    }

    @Override
    public String getName() { return "extend"; }

    @Override
    public void visit(OpVisitor opVisitor) { opVisitor.visit(this); }

    @Override
    public Op1 copy(Op subOp){
        return new OpExtend(subOp, assignments);
    }

}
