package com.sparql_to_aql;

import com.aql.querytree.AqlQueryNode;
import com.aql.querytree.AqlQuerySerializer;
import com.aql.querytree.AqlQueryTreeWriter;
import com.arangodb.ArangoCursor;
import com.arangodb.entity.BaseDocument;
import com.sparql_to_aql.constants.ArangoDataModel;
import com.sparql_to_aql.constants.Configuration;
import com.sparql_to_aql.database.ArangoDbClient;
import com.sparql_to_aql.entities.algebra.transformers.*;
import com.sparql_to_aql.utils.MathUtils;
import com.sparql_to_aql.utils.RewritingUtils;
import com.sparql_to_aql.utils.SparqlUtils;
import org.apache.commons.cli.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.jena.query.*;
import org.apache.jena.sparql.algebra.*;
import org.apache.jena.sparql.algebra.optimize.TransformReorder;
import virtuoso.jena.driver.VirtGraph;
import virtuoso.jena.driver.VirtuosoQueryExecution;
import virtuoso.jena.driver.VirtuosoQueryExecutionFactory;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Main {

    private static final int queryRuns = 15;
    private static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyyMMddHHmm");

    public static void main(String[] args) {
        // create the parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        //accept more than one file path so we can translate, run, and measure the running time of multiple queries with one call
        options.addOption(Option.builder("f").longOpt("files").hasArgs().desc("Paths to one or more files containing a SPARQL query").argName("file").required().build());
        options.addOption(Option.builder("m").longOpt("data_model").hasArg().desc("ArangoDB data model being queried; Value must be either 'D' if the document model transformation was used, or 'G' if the graph model transformation was used").argName("data model").required().build());
        options.addOption(Option.builder("runOnArangoDb").longOpt("runOnArangoDb").desc("If parameter is given, the generated AQL queries will be run against ArangoDB database").argName("run on ArangoDb").build());
        options.addOption(Option.builder("runOnVirtuoso").longOpt("runOnVirtuoso").desc("If parameter is given, the SPARQL queries will be run against Virtuoso database").argName("run on Virtuoso").build());

        //initialise ARQ before making any calls to Jena, otherwise running jar file throws exception
        ARQ.init();

        try {
            // parse the command line arguments
            CommandLine line = parser.parse(options, args);
            boolean runOnArangoDB = line.hasOption("runOnArangoDb");
            boolean runOnVirtuoso = line.hasOption("runOnVirtuoso");

            ArangoDataModel data_model = ArangoDataModel.valueOf(line.getOptionValue("m"));
            String[] filePaths = line.getOptionValues("f");

            String directoryName = "runtime_results/" + data_model.toString();
            new File(directoryName).mkdirs();

            String virtuosoRuntimesDirectoryName = "runtime_results/V";
            new File(virtuosoRuntimesDirectoryName).mkdirs();

            String resultDataDirectoryName = "query_results/" + data_model.toString();
            new File(resultDataDirectoryName).mkdirs();

            String transformedQueryDirectoryName = "transformed_queries/" + data_model.toString();
            new File(transformedQueryDirectoryName).mkdirs();

            String formattedDate = DATE_FORMAT.format(new Date());
            //add results of multiple queries to the same results file (one row for each query, make first column the query file name)
            FileWriter csvWriter = new FileWriter(directoryName + "/" + formattedDate + ".csv");
            csvWriter.append("Query" + ",");
            csvWriter.append(data_model.name() + "\r\n");
            FileWriter csvWriterVirtuoso = new FileWriter(virtuosoRuntimesDirectoryName + "/" + formattedDate + "_virtuoso" + ".csv");

            List<File> files = GetFilesFromArgumentPaths(filePaths);

            for(File f: files) {
                String filePath = f.getPath();
                //catch exceptions per file, so if one file has an issue we skip it and go to the next
                try {
                    String fileName = FilenameUtils.removeExtension(f.getName());

                    System.out.println("Processing SPARQL query from file " + filePath);
                    String sparqlQuery = new String(Files.readAllBytes(Paths.get(filePath)));

                    //below QueryFactory.create call is very slow unless it's warmed up! make sure to think about this when measuring performance time
                    Query query = QueryFactory.create(sparqlQuery);

                    String aqlQuery = SparqlQueryToAqlQuery(query, data_model);
                    //System.out.println(aqlQuery);
                    SaveAqlQueryToFile(transformedQueryDirectoryName, f.getName(), aqlQuery);

                    if(runOnArangoDB) {
                        ArangoDbClient arangoDbClient = new ArangoDbClient();
                        String resultDataFileName = resultDataDirectoryName + "/" + fileName + "_" + formattedDate + ".csv";
                        System.out.println("Executing generated AQL query on ArangoDb...");
                        ExecuteAqlQuery(csvWriter, fileName, arangoDbClient, aqlQuery, resultDataFileName);

                        if(runOnVirtuoso){
                            VirtGraph db = new VirtGraph(Configuration.GetVirtuosoGraphName(), Configuration.GetVirtuosoUrl(), Configuration.GetVirtuosoUser(), Configuration.GetVirtuosoPassword());
                            System.out.println("Executing SPARQL query on Virtuoso database...");
                            String resultDataFileNameVirtuoso = resultDataDirectoryName + "/" + fileName + "_" + formattedDate + "_virtuoso" + ".csv";
                            VirtuosoQueryExecution vqe = VirtuosoQueryExecutionFactory.create(query, db);
                            ExecuteSparqlQueryOnVirtuoso(csvWriterVirtuoso, fileName, vqe, resultDataFileNameVirtuoso);
                        }
                    }
                }
                catch (QueryException qe) {
                    System.out.println("Invalid SPARQL query: " + qe);
                }
                catch (IOException e) {
                    System.out.println(e);
                }
                catch(UnsupportedOperationException e){
                    System.out.println(e);
                }
            }

            csvWriter.close();
        }
        catch(IOException e){
            System.out.println("Error reading/writing file.");
        }
        catch(ParseException exp) {
            System.err.println("Parsing failed. Reason: " + exp.getMessage());
        }

        System.exit(0);
    }

    private static String SparqlQueryToAqlQuery(Query query, ArangoDataModel dataModel) throws IOException {
        //System.out.println("Generating algebra");
        Op op = Algebra.compile(query);

        //System.out.println("Writing algebra");
        //SSE.write(op);

        //below is used to get query back to SPARQL query form - create something similar for changing AQL query expression tree into string form
        //Query queryForm = OpAsQuery.asQuery(op);
        //System.out.println(queryForm.serialize());

        //System.out.println("initial validation and optimization of algebra");
        //call any optimization transformers on the algebra tree

        //transformer to use if we're gonna remove REDUCED from a query and just do a normal project
        op = Transformer.transform(new OpReducedTransformer(), op);

        //transformer to reorder the position of triple patterns in BGPs/Quads to improve runtime
        op = Transformer.transform(new TransformReorder(), op);

        //op = Transformer.transform(new TransformFilterPlacement(), op);
        //op = Transformer.transform(new OpSequenceTransformer(), op);

        //transformer to merge project and distinct operators into one
        op = Transformer.transform(new OpDistinctTransformer(), op);

        //transformer to use to combine graph and its nested bgp into one operator
        op = Transformer.transform(new OpGraphTransformer(), op);

        //transformer to use to nest slice op in project op instead of vice versa - IMPORTANT this must be applied before OpDistinctTransformer
        op = Transformer.transform(new OpProjectOverSliceTransformer(), op);


        //If the left side of the left join is the empty graph pattern, we can simply drop the left join and keep the results of the right side
        //op = Transformer.transform(new LeftJoinOverIdentityPatternTransformer(), op);

        //consider also the below existing transformers (refer to https://jena.apache.org/documentation/javadoc/arq/org/apache/jena/sparql/algebra/optimize/package-summary.html for more)
        //TransformPattern2Join, TransformExtendCombine, TransformFilterEquality, TransformFilterInequality, TransformRemoveAssignment, TransformImplicitLeftJoin, TransformMergeBGPs
        //SSE.write(op);

        //get FROM and FROM NAMED uris
        List<String> namedGraphs = query.getNamedGraphURIs(); //get all FROM NAMED uris
        List<String> defaultGraphUris = query.getGraphURIs(); //get all FROM uris (forming default graph)

        ArqToAqlTreeVisitor queryExpressionTranslator;

        switch (dataModel){
            case D:
                queryExpressionTranslator = new ArqToAqlTreeVisitor_BasicApproach(defaultGraphUris, namedGraphs);
                break;
            case G:
                queryExpressionTranslator = new ArqToAqlTreeVisitor_GraphApproach(defaultGraphUris, namedGraphs);
                break;
            default: throw new RuntimeException("Unsupported ArangoDB data model");
        }

        OpWalker.walk(op, queryExpressionTranslator);

        AqlQueryNode aqlQueryExpression = queryExpressionTranslator.GetAqlQueryTree();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        AqlQueryTreeWriter aqlQueryTreeWriter = new AqlQueryTreeWriter(outputStream);
        aqlQueryExpression.visit(aqlQueryTreeWriter);
        aqlQueryTreeWriter.finishVisit();

        outputStream.close();

        //Use AQL query serializer to get actual AQL query
        StringWriter out = new StringWriter();
        AqlQuerySerializer aqlQuerySerializer = new AqlQuerySerializer(out);
        aqlQueryExpression.visit(aqlQuerySerializer);
        aqlQuerySerializer.finishVisit();

        return out.toString();
    }

    private static List<File> GetFilesFromArgumentPaths(String[] filePaths){
        List<File> files = new ArrayList<>();

        for (String filePath: filePaths) {
            File file = new File(filePath);
            if (file.isDirectory()) {
                //get list of all files in directory and we'll process them all
                for (File fObj : file.listFiles()) {
                    if (fObj.isFile())
                        files.add(fObj);
                }
            } else {
                files.add(file);
            }
        }

        return files;
    }

    private static void ExecuteAqlQuery(FileWriter csvWriter, String fileName, ArangoDbClient arangoDbClient, String aqlQuery, String resultDataFileName) throws IOException {
        List<Double> timeMeasurements = new ArrayList<>();
        ArangoCursor<BaseDocument> results = null;

        csvWriter.append(fileName + ",");
        for (int i = 0; i < queryRuns; i++) {
            Instant start = Instant.now();
            results = arangoDbClient.execQuery(Configuration.GetArangoDatabaseName(), aqlQuery);
            Instant finish = Instant.now();
            //timeMeasurements[i] = TimeUnit.NANOSECONDS.toMicros(Duration.between(start, finish).toNanos());
            timeMeasurements.add(Duration.between(start, finish).toNanos() / 1E6);
        }

        //remove the two largest and the two smallest run times, then compute average
        double avgRuntime = MathUtils.calculateAverageDouble(timeMeasurements, 2);
        DecimalFormat df = new DecimalFormat("#.##");
        //new DecimalFormat("##.00")
        csvWriter.append(df.format(avgRuntime));
        csvWriter.append("\r\n");
        csvWriter.flush();

        RewritingUtils.printAqlQueryResultsToFile(results, resultDataFileName);
    }

    private static void ExecuteSparqlQueryOnVirtuoso(FileWriter csvWriter, String fileName, VirtuosoQueryExecution vqe, String resultDataFileName) throws IOException {
        List<Double> timeMeasurements = new ArrayList<>();
        ResultSet results = null;

        csvWriter.append(fileName + ",");
        for (int i = 0; i < queryRuns; i++) {
            Instant start = Instant.now();
            results = vqe.execSelect();

            Instant finish = Instant.now();
            timeMeasurements.add(Duration.between(start, finish).toNanos() / 1E6);
        }

        //remove the two largest and the two smallest run times, then compute average
        double avgRuntime = MathUtils.calculateAverageDouble(timeMeasurements, 2);
        DecimalFormat df = new DecimalFormat("#.##");
        csvWriter.append(df.format(avgRuntime));
        csvWriter.append("\r\n");
        csvWriter.flush();

        //print data results to file
        SparqlUtils.printSparqlQueryResultsToFile(results, resultDataFileName);
    }

    private static void SaveAqlQueryToFile(String directoryName, String fileName, String aqlQuery) throws IOException{
        FileWriter queryWriter = new FileWriter(directoryName + "/" + fileName);

        queryWriter.append(aqlQuery);
        queryWriter.flush();
        queryWriter.close();
    }
}
