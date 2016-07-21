package sparql

@Grab(group='org.apache.jena', module='jena-core', version='2.13.0')
@Grab(group='org.apache.jena', module='jena-arq', version='2.13.0')
@GrabResolver(name='maven.aksw', root='http://maven.aksw.org/repository/internal/')
@Grab(group='com.openlink.virtuoso', module='virt_jena2', version='7.1.0')
@Grab(group='com.openlink.virtuoso', module='virtjdbc4', version='7.1.0')

import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.util.FileManager
import com.hp.hpl.jena.rdf.model.Statement;
import groovyx.gpars.GParsPool;
import org.apache.jena.riot.web.LangTag;
import virtuoso.jena.driver.VirtGraph
import virtuoso.jena.driver.VirtuosoQueryExecution
import virtuoso.jena.driver.VirtuosoQueryExecutionFactory;
import virtuoso.jena.driver.VirtuosoUpdateFactory;
import virtuoso.jena.driver.VirtuosoUpdateRequest;
import virtuoso.jdbc4.VirtuosoConnectionPoolDataSource;
import org.semanticweb.owlapi.io.FileDocumentSource
import java.nio.charset.Charset
import java.text.Normalizer

public class SparqlAdaptor {

    private VirtuosoConnectionPoolDataSource virtuosoPool=null;
    private int nMaxPoolSize = 8;

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
     * Environment path.
     */
    private String envPath=System.getProperty("user.dir")+File.separator+"sparql"+File.separator;
    /**
     * Graphs directory
     */
    private String graphsPath= System.getProperty("user.dir")+File.separator+"graphs"+File.separator;


    /**
     * Constructor of the adaptor.
     */
    public SparqlAdaptor(){
        this.initialize()
    }

    /**
     * It initializes the properties needed to connect to the repository.
     */
    public void initialize(){
        try{
            Properties properties = new Properties();
            properties.load(new FileInputStream(envPath+"virtuoso_config.properties"));
            String virtuosoUser   = properties.getProperty("user");
            String virtuosoPasswd = properties.getProperty("password");
            String virtuosoPort = properties.getProperty("port");
            String virtuosoServer = properties.getProperty("server");

            virtuosoPool = new VirtuosoConnectionPoolDataSource();
            virtuosoPool.setUser(virtuosoUser);
            virtuosoPool.setPassword(virtuosoPasswd);
            virtuosoPool.setPortNumber(Integer.parseInt(virtuosoPort));
            virtuosoPool.setServerName(virtuosoServer);
            virtuosoPool.setMaxPoolSize(nMaxPoolSize);

        }catch(Exception e){
            e.printStackTrace();
            System.out.println("ERROR: it was not possible to access to the RDF repository");
        }
    }


    /**
     * It escapes SPARQL language. The function has been implemented according to the guidelines provided in W3C
     * standard SPARQL Query Language for RDF.
     * @param literal The literal that will be checked. If this literal contain any reserverd word, it will be then
     * escaped using \\.
     * @return Literal checked..
     */
    public String escapeSparql(String literal){
        //To avoid problems in sparql.

        if (literal.contains("\\\"")) {
            literal = literal.replace("\\\"", "\"");
        }
        if (literal.contains("\\'")) {
            literal = literal.replace("\\'", "'");
        }
        literal = literal.replace("\\", "\\\\");
        literal = literal.replace("\"", "\\\"");
        literal = literal.replace("'", "\\'");
        literal = literal.replace("\t", "\\t");
        literal = literal.replace("\n", "\\n");
        literal = literal.replace("\r", "\\r");
        literal = literal.replace("\b", "\\b");
        literal = literal.replace("\f", "\\f");

        //We normalize the text to delete weirds characters.
        literal = Normalizer.normalize(literal, Normalizer.Form.NFD).replaceAll("[^\\p{ASCII}]","");

        return (literal);
    }

    /**
     * It inserts graph in virtuoso.
     * @param graphName The name used to insert the graph.
     * @param graphModel The Jena model that contains the ontology, which will be updated in the repository.
     */
    public void insertGraph(String graphName, Model graphModel){
        try{
            VirtGraph virtGraph = new VirtGraph(graphBasedName+graphName,virtuosoPool);
            java.sql.Statement stmt = virtGraph.createStatement();

            try {
                String deleteTriples = "SPARQL CLEAR GRAPH <"+graphBasedName+graphName+">";
                stmt.executeUpdate(deleteTriples);
                String dropGraph = "SPARQL DROP SILENT GRAPH <"+graphBasedName+graphName+">";
                stmt.executeUpdate(dropGraph)
            }catch(Exception e) {
                System.out.println("Warning: the graph it was not deleted because it was not created before");
            }
            String createGraph = "SPARQL CREATE SILENT GRAPH <"+graphBasedName+graphName+">";
            stmt.executeUpdate(createGraph);
            //String loadGraph = "SPARQL LOAD <file:/../../../.."+graphFile.getAbsolutePath()+"> INTO <http://aber-owl.net/"+name+">";
            StmtIterator stmIt = graphModel.listStatements();
            Statement statement;
            while(stmIt.hasNext()){
                String sentence=null;
                Literal literal=null;
                statement = stmIt.nextStatement();
                if(statement.getObject().isLiteral()) {
                    literal =  (Literal)statement.getObject();
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

                if (sentence != null) {
                    stmt.executeUpdate(sentence);
                }
            }

            stmt.close();
            stmt = null;
            System.out.println("Successfully loadded "+graphName+" in the repository");
        }catch(Exception e) {
            System.out.println("Graph not loaded: " + graphName + " exception: " + e.getMessage());
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
            VirtGraph virtGraph = new VirtGraph(graphBasedName+ontologyName,virtuosoPool);
            java.sql.Statement stmt = virtGraph.createStatement();

            String sentence = "SELECT DISTINCT ?p FROM <"+graphBasedName+ontologyName+"> WHERE { ?s ?p ?o}"
            java.sql.ResultSet resultSet = stmt.executeQuery(sentence)
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
            stmt = null;
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
    public List<HashMap> querySparql(String graphName, String label, String property) {
        ArrayList<HashMap> result = new ArrayList<HashMap>();
        try{
            graphName+="_ELK";//temporarily
            System.out.println(graphName);
            VirtGraph virtGraph = new VirtGraph(graphBasedName+graphName,virtuosoPool);
            java.sql.Statement stmt = virtGraph.createStatement();
            Iterator<String> itProperties=properties.iterator();
            String sentence;
            sentence = "SPARQL SELECT DISTINCT ?class ?label FROM <"+graphBasedName+graphName+"> WHERE " +
                    "{ ?subject <http://www.w3.org/2000/01/rdf-schema#label> \""+label+"\"@en. " +
                    "?class "+property+" ?subject. " +
                    "?class <http://www.w3.org/2000/01/rdf-schema#label> ?object. " +
                    "FILTER (langMatches(lang(?object),\"en\")). " +
                    "BIND (str(?object) as ?label) }";
            java.sql.ResultSet resultSet = stmt.executeQuery(sentence);
            String clazz;
            String rLabel;
            while(resultSet.next()){
                clazz = resultSet.getString(1);
                rLabel = resultSet.getString(2);
                if((clazz!=null)&&(rLabel!=null)) {
                    def info = [
                            "owlClass": clazz,
                            "label"   : rLabel
                    ];
                    result.add(info);
                }
            }
            stmt.close();
            stmt = null;
        }catch(Exception e){
            System.out.println("ERROR: It was not possible to access to the RDF repository.")
        }

        return(result);
    }

    /**
     * It checks if a given name of the graph is contained in the repository.
     * @param graphName Graph's name to check.
     * @return boolean. True if the graphs is already conteined in the repository. False in other case.
     */
    private boolean existGraph(String graphName){
        try{
            VirtGraph virtGraph = new VirtGraph(graphBasedName+graphName,virtuosoPool);
            java.sql.Statement stmt = virtGraph.createStatement();
            String sentence = "SPARQL ASK { GRAPH <"+graphBasedName+graphName+"> { ?s ?p ?o} }"
            java.sql.ResultSet resultSet = stmt.executeQuery(sentence);
            if(resultSet.next()){
                return(true);
            }
            stmt.close();
            stmt=null;
        }catch(Exception e){
            System.out.println("ERROR: It was not possible to check if the graph is stored in RDF repository.")
        }
        return(false);
    }

    /**
     * It converts an ontology into RDF graph
     * @param name The ontology's name that is used to store it in the repository.
     * @param timestamp The timestamp of this ontology to differentiate it to others versions.
     * @param owlFile The ontology file.
     */
    public void convertOntology(FileDocumentSource owlFile, int objectPropertiesCounter){
        try {
            String name = owlFile.getDocumentIRI().getShortForm()
            int pos = name.lastIndexOf(".");
            if(pos>=0){
                name = name.substring(0,pos);
            }
            //println("Converting ontology:"+name+" into RDF graph...")
            String[] reasoners=['ELK'];
            String[] formats=['RDFXML']
            boolean eqClasses = true;
            String path = System.getProperty("user.dir");
            String appPath = envPath+"Onto2Graph.jar"
            File graphsDir = new File(graphsPath)
            if(!graphsDir.exists()){
                graphsDir.mkdir();
            }
            String graphLocation = graphsPath+name;
            File graphFile = new File(graphLocation+".rdfxml");

            if (!graphFile.exists()) {
                GParsPool.withPool {
                    reasoners.eachParallel { String reasoner ->
                        formats.each { String format ->
                            //size
                            File file = new File(owlFile.getDocumentIRI().toURI());
                            int fileSize = file.length()/(Math.pow(1024,2)); //size in Megabytes
                            int maxNThreads = objectPropertiesCounter*8; // 8 threads for each property.
                            int maxJavaHeap = fileSize * maxNThreads;
                            if(maxNThreads>32){
                                maxNThreads = 32; // We restrict the numbers of the threads of the application.
                            }
                            if(maxJavaHeap>16){
                                maxJavaHeap = 16; //We restrict the java heap to 16gb.
                            }
                            String command = "java -Xmx"+maxJavaHeap+"g -d64 -jar " + appPath +
                                    " -ont " + path + "/onts/" + owlFile.getDocumentIRI().getShortForm() +
                                    " -out " + graphLocation +
                                    " -r " + reasoner +
                                    " -f " + format +
                                    " -eq " + eqClasses.toString() +
                                    " -op [*]" +
                                    " -nt "+maxNThreads;
                            System.out.println(command)
                            long start = Calendar.getInstance().getTimeInMillis()
                            def process = command.execute();
                            def outputStream = new StringBuffer();
                            process.consumeProcessErrorStream(outputStream);
                            println process.text
                            println outputStream.toString()
                            System.out.println((Calendar.getInstance().getTimeInMillis()-start)/1000);
                        }
                    }
                }
            }
            if(!existGraph(name)){
                Model graphModel = ModelFactory.createDefaultModel();
                InputStream input = FileManager.get().open(graphFile.getAbsolutePath())
                graphModel.read(input,"")
                insertGraph(name,graphModel);
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

}
