@Grapes([
	  @Grab('org.eclipse.jetty:jetty-server:9.0.0.M5'),
	  @Grab('org.eclipse.jetty:jetty-servlet:9.0.0.M5'),
	  @Grab('javax.servlet:javax.servlet-api:3.0.1'),
	  @GrabExclude('org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016'),
	  @GrabConfig(systemClassLoader=true)
	])
 
import groovy.sparql.*
 
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import uk.ac.aber.lus11.sparqowlapi.request.* 
import groovy.json.*
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import javax.servlet.http.*
import javax.servlet.ServletConfig

class DoPhenoServer extends HttpServlet {
  public final static String disfilename = "doid2hpo-fulltext.txt"
  def requestHandler
  def context
  void init(ServletConfig config) {
    super.init(config)
    context = config.servletContext


  }
  void service(HttpServletRequest request, HttpServletResponse response) {
    requestHandler.binding = new ServletBinding(request, response, context)
    use (ServletCategory) {
      requestHandler.call()
    }
  }

  /**
   * Create the ontology manager and load it with the given ontology.
   * 
   * @throws OWLOntologyCreationException 
   */
  void loadOntologies() throws OWLOntologyCreationException, IOException {

    def ontologies = QuerySparql.query("""
PREFIX meta: <http://bioportal.bioontology.org/metadata/def/>

SELECT DISTINCT ?graph ?vid ?acro ?name ?loc ?fn ?lang
WHERE {
    ?vrtID meta:hasVersion ?version .
    ?vrtID meta:id ?vid .
    ?version meta:hasDataGraph ?graph .
    ?id <http://bioportal.bioontology.org/metadata/def/hasDataGraph> ?graph .
    ?id <http://omv.ontoware.org/2005/05/ontology#hasOntologyLanguage> ?lang .
  OPTIONAL {
    ?id <http://omv.ontoware.org/2005/05/ontology#acronym> ?acro .
    }
  OPTIONAL {
    ?id <http://omv.ontoware.org/2005/05/ontology#name> ?name .
    }
}
""")
    ontologies.each { ont ->
      println "Loading ${ont.name}..."
      //      println ont
      IRI iri = IRI.create("http://data.bioontology.org/ontologies/"+ont.acro.toUpperCase()+"/download?apikey=24e0413e-54e0-11e0-9d7b-005056aa3316")
      try {
	def oManager = OWLManager.createOWLOntologyManager();
	OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration() ;
	config.setFollowRedirects(true) ;
	config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT) ;
	OWLOntology ontology = oManager.loadOntologyFromOntologyDocument(new IRIDocumentSource(iri), config);
	
      } catch (Exception E) {
	//	println "${ont.name} loading failed..."
	//	println iri
	println E.getMessage()
      }
      /*
	this.ontologies.put(oListString, ontology);
	this.ontologyManagers.put(oListString, this.oManager) ;
      */
    }
    println ontologies.size()
    /*
    for(String oListString : oList) {
      IRI iri = IRI.create(oListString);
      try {

      } catch (OWLOntologyAlreadyExistsException E) {
	// do nothing
      } catch (Exception E) {
	E.printStackTrace() ;
      }
      
      }*/
  }


  void run(int port) {
    def jetty = new Server(port)
    def context = new ServletContextHandler(jetty, '/', ServletContextHandler.SESSIONS)

    int k = 500 ; // for SuggestTree
    int maxLength = 10000 ; // for SuggestTree
    SuggestTree allLabels = new SuggestTree(k, new HashMap<String, Integer>())
    Map<String, Set<String>> allLabels2id = new HashMap<>() 
    Map<String, SuggestTree> labels = new LinkedHashMap<>() 
    Map<String, Map<String, Set<String>>> labels2id = new LinkedHashMap<>(); // ontUri -> label -> OWLClassIRI
    Map<String, OWLOntology> ontologies = new LinkedHashMap<>()
    List<OWLAnnotationProperty> aProperties = new ArrayList<>()
    Map<String, QueryEngine> queryEngines = new LinkedHashMap<>()
    Map<String, OWLOntologyManager> ontologyManagers = new LinkedHashMap<>()
    OWLDataFactory df = OWLManager.getOWLDataFactory()
    
    loadOntologies()
    context.resourceBase = '.'  
    context.addServlet(GroovyServlet, '/AberOWLServlet.groovy')
    context.setAttribute('version', '1.0')  


    // context.setAttribute("dis2name", dis2name)
    // context.setAttribute("pheno2name", pheno2name)
    // context.setAttribute("dismap", dismap)
    // context.setAttribute("donames", donames)
    // context.setAttribute("name2doid", name2doid)

    jetty.start()
  }

  public static void main(args) {
    DoPhenoServer s = new DoPhenoServer()
    s.run(30003)
  }
}

