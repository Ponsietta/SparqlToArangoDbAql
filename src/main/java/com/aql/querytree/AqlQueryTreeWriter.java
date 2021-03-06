package com.aql.querytree;

import com.aql.querytree.expressions.*;
import com.aql.querytree.expressions.functions.*;
import com.aql.querytree.operators.*;
import com.aql.querytree.resources.AssignedResource;
import com.aql.querytree.resources.GraphIterationResource;
import com.aql.querytree.resources.IterationResource;
import org.apache.jena.atlas.io.IndentedWriter;

import java.io.OutputStream;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class AqlQueryTreeWriter implements NodeVisitor, ExprVisitor {
    static final int BLOCK_INDENT = 2;

    IndentedWriter out;

    public AqlQueryTreeWriter(OutputStream _out)
    {
        out = new IndentedWriter(_out);
        out.setUnitIndent(BLOCK_INDENT);
    }

    private void visitOpN(OpN op) {
        start(op, true);
        for (Iterator<AqlQueryNode> iter = op.iterator(); iter.hasNext();)
        {
            AqlQueryNode sub = iter.next();
            sub.visit(this);
            if(iter.hasNext())
                out.println();
        }
        finish(op);
    }

    private void visitOp2(Op2 op, ExprList exprs) {
        start(op, true);
        op.getLeft().visit(this);
        out.ensureStartOfLine();
        op.getRight().visit(this);

        /*if (exprs != null) {
            out.ensureStartOfLine();
            WriterExpr.output(out, exprs, sContext);
        }*/
        finish(op);
    }

    private void visitOp1(Op1 op) {
        start(op, true);
        op.getChild().visit(this);
        finish(op);
    }

    public void visit(IterationResource forloop){
        start(forloop, false);
        start();
        boolean useBrackets = false;
        out.print(forloop.getIterationVar().getVarName());
        out.print(" ");
        Expr dataArrayExpr = forloop.getDataArrayExpr();
        if(!(dataArrayExpr instanceof ExprVar)) {
            useBrackets = true;
            out.print("(");
        }

        forloop.getDataArrayExpr().visit(this);
        if(useBrackets)
            out.print(")");

        finish();
        finish(forloop);
    }

    public void visit(GraphIterationResource graphForloop){
        out.print("FOR " + graphForloop.getVertexVar());

        if(graphForloop.getEdgeVar() != null){
            out.print(", " + graphForloop.getEdgeVar());

            if(graphForloop.getPathVar() != null)
                out.print(", " + graphForloop.getPathVar());
        }

        out.print(" IN ");

        if(graphForloop.getMin() != null){
            out.print(graphForloop.getMin().toString());
            if(graphForloop.getMax() != null)
                out.print(".." + graphForloop.getMax());
        }

        out.print(" " + graphForloop.getDirectionAsString() + " " + graphForloop.getStartVertex() + " ");

        if(graphForloop.getGraph()!= null){
            out.print(" GRAPH " + graphForloop.getGraph());
        }
        else{
            out.print(String.join(", ", graphForloop.getEdgeCollections()));
        }

        out.println();
    }

    public void visit(OpFilter op){
        start(op, false);
        formatExprList(op.getExprs());
        out.println();
        op.getChild().visit(this);

        finish(op);
    }

    public void visit(AssignedResource opAssign){
        start(opAssign, false);
        start();
        out.print(opAssign.getVariableName());
        out.print(", ");
        if(opAssign.assignsExpr()){
            opAssign.getExpr().visit(this);
        }else{
            out.print("(");
            opAssign.getOp().visit(this);
            out.print(")");
        }
        finish();
        finish(opAssign);
    }

    public void visit(OpNest opNest){
        visitOp2(opNest, null);
    }

    public void visit(OpSort op){
        start(op, false);

        // Write conditions
        start();

        boolean first = true;
        for (SortCondition sc : op.getConditions()) {
            if (!first)
                out.print(" ");
            first = false;
            formatSortCondition(sc);
        }
        finish();
        out.println();
        op.getChild().visit(this);;
        finish(op);
    }

    private void formatSortCondition(SortCondition sc) {
        String tag = null;

        if(sc.getDirection() == SortCondition.Direction.ASC)
        {
            tag = "ASC";
            start(tag, false);
        }
        else if(sc.getDirection() == SortCondition.Direction.DESC) {
            tag = "DESC";
            start(tag, false);
        }

        sc.getExpression().visit(this);

        if (tag != null)
            finish();
    }

    public void visit(OpProject op){
        start(op, false);
        //writeVarList(op.getExprs());
        out.println();
        op.getChild().visit(this);
        finish(op);
    }

    public void visit(OpLimit op){
        start(op, false);
        writeLongOrDefault(op.getStart());
        out.print(" ");
        writeLongOrDefault(op.getLength());
        out.println();
        op.getChild().visit(this);
        finish(op);
    }

    private void writeLongOrDefault(Long value) {
        String x = "_";
        if (value != Long.MAX_VALUE)
            x = Long.toString(value);
        out.print(x);
    }

    public void visit(OpCollect op){
        op.getChild().visit(this);

        out.print("COLLECT ");
        op.getVarExprs().visit(this);

        if(op.isWithCount())
            out.print("WITH COUNT INTO " + op.getCountVar().getVarName());
        out.println();
    }

    public void visit(ExprFunction1 expr){
        if(expr.getOpName() != null){
            out.print(expr.getOpName());
            expr.getArg().visit(this);
        }
        else{
            out.print(expr.getFunctionName() + "(");
            expr.getArg().visit(this);
            out.print(")");
        }
    }

    public void visit(ExprFunction2 expr){
        if(expr.getOpName() != null){
            out.print("(");
            out.print(expr.getOpName());
            out.print(" ");
            expr.getArg1().visit(this);
            out.print(" ");
            expr.getArg2().visit(this);
            out.print(")");
        }
        else if(expr instanceof Expr_In){
            expr.getArg1().visit(this);
            out.print(" IN ");
            expr.getArg2().visit(this);
        }
    }

    public void visit(ExprFunction3 expr){
        if(expr instanceof Expr_Conditional){
            expr.getArg1().visit(this);
            out.print(" ? ");
            expr.getArg2().visit(this);
            out.print(" : ");
            expr.getArg3().visit(this);
        }
    }

    public void visit(ExprFunctionN expr){
        out.print(expr.getFunctionName() + "(");
        List<Expr> functionArgs = expr.getArgs();
        for(int i=0; i < functionArgs.size(); i++){
            functionArgs.get(i).visit(this);
            if(i < functionArgs.size()-1)
                out.print(",");
        }
        out.print(")");
    }

    public void visit(ExprSubquery expr){
        expr.getSubquery().visit(this);
    }

    public void visit(Constant expr){
        out.print(expr.toString());
    }

    public void visit(ExprVar expr){
        out.print(expr.getVarName());
    }

    public void visit(Var var){
        out.print(var.getVarName());
    }

    public void visit(VarExprList exprs){
        Iterator<Map.Entry<Var, Expr>> it = exprs.getExprs().entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Var, Expr> pair = it.next();
            out.print(pair.getKey().getVarName() + ": ");
            pair.getValue().visit(this);
            if(it.hasNext()){
                out.print(", ");
            }
        }
    }

    public void visit(OpSequence opSequence){
        visitOpN(opSequence);
    }

    public void finishVisit()
    {
        out.println();
        out.flush();
        out.setAbsoluteIndent(0);
    }

    private void start(AqlQueryNode op, boolean newline) {
        start(op.getName(), newline);
    }

    private void start(String tag, boolean newline) {
        out.print("(");
        out.print(tag);

        if(newline)
            out.println();
        else
            out.print(" ");

        out.incIndent();
    }

    private void start() {
        out.print("(");
    }

    private void finish(AqlQueryNode node) {
        out.decIndent();
        out.print(")");
    }

    private void finish() {
        out.print(")");
    }

    private void formatExprList(ExprList exprList){
        if(exprList.size() == 0){
            out.print("()");
            return;
        }

        if(exprList.size() == 1){
            exprList.get(0).visit(this);
            return;
        }

        out.print("(");

        for(int i=0; i < exprList.size(); i++){
            exprList.get(i).visit(this);
            if(i < exprList.size()-1)
                out.print(" ");
        }

        out.print(")");
    }
}
