package com.sparql_to_aql;

import com.aql.querytree.AqlQueryNode;
import com.aql.querytree.expressions.ExprList;
import com.aql.querytree.expressions.ExprVar;
import com.aql.querytree.expressions.constants.Const_String;
import com.aql.querytree.expressions.functions.*;
import com.aql.querytree.operators.*;
import com.aql.querytree.resources.IterationResource;
import com.sparql_to_aql.constants.ArangoAttributes;
import com.sparql_to_aql.constants.ArangoDataModel;
import com.sparql_to_aql.constants.ArangoDatabaseSettings;
import com.sparql_to_aql.constants.NodeRole;
import com.sparql_to_aql.entities.BoundAqlVars;
import com.sparql_to_aql.utils.AqlUtils;
import com.sparql_to_aql.utils.RewritingUtils;
import com.sparql_to_aql.utils.VariableGenerator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.op.*;

import java.util.*;

public class ArqToAqlTreeVisitor_BasicApproach extends ArqToAqlTreeVisitor
{
    public ArqToAqlTreeVisitor_BasicApproach(List<String> defaultGraphNames, List<String> namedGraphs){
        super(defaultGraphNames, namedGraphs, ArangoDataModel.D);
    }

    public ArqToAqlTreeVisitor_BasicApproach(List<String> defaultGraphNames, List<String> namedGraphs, VariableGenerator forLoopVarGen, VariableGenerator assignmentVarGen, VariableGenerator graphVertexVarGen, VariableGenerator graphEdgeVarGen, VariableGenerator graphPathVarGen){
        super(defaultGraphNames, namedGraphs, ArangoDataModel.D, forLoopVarGen, assignmentVarGen, graphVertexVarGen, graphEdgeVarGen, graphPathVarGen);
    }

    @Override
    public void visit(OpBGP opBgp){
        AqlQueryNode currAqlNode = null;
        Map<String, BoundAqlVars> usedVars = new HashMap<>();

        for(Triple triple : opBgp.getPattern()){
            //keep list of FILTER clauses per triple
            ExprList filterConditions = new ExprList();

            String iterationVar = forLoopVarGenerator.getNew();
            AqlQueryNode aqlNode = new IterationResource(iterationVar, new ExprVar(ArangoDatabaseSettings.DocumentModel.rdfCollectionName));

            //if there are default graphs specified, filter by those
            AddDefaultGraphFilters(iterationVar, filterConditions);

            ProcessTripleParts(triple, iterationVar, filterConditions, usedVars);
            aqlNode = RewritingUtils.AddFilterConditionsIfPresent(aqlNode, filterConditions);

            if(currAqlNode == null) {
                currAqlNode = aqlNode;
            }
            else {
                currAqlNode = new OpNest(currAqlNode, aqlNode);
            }
        }

        //add used vars in bgp to list
        boundSparqlVariablesByOp.setSparqlVariablesByOp(opBgp, usedVars);
        createdAqlNodes.add(currAqlNode);
    }

    @Override
    public void visit(OpQuadPattern opQuadPattern){
        //each quad in a quad pattern will have the same graph node (ie. same graph iri/variable).. so we can handle this more easily
        Node graphNode = opQuadPattern.getGraphNode();
        AqlQueryNode currAqlNode = null;
        Map<String, BoundAqlVars> usedVars = new HashMap<>();
        boolean firstTripleBeingProcessed = true;

        //using this variable, we will make sure the graph name of every triple matching the BGP is in the same graph
        String outerGraphVarToMatch = "";

        for(Triple triple : opQuadPattern.getBasicPattern()){
            //keep list of FILTER clauses per triple
            ExprList filterConditions = new ExprList();

            String iterationVar = forLoopVarGenerator.getNew();
            AqlQueryNode aqlNode = new IterationResource(iterationVar, new ExprVar(ArangoDatabaseSettings.DocumentModel.rdfCollectionName));

            //if this is the first for loop and there are named graphs specified, add filters for those named graphs
            if(firstTripleBeingProcessed){
                outerGraphVarToMatch = AqlUtils.buildVar(iterationVar, ArangoAttributes.GRAPH_NAME, ArangoAttributes.VALUE);

                if(graphNode.isVariable()){
                    AddNamedGraphFilters(iterationVar, filterConditions);

                    //bind graph var
                    usedVars.put(graphNode.getName(), new BoundAqlVars(AqlUtils.buildVar(iterationVar, ArangoAttributes.GRAPH_NAME)));
                }
                else{
                    //add filter with specific named graph
                    filterConditions.add(new Expr_Equals(new ExprVar(outerGraphVarToMatch), new Const_String(graphNode.getURI())));
                }
            }
            else{
                //make sure that graph name for consecutive triples matches the one of the first triple
                filterConditions.add(new Expr_Equals(new ExprVar(AqlUtils.buildVar(iterationVar, ArangoAttributes.GRAPH_NAME, ArangoAttributes.VALUE)), new ExprVar(outerGraphVarToMatch)));
            }

            ProcessTripleParts(triple, iterationVar, filterConditions, usedVars);
            aqlNode = RewritingUtils.AddFilterConditionsIfPresent(aqlNode, filterConditions);

            if(currAqlNode == null) {
                currAqlNode = aqlNode;
            }
            else {
                currAqlNode = new OpNest(currAqlNode, aqlNode);
            }

            firstTripleBeingProcessed = false;
        }

        //add used vars in bgp to list
        boundSparqlVariablesByOp.setSparqlVariablesByOp(opQuadPattern, usedVars);
        createdAqlNodes.add(currAqlNode);
    }

    private void ProcessTripleParts(Triple triple, String iterationVar, ExprList filterConditions, Map<String, BoundAqlVars> usedVars){
        RewritingUtils.ProcessTripleNode(triple.getSubject(), NodeRole.SUBJECT, iterationVar, filterConditions, usedVars);
        RewritingUtils.ProcessTripleNode(triple.getPredicate(), NodeRole.PREDICATE, iterationVar, filterConditions, usedVars);
        RewritingUtils.ProcessTripleNode(triple.getObject(), NodeRole.OBJECT, iterationVar, filterConditions, usedVars);
    }
}
