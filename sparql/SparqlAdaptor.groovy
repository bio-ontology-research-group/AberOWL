package sparql

@Grab(group='org.apache.jena', module='jena-core', version='3.0.1')
@Grab(group='org.apache.jena', module='jena-arq', version='3.0.1')

import groovyx.gpars.GParsPool;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.sql.ResultSet;
import org.semanticweb.owlapi.io.FileDocumentSource;
import org.apache.jena.rdf.model.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.iterator.*;
import org.apache.jena.util.*;
import org.apache.jena.riot.web.LangTag;

public class SparqlAdaptor {

    /**
     * It represents the url where the virtuoso server is located
     */
    private String virtuosoURL;//URL where is located the virtuoso server.
    /**
     * The user name in the virtuoso server
     */
    private String virtuosoUser;//User name
    /**
     * The password needed to access to the repository
     */
    private String virtuosoPasswd;//Password
    /**
     * The based name that will be used to insert graphs in the virtuoso repository.
     */
    private String graphBasedName="http://aber-owl.net/";

    /**
     * Constructor of the adaptor.
     */
    public SparqlAdaptor(){
        this.initialize()
    }

    /**
     * It initializes the properties needed to connect to the repository.
     */
    private void initialize(){
        try{
            String pathFile = (System.getProperty("user.dir")+File.separator+"resources"+File.separator+"virtuoso_config.properties");
            Properties properties = new Properties();
            properties.load(new FileInputStream(pathFile));
            this.virtuosoURL    = properties.getProperty("url");
            this.virtuosoUser   = properties.getProperty("user");
            this.virtuosoPasswd = properties.getProperty("password");
        }catch(Exception e){
                e.printStackTrace()
                System.out.println("ERROR: It was not possible to access to the property file");
        }
    }
    /**
     * It performs the connection to virtuoso repository.
     * @return Connection to the repository..
     */
    private Connection virtuosoConnection() {
        Connection connection=null;
        try{
            Class.forName("virtuoso.jdbc4.Driver");
            connection = DriverManager.getConnection(virtuosoURL,virtuosoUser,virtuosoPasswd);
        }catch(Exception e){
            e.printStackTrace();
            System.out.println("ERROR: it was not possible to access to the RDF repository");
        }
        return connection;
    }

    /**
     * It escapes SPARQL language. The function has been implemented according to the guidelines provided in W3C
     * standard SPARQL Query Language for RDF.
     * @param literal The literal that will be checked. If this literal contain any reserverd word, it will be then
     * escaped using \\.
     * @return Literal checked..
     */
    public String escapeSparql(String literal) {
        //To avoid problems in sparql.
        if (literal.contains("\\\"")) {
            literal = literal.replace("\\\"", "\"")
        }
        if (literal.contains("\\'")) {
            literal = literal.replace("\\'", "'")
        }
        literal = literal.replace("\\", "\\\\");
        literal = literal.replace("\"", "\\\"");
        literal = literal.replace("'", "\\'");
        literal = literal.replace("\t", "\\t");
        literal = literal.replace("\n", "\\n");
        literal = literal.replace("\r", "\\r");
        literal = literal.replace("\b", "\\b");
        literal = literal.replace("\f", "\\f");
        return (literal)
    }

    /**
     * It inserts graph in virtuoso.
     * @param graphName The name used to insert the graph.
     * @param graphModel The Jena model that contains the ontology, which will be updated in the repository.
     */
    public void insertGraph(String graphName, Model graphModel) {
        try{
            Connection kbConn = virtuosoConnection();
            Statement stmt = kbConn.createStatement();
            try {
                String deleteTriples = "SPARQL CLEAR GRAPH <"+graphBasedName+graphName+">";
                stmt.execute(deleteTriples);
                String dropGraph = "SPARQL DROP GRAPH <"+graphBasedName+graphName+">";
                stmt.execute(dropGraph);
            }catch(Exception e) {
                System.out.println("Warning: the graph it was not deleted because it was not created before");
            }
            String createGraph = "SPARQL CREATE GRAPH <"+graphBasedName+graphName+">";
            stmt.execute(createGraph);
            //String loadGraph = "SPARQL LOAD <file:/../../../.."+graphFile.getAbsolutePath()+"> INTO <http://aber-owl.net/"+name+">";
            // stmt.execute(loadGraph);
            StmtIterator stmIt = graphModel.listStatements();
            org.apache.jena.rdf.model.Statement statement = null;
            String sentence;
            while(stmIt.hasNext()){
                statement = stmIt.nextStatement();
                if(statement.getObject().isLiteral()) {
                    Literal literal =  (Literal)statement.getObject();
                    if(!literal.getLanguage().isEmpty()) {
                        //Canonicalize with the rules of RFC 4646
                        if(((!literal.getLanguage().isEmpty()))&&(LangTag.parse(literal.getLanguage())!=null)) {
                            sentence = "SPARQL INSERT INTO GRAPH <" + graphBasedName + graphName + "> { " +
                                    "<" + statement.getSubject().getURI() + "> " +
                                    "<" + statement.getPredicate().toString() + "> " +
                                    "\"" + escapeSparql(literal.getValue().toString()) + "\"@" + literal.getLanguage() + " }";
                        }else{//if the language has been defined incorrectly, then we insert the literal without language
                            sentence = "SPARQL INSERT INTO GRAPH <" + graphBasedName + graphName + "> { " +
                                    "<" + statement.getSubject().getURI() + "> " +
                                    "<" + statement.getPredicate().toString() + "> " +
                                    "\""+ escapeSparql(literal.getValue().toString())+"\" }";
                        }
                    }
                }else if(statement.getObject().isResource()){
                    Resource resource =  (Resource)statement.getObject();
                    sentence = "SPARQL INSERT INTO GRAPH <"+graphBasedName+graphName+"> { " +
                            "<"+statement.getSubject().getURI()+"> " +
                            "<"+statement.getPredicate().toString()+"> " +
                            "<"+resource.getURI()+"> }";
                }
                stmt.executeUpdate(sentence)
            }
            stmt.close();
            kbConn.close()
            System.out.println("Successfully loadded "+graphName+" in the repository");
        }catch(Exception e) {
            e.printStackTrace()
        }
    }

    /**
     * Given an ontology name, it returns its list of properties (predicates) stored in virtuoso.
     * @param ontologyName The name of the graph from where the properties (predicates) will be queried.
     * @return Map of properties.The format used to store this information is:
     * name: it represents the name of the property
     * uri: it represents the uri of the property
     */
    protected List<HashMap<String,String>> getProperties(String ontologyName){
        ArrayList<HashMap<String,String>> properties = new ArrayList<HashMap<String,String>>()
        try{
            Connection kbConn = virtuosoConnection();
            Statement stmt = kbConn.createStatement();
            sentence = "SPARQL SELECT DISTINCT ?p FROM <"+graphBasedName+ontologyName+"> WHERE { ?s ?p ?o}"
            ResultSet resultSet = stmt.executeQuery(sentence);
            String property;
            while(resultSet.next()){
                property = resultSet.getString(1);
                if(property!=null) {
                    HashMap<String,String> hashProp = new HashMap<String,String>();
                    String name = object.substring(property.lastIndexOf("/")+1,property.length())
                    name = name.replace("#","");
                    hashProp.put("name",name);
                    hashProp.put("uri",property);
                    properties.add(name,property);
                }
            }
            stmt.close();
            kbConn.close();
        }catch(Exception e){
            System.out.println("ERROR: It was not possible to access to the RDF repository.")
        }

        return(children);
    }
    /**
     * Given an ontology name, literal's label and list of propertiesl, it returns all resources related according
     * to the properties given.
     * @param graphName The name of the graphs.
     * @param label The label of the literal
     * @param properties The list of properties
     * @return List of labels, which are related to the given label.
     */
    protected List<String> querySparql(String graphName, String label, List<String> properties) {
        HashMap<String,HashMap<String,String>> children = new HashMap<String,HashMap<String,String>>();
        try{
            Connection kbConn = virtuosoConnection();
            Statement stmt = kbConn.createStatement();
            Iterator<String> itProperties=properties.iterator();
            String sentence;
            String property;
            while(itProperties.hasNext()){
                property = itProperties.next();
                sentence = "SPARQL SELECT ?s FROM <"+graphBasedName+graphName+"> WHERE { ?s <"+property+"> <"+escapeSparql(label)+">}"
                ResultSet resultSet = stmt.executeQuery(sentence);
                String object;
                while(resultSet.next()){
                    object = resultSet.getString(1);
                    if(object!=null) {
                        children.add(object)
                    }
                }
            }
            stmt.close();
            kbConn.close();
        }catch(Exception e){
            System.out.println("ERROR: It was not possible to access to the RDF repository.")
        }

        return(children);
    }

    /**
     * It converts an ontology into RDF graph
     * @param name The ontology's name that is used to store it in the repository.
     * @param timestamp The timestamp of this ontology to differentiate it to others versions.
     * @param owlFile The ontology file.
     */
    public void convertOntology(String name, String timestamp, FileDocumentSource owlFile){
        try {
            println("Converting ontology:"+name+" into RDF graph...")
            String[] reasoners=['ELK'];
            String[] formats=['RDFXML']
            boolean eqClasses = true;
            String path = System.getProperty("user.dir");
            String appPath = path+File.separator+"sparql"+File.separator+"Onto2Graph.jar"
            String graphsPath = path+File.separator+"graphs"+File.separator;
            String graphLocation = graphsPath+name + "_" + timestamp;
            File graphFile = new File(graphLocation+".rdfxml");

            if (!graphFile.exists()) {
                GParsPool.withPool {
                    reasoners.eachParallel { String reasoner ->
                        formats.each { String format ->
                            String command = "java -jar " + appPath +
                                    " -ont " + path + "/onts/" + owlFile.getDocumentIRI().getShortForm() +
                                    " -out " + graphLocation +
                                    " -r " + reasoner +
                                    " -f " + format +
                                    " -eq " + eqClasses.toString() +
                                    " -op [*]" +
                                    " -nt 4";

                            def process = command.execute();
                            def outputStream = new StringBuffer();
                            process.consumeProcessErrorStream(outputStream);
                            println process.text
                            println outputStream.toString()

                            Model graphModel = ModelFactory.createDefaultModel();
                            InputStream input = FileManager.get().open(graphFile.getAbsolutePath())
                            graphModel.read(input,"")
                            insertGraph(name,graphModel);
                        }
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

}
