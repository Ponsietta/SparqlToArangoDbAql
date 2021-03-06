package com.sparql_to_aql;

import com.aql.querytree.AqlQueryNode;
import com.aql.querytree.expressions.ExprList;
import com.aql.querytree.expressions.ExprVar;
import com.aql.querytree.expressions.constants.Const_String;
import com.aql.querytree.expressions.functions.Expr_Equals;
import com.aql.querytree.operators.*;
import com.aql.querytree.resources.GraphIterationResource;
import com.aql.querytree.resources.IterationResource;
import com.sparql_to_aql.constants.ArangoAttributes;
import com.sparql_to_aql.constants.ArangoDataModel;
import com.sparql_to_aql.constants.ArangoDatabaseSettings;
import com.sparql_to_aql.entities.BoundAqlVars;
import com.sparql_to_aql.utils.AqlUtils;
import com.sparql_to_aql.utils.RewritingUtils;
import com.sparql_to_aql.utils.VariableGenerator;
import org.apache.jena.graph.Node;
import org.apache.jena.graph.Triple;
import org.apache.jena.sparql.algebra.op.*;
import java.util.*;

public class ArqToAqlTreeVisitor_GraphApproach extends ArqToAqlTreeVisitor {

    public ArqToAqlTreeVisitor_GraphApproach(List<String> defaultGraphNames, List<String> namedGraphs){
        super(defaultGraphNames, namedGraphs, ArangoDataModel.G);
    }

    public ArqToAqlTreeVisitor_GraphApproach(List<String> defaultGraphNames, List<String> namedGraphs, VariableGenerator forLoopVarGen, VariableGenerator assignmentVarGen, VariableGenerator graphVertexVarGen, VariableGenerator graphEdgeVarGen, VariableGenerator graphPathVarGen){
        super(defaultGraphNames, namedGraphs, ArangoDataModel.G, forLoopVarGen, assignmentVarGen, graphVertexVarGen, graphEdgeVarGen, graphPathVarGen);
    }

    @Override
    public void visit(OpBGP opBgp){
        AqlQueryNode currAqlNode = null;
        Map<String, BoundAqlVars> usedVars = new HashMap<>();

        for(Triple triple : opBgp.getPattern()){
            Node subject = triple.getSubject();
            Node object = triple.getObject();
            String startVertex;
            boolean startVertexIsSubject = true;
            GraphIterationResource.TraversalDirection graphTraversalDirection = GraphIterationResource.TraversalDirection.OUTBOUND;

            // we want to use an INBOUND graph traversal when we know the object but not the subject
            if(subject.isVariable() && !usedVars.containsKey(subject.getName()) && (object.isURI() || object.isLiteral() || (object.isVariable() && usedVars.containsKey(object.getName())))){
                String forloopVar = forLoopVarGenerator.getNew();
                AqlQueryNode new_forloop;
                if(object.isLiteral()){
                    new_forloop = new IterationResource(forloopVar, new ExprVar(ArangoDatabaseSettings.GraphModel.rdfLiteralsCollectionName));
                }
                else{
                    new_forloop = new IterationResource(forloopVar, new ExprVar(ArangoDatabaseSettings.GraphModel.rdfResourcesCollectionName));
                }
                ExprList objectFilterConditions = new ExprList();
                RewritingUtils.ProcessTripleNode(triple.getObject(), forloopVar, objectFilterConditions, usedVars, true);
                if(objectFilterConditions.size() > 0){
                    new_forloop = new com.aql.querytree.operators.OpFilter(objectFilterConditions, new_forloop);
                }

                if(currAqlNode == null)
                    currAqlNode = new_forloop;
                else
                    currAqlNode = new OpNest(currAqlNode, new_forloop);

                startVertex = forloopVar;
                startVertexIsSubject = false;
                graphTraversalDirection = GraphIterationResource.TraversalDirection.INBOUND;
            }
            else if(subject.isVariable() && usedVars.containsKey(subject.getName())) {
                startVertex = usedVars.get(subject.getName()).getFirstVarName();
            }
            else {
                ExprList subjectFilterConditions = new ExprList();
                String forloopVar = forLoopVarGenerator.getNew();
                AqlQueryNode new_forloop = new IterationResource(forloopVar, new ExprVar(ArangoDatabaseSettings.GraphModel.rdfResourcesCollectionName));
                RewritingUtils.ProcessTripleNode(triple.getSubject(), forloopVar, subjectFilterConditions, usedVars, false);
                if(subjectFilterConditions.size() > 0){
                    new_forloop = new com.aql.querytree.operators.OpFilter(subjectFilterConditions, new_forloop);
                }

                if(currAqlNode == null)
                    currAqlNode = new_forloop;
                else
                    currAqlNode = new OpNest(currAqlNode, new_forloop);

                startVertex = forloopVar;
            }

            //keep list of FILTER clauses per triple
            ExprList filterConditions = new ExprList();
            String iterationVertexVar = graphForLoopVertexVarGenerator.getNew();
            String iterationEdgeVar = graphForLoopEdgeVarGenerator.getNew();
            String iterationPathVar = graphForLoopPathVarGenerator.getNew();

            AqlQueryNode aqlNode = new GraphIterationResource(iterationVertexVar, iterationEdgeVar, iterationPathVar, 1, 1, startVertex, graphTraversalDirection, Arrays.asList(ArangoDatabaseSettings.GraphModel.rdfEdgeCollectionName));

            //if there are default graphs specified, filter by those
            //we don't need to check that each triple matched by the BGP is in the same named graph.. since here we're using the default graph so all triples are considered to be in that one graph
            AddDefaultGraphFilters(AqlUtils.buildVar(iterationPathVar, "edges[0]"), filterConditions);

            RewritingUtils.ProcessTripleNode(triple.getPredicate(), AqlUtils.buildVar(iterationPathVar, "edges[0]", ArangoAttributes.PREDICATE), filterConditions, usedVars, false);

            if(startVertexIsSubject) {
                RewritingUtils.ProcessTripleNode(triple.getObject(), AqlUtils.buildVar(iterationPathVar, "vertices[1]"), filterConditions, usedVars, true);
            }
            else{
                RewritingUtils.ProcessTripleNode(triple.getSubject(), AqlUtils.buildVar(iterationPathVar, "vertices[1]"), filterConditions, usedVars, false);
            }

            if(filterConditions.size() > 0)
                aqlNode = new com.aql.querytree.operators.OpFilter(filterConditions, aqlNode);

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
        Node graphNode = opQuadPattern.getGraphNode();
        AqlQueryNode currAqlNode = null;
        Map<String, BoundAqlVars> usedVars = new HashMap<>();
        boolean firstTripleBeingProcessed = true;

        //using this variable, we will make sure the graph name of every triple matching the BGP is in the same graph
        String outerGraphVarToMatch = "";

        for(Triple triple : opQuadPattern.getBasicPattern()){
            ExprList subjectFilterConditions = new ExprList();
            Node subject = triple.getSubject();
            String startVertex;
            //if the subject of the triple isn't bound already - we need an extra for loop
            if(subject.isURI() || (subject.isVariable() && !usedVars.containsKey(subject.getName()))){
                String forloopVar = forLoopVarGenerator.getNew();
                AqlQueryNode new_forloop = new IterationResource(forloopVar, new ExprVar(ArangoDatabaseSettings.GraphModel.rdfResourcesCollectionName));
                RewritingUtils.ProcessTripleNode(triple.getSubject(), forloopVar, subjectFilterConditions, usedVars, false);
                if(subjectFilterConditions.size() > 0){
                    new_forloop = new com.aql.querytree.operators.OpFilter(subjectFilterConditions, new_forloop);
                }

                if(currAqlNode == null)
                    currAqlNode = new_forloop;
                else
                    currAqlNode = new OpNest(currAqlNode, new_forloop);

                startVertex = forloopVar;
            }
            else {
                startVertex = usedVars.get(subject.getName()).getFirstVarName();
            }

            //keep list of FILTER clauses per triple
            ExprList filterConditions = new ExprList();
            String iterationVertexVar = graphForLoopVertexVarGenerator.getNew();
            String iterationEdgeVar = graphForLoopEdgeVarGenerator.getNew();
            String iterationPathVar = graphForLoopPathVarGenerator.getNew();

            AqlQueryNode aqlNode = new GraphIterationResource(iterationVertexVar, iterationEdgeVar, iterationPathVar, 1, 1, startVertex, GraphIterationResource.TraversalDirection.OUTBOUND, Arrays.asList(ArangoDatabaseSettings.GraphModel.rdfEdgeCollectionName));

            //if this is the first for loop and there are named graphs specified, add filters for those named graphs
            if(firstTripleBeingProcessed){
                outerGraphVarToMatch = AqlUtils.buildVar(iterationEdgeVar, ArangoAttributes.GRAPH_NAME, ArangoAttributes.VALUE);

                if(graphNode.isVariable()){
                    AddNamedGraphFilters(AqlUtils.buildVar(iterationPathVar, "edges[0]"), filterConditions);

                    //bind graph var
                    usedVars.put(graphNode.getName(), new BoundAqlVars(AqlUtils.buildVar(iterationEdgeVar, ArangoAttributes.GRAPH_NAME)));
                }
                else{
                    //add filter with specific named graph
                    filterConditions.add(new Expr_Equals(new ExprVar(outerGraphVarToMatch), new Const_String(graphNode.getURI())));
                }
            }
            else{
                //make sure that graph name for consecutive triples matches the one of the first triple
                filterConditions.add(new Expr_Equals(new ExprVar(AqlUtils.buildVar(iterationPathVar, "edges[0]", ArangoAttributes.GRAPH_NAME, ArangoAttributes.VALUE)), new ExprVar(outerGraphVarToMatch)));
            }

            RewritingUtils.ProcessTripleNode(triple.getPredicate(), AqlUtils.buildVar(iterationPathVar, "edges[0]", ArangoAttributes.PREDICATE), filterConditions, usedVars, false);
            RewritingUtils.ProcessTripleNode(triple.getObject(), AqlUtils.buildVar(iterationPathVar, "vertices[1]"), filterConditions, usedVars, true);

            if(filterConditions.size() > 0)
                aqlNode = new com.aql.querytree.operators.OpFilter(filterConditions, aqlNode);

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
}
