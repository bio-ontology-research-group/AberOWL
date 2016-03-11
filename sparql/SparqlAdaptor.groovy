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
import org.apache.jena.util.iterator.*;
import org.apache.jena.util.*;

public class SparqlAdaptor {

    private String virtuosoURL;//URL where is located the virtuoso server.
    private String virtuosoUser;//User name
    private String virtuosoPasswd;//Password
    private String graphBasedName="http://aber-owl.net/";

    public SparqlAdaptor(){
        this.initialize()
    }


    public void initialize(){
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

    public void insertGraph(String graphName, Model graphModel) {
        try{
            Connection kbConn = virtuosoConnection();
            Statement stmt = kbConn.createStatement();
            try {
                String deleteTriples = "SPARQL CLEAR GRAPH <"+graphBasedName+graphName+">"
                stmt.execute(deleteTriples);
                String dropGraph = "SPARQL DROP GRAPH <"+graphBasedName+graphName+">";
                stmt.execute(dropGraph);
            }catch(Exception e) {
                System.out.println("Warning: the graph it was not deleted because it was not created before")
            }
            String createGraph = "SPARQL CREATE GRAPH <"+graphBasedName+graphName+">";
            stmt.execute(createGraph);
            //String loadGraph = "SPARQL LOAD <file:/../../../.."+graphFile.getAbsolutePath()+"> INTO <http://aber-owl.net/"+name+">";
            //stmt.execute(loadGraph);
            StmtIterator stmIt = graphModel.listStatements();
            String sentence;
            org.apache.jena.rdf.model.Statement statement = null;
            while(stmIt.hasNext()){
                statement = stmIt.nextStatement();
                if(statement.getObject().isLiteral()) {
                    sentence = "SPARQL INSERT INTO GRAPH <"+graphBasedName+graphName+"> { <"+statement.getSubject().getURI()+"> <"+statement.getPredicate().toString()+"> " + "<"+statement.getObject().asLiteral().getString()+"> }";
                }else if(statement.getObject().isResource()){
                    sentence = "SPARQL INSERT INTO GRAPH <"+graphBasedName+graphName+"> { <"+statement.getSubject().getURI()+"> <"+statement.getPredicate().toString()+"> "+ "<"+statement.getObject().asResource().getURI()+"> }";
                }
                stmt.execute(sentence)
            }
            stmt.close();
            kbConn.close()
            System.out.println("Successfully loadded "+graphName+" in the repository");
        }catch(Exception e) {
            e.printStackTrace()
            System.out.println("Error:It was not possible to access to the RDF repository.");
        }

    }

    protected List<String> querySparql(String graphName, String subjectConcept, List<String> properties) {
        ArrayList<String> children = new ArrayList<String>();
        try{
            Connection kbConn = virtuosoConnection();
            Statement stmt = kbConn.createStatement();
            Iterator<String> itProperties=properties.iterator();
            String sentence;
            String property;
            while(itProperties.hasNext()){
                property = itProperties.next();
                sentence = "SPARQL SELECT ?o FROM <"+graphBasedName+graphName+"> WHERE { <"+subjectConcept+"> <"+property+"> ?o}"
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

    public void convertOntology(String name, String timestamp, FileDocumentSource owlFile){
        try {
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
                                    " -op [*]";

                            def process = command.execute();
                            System.out.println(process.text);
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
