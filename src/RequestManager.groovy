package src

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.elk.owlapi.ElkReasonerConfiguration
import org.semanticweb.elk.reasoner.config.*
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.reasoner.structural.StructuralReasoner
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import org.semanticweb.owlapi.owllink.*;
import org.semanticweb.owlapi.util.*;
import org.semanticweb.owlapi.search.*;
import org.semanticweb.owlapi.manchestersyntax.renderer.*;
import org.semanticweb.owlapi.reasoner.structural.*

import java.util.concurrent.*
import java.util.concurrent.atomic.*
import java.util.timer.*
import db.*;

import groovy.json.*
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.ContentType
import com.google.common.collect.*

class RequestManager {
  private static final WEB_ROOT = 'http://aber-owl.net/'
  private static final ELK_THREADS = "64"
  private static final MAX_UNSATISFIABLE_CLASSES = 500

  private static final MAX_QUERY_RESULTS = 5000
  private static final MAX_REASONER_RESULTS = 100000

  private static final URL = 'http://127.0.0.1:9200/' // Elastic endpoint

  OWLOntologyManager oManager
  List<OWLAnnotationProperty> aProperties = new ArrayList<>();
  OWLDataFactory df = OWLManager.getOWLDataFactory();
  OntologyDatabase oBase = new OntologyDatabase()


  def ontology = null
  def ontUri = null
  def queryEngine = null
  def loadStati = [:]

  def static search(def type, def map) {

    def http = new HTTPBuilder(URL)
    def j = new groovy.json.JsonBuilder(map)
    try {
      def t 
      http.post( path: '/aberowl/'+type+'/_search', body: j.toPrettyString() ) { resp, reader ->
	t = reader
      }
      http.shutdown()
      return t
    } catch (Exception E) {
      E.printStackTrace()
      println j.toPrettyString()
    }
  }

  RequestManager(String ont) {

    this.ontUri = ont
    loadOntology()
    loadAnnotations()
    createReasoner()
    println "Finished loading $ontUri"
  }

  // Set<String> listOntologies() {
  //   return ontologies.keySet()
  // }

  List<String> queryNames(String query) {
    String[] fields = ['label', 'ontology', 'oboid', 'definition', 'synonym', 'AberOWL-catch-all', 'AberOWL-subclass', 'AberOWL-equivalent']
    def oQuery = query
    Map boostVals = ['label'             : 100,
                     'ontology'          : 1000, // when ontology is added to query, sort by ontology
                     'oboid'             : 10000, // definitely want the matching id returned first when searching for ID
                     'definition'        : 3,
                     'synonym'           : 75,
                     'AberOWL-subclass'  : 25, // less than synonym/label, but more than definition
                     'AberOWL-equivalent': 25, // less than synonym/label, but more than definition
                     'AberOWL-catch-all' : 0.01
		    ]


    //    def parser = new classic.MultiFieldQueryParser(fields, new WhitespaceAnalyzer(), boostVals)
    def queryList = []

    
    oQuery.split().each {
      def omap = [:]
      omap = [:]
      omap.dis_max = [:]
      omap.dis_max.queries = []
      fields.each { f ->
	def q = [ "match" : [ "${f}" : ["query" : "${it}", "boost" : boostVals[f]]]]
	omap.dis_max.queries << q
      }
      queryList << omap
    }
    
    if (ontUri && ontUri != '') {
      queryList << ["match" : ["ontology":"${ontUri}"]]
    }

    def fQuery = ["query": ["bool":["must":[]]]]
    fQuery.from = 0
    fQuery.size = MAX_QUERY_RESULTS
    queryList.each { 
      fQuery.query.bool.must << it
    }

    def hits = search("owlclass", fQuery)
    def ret = []
    hits.hits.hits.each { 
      def temp = it._source
      temp.value = temp.first_label
      temp.data = temp["class"]
      ret << temp
    }
    return ret
  }

  /**
   * Load a new or replace an existing ontology
   *
   * @param name corresponding to name of the ontology in the database
   */
  void reloadOntology() {
    def name = this.ontUri
    def oRec = oBase.getOntology(name, false)
    println 'got record ' + oRec.id
    if (!oRec) {
      println 'no oRec'
      return;
    }
    println oRec.lastSubDate
    if (oRec.lastSubDate == 0) {
      println 'lastSubDate 0'
      return;
    }
    boolean newO = false
    try {
      OWLOntologyManager lManager = OWLManager.createOWLOntologyManager()
      OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
      config.setFollowRedirects(true)
      config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)
      println 'trying to update current version of ontology'
      def fSource = new FileDocumentSource(new File('../ontologies/' + name + '/live/' + name + '.owl'))
      def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config)
      println "Updated ontology: " + oRec.id
      
      this.ontology = ontology
      this.ontologyManager = lManager

      reloadOntologyAnnotations(oRec.id)

      List<String> langs = new ArrayList<>();
      Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
      for (OWLAnnotationProperty annotationProperty : this.aProperties) {
        preferredLanguageMap.put(annotationProperty, langs);
      }
      // May be replaced with any reasoner using the standard interface
      OWLReasonerFactory reasonerFactory = new ElkReasonerFactory()
      createOntologyReasoner(reasonerFactory, preferredLanguageMap)
    } catch (OWLOntologyInputSourceException e) {
      println "input source exception for " + oRec.id + ": " + e.getMessage()
    } catch (IOException e) {
      println "IOException exception for " + oRec.id
    } catch (Exception e) {
      e.printStackTrace()
    }
  }

  /**
   * Create the ontology manager and load it with the given ontology.
   * Create the ontology manager and load it with the given ontology.
   *
   * @throws OWLOntologyCreationException , IOException
   * @throws OWLOntologyCreationException , IOException
   */
  void loadOntology() throws OWLOntologyCreationException, IOException {
    def oRec = oBase.getOntology(ontUri, false)
    try {
      if (oRec.lastSubDate == 0) {
	return;
      }
      println "Loading " + oRec.id
      OWLOntologyManager lManager = OWLManager.createOWLOntologyManager()
      OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
      config.setFollowRedirects(true)
      config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)
      def fSource = new FileDocumentSource(new File('../ontologies/' + oRec.id + '/live/' + oRec.id + '.owl'))
      def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config);
      this.ontology = ontology
      this.oManager = lManager
      println "Successfully loaded " + oRec.id
      loadStati['status'] = 'loaded'
    } catch (OWLOntologyAlreadyExistsException E) {
      if (oRec && oRec.id) {
	println 'DUPLICATE ' + oRec.id
      }
    } catch (OWLOntologyInputSourceException e) {
      if (oRec && oRec.id) {
	loadStati['status'] = 'unloadable'
	loadStati['message'] = e.getMessage()
      }
    } catch (IOException e) {
      println "Can't load external import for " + oRec.id
      if (oRec && oRec.id) {
	loadStati['status'] = 'unloadable'
	loadStati['message'] = e.getMessage()
      }
    } catch (OWLOntologyCreationIOException e) {
      println "Failed to load imports for " + oRec.id
      if (oRec && oRec.id) {
	loadStati['status'] = 'unloadable'
	loadStati['message'] = e.getMessage()
      }
    } catch (UnparsableOntologyException e) {
      println "Failed to parse ontology " + oRec.id
      if (oRec && oRec.id) {
	loadStati['status'] = 'unloadable'
	loadStati['message'] = e.getMessage()
      }
    } catch (UnloadableImportException e) {
      println "Failed to load imports for " + oRec.id
      if (oRec && oRec.id) {
	loadStati['status'] = 'unloadable'
	loadStati['message'] = e.getMessage()
      }
    } catch (Exception e) {
      println ontUri + ' other error'
      if (oRec && oRec.id) {
	loadStati['status'] = 'unloadable'
	loadStati['message'] = e.getMessage()
      }
    }
  }

  void createOntologyReasoner(OWLReasonerFactory reasonerFactory, Map preferredLanguageMap) {
    OWLOntology ontology = this.ontology
    try {
      OWLOntologyManager manager = this.oManager
      /* Configure Elk */
      ReasonerConfiguration eConf = ReasonerConfiguration.getConfiguration()
      eConf.setParameter(ReasonerConfiguration.NUM_OF_WORKING_THREADS, this.ELK_THREADS)
      eConf.setParameter(ReasonerConfiguration.INCREMENTAL_MODE_ALLOWED, "true")

      /* OWLAPI Reasoner config, no progress monitor */
      OWLReasonerConfiguration rConf = new ElkReasonerConfiguration(ElkReasonerConfiguration.getDefaultOwlReasonerConfiguration(new NullReasonerProgressMonitor()), eConf)
      OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology, rConf);
      oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

      def sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);

      // dispose of old reasoners, close the threadpool
      queryEngine?.getoReasoner()?.dispose()

      // check if there are many many unsatisfiable classes, then switch to structural reasoner
      if (oReasoner.getEquivalentClasses(df.getOWLNothing()).getEntitiesMinusBottom().size() >= MAX_UNSATISFIABLE_CLASSES) {
	oReasoner.dispose()
	StructuralReasonerFactory sReasonerFactory = new StructuralReasonerFactory()
	oReasoner = sReasonerFactory.createReasoner(ontology)
	loadStati['status'] = 'incoherent'
	queryEngine = new QueryEngine(oReasoner, sForm)
	println "Successfully classified $ontUri but switched to structural reasoner"
      } else {
	this.queryEngine = new QueryEngine(oReasoner, sForm)
	println "Successfully classified $ontUri"
	loadStati['status'] = 'classified'
      }
    } catch (InconsistentOntologyException e) {
      println "inconsistent ontology $ontUri"
      try {
	oReasoner.dispose()
	StructuralReasonerFactory sReasonerFactory = new StructuralReasonerFactory()
	OWLReasoner sr = sReasonerFactory.createReasoner(ontology)
	def sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, ontologyManagers.get(k))
	this.queryEngine = new QueryEngine(sr, sForm)
	loadStati['status'] = 'inconsistent'
	loadStati['message'] = e.getMessage()
      } catch (Exception E) {
	println "Terminal error with $ontUri"
	E.printStackTrace()
      }
    } catch (java.lang.IndexOutOfBoundsException e) {
      println "Failed " + ontUri
      //      e.printStackTrace()
      loadStati['status'] = 'unloadable'
      loadStati['message'] = e.getMessage()
    } catch (Exception e) {
      println "Failed " + ontUri
      //      e.printStackTrace()
      loadStati['status'] = 'unloadable'
      loadStati['message'] = e.getMessage()
    }
  }

  /**
   * Create and run the reasoning on the loaded OWL ontologies, creating a QueryEngine for each.
   */
  void createReasoner() {
    println "Classifying $ontUri"
    List<String> langs = new ArrayList<>();
    Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
    for (OWLAnnotationProperty annotationProperty : this.aProperties) {
      preferredLanguageMap.put(annotationProperty, langs);
    }

    OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
    try {
      createOntologyReasoner(reasonerFactory, preferredLanguageMap)
    } catch (Exception E) {
      println "Exception encountered when reasoning $k: " + E
    }
    println "Classified $ontUri"
  }

  /**
   * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider for a given ontology.
   */
    void reloadOntologyAnnotations() {
    OWLDataFactory factory = df
    OWLAnnotationProperty rdfsLabel = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
    aProperties.add(rdfsLabel)
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym")))
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym")))
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")))
    }

  /**
   * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider for all ontologies.
   */
  void loadAnnotations() {
    reloadOntologyAnnotations()
  }

  Set classes2info(Set<OWLClass> classes, String uri) {
    o = this.ontology
    ArrayList result = new ArrayList<HashMap>();
    //    for (def c : classes) {
    classes.each { c ->
      def info = [
	"owlClass"  : c.toString(),
	"classURI"  : c.getIRI().toString(),
	"ontologyURI": uri.toString(),
	"remainder" : c.getIRI().getFragment(),
	"label"     : null,
	"definition": null,
	"deprecated": false
      ];

      for (OWLAnnotation annotation : EntitySearcher.getAnnotations(c, o)) {
	if (annotation.isDeprecatedIRIAnnotation()) {
	  info['deprecated'] = true
	}
      }
      if (info['deprecated'] == false ) {
	def labels = [
	  df.getRDFSLabel(),
	  df.getOWLAnnotationProperty(new IRI('http://www.w3.org/2004/02/skos/core#prefLabel')),
	  df.getOWLAnnotationProperty(new IRI('http://purl.obolibrary.org/obo/IAO_0000111'))
	]
	def definitions = [
	  df.getOWLAnnotationProperty(new IRI('http://purl.obolibrary.org/obo/IAO_0000115')),
	  df.getOWLAnnotationProperty(new IRI('http://www.w3.org/2004/02/skos/core#definition')),
	  df.getOWLAnnotationProperty(new IRI('http://purl.org/dc/elements/1.1/description')),
	  df.getOWLAnnotationProperty(new IRI('http://www.geneontology.org/formats/oboInOwl#hasDefinition'))
	]
	
	try {
	  labels.each {
	    EntitySearcher.getAnnotationAssertionAxioms(c, o).each { ax ->
	      if (ax.getProperty() == it) {
		//	EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
		if (ax.getValue() instanceof OWLLiteral) {
		  def val = (OWLLiteral) ax.getValue()
		  info['label'] = val.getLiteral()
		  throw new Exception("label found")
		}
	      }
	    }
	  }
	} catch (Exception E) {}
	try {
	  definitions.each {
	    EntitySearcher.getAnnotationAssertionAxioms(c, o).each { ax ->
	      if (ax.getProperty() == it) {
		//	EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
		if (ax.getValue() instanceof OWLLiteral) {
		  def val = (OWLLiteral) ax.getValue()
		  info['definition'] = val.getLiteral()
		  throw new Exception("label found")
		}
	      }
	    }
	  }
	} catch (Exception E) {}
      
	if (info['label'] == null) { // add but make deprecated
	  info['label'] = info['remainder']
	}
	result.add(info);
      }
    }
    return result
  }

  /**
   * Iterate the query engines, collecting results from each and collating them into a single structure.
   *
   * @param mOwlQuery Class query in Manchester OWL Syntax.
   * @param requestType Type of class match to be performed. Valid values are: subclass, superclass, equivalent or all.
   * @return Set of OWL Classes.
   */
  Set runQuery(String mOwlQuery, String type, boolean direct, boolean labels) {
    def start = System.currentTimeMillis()

    type = type.toLowerCase()
    def requestType
    switch (type) {
    case "superclass": requestType = RequestType.SUPERCLASS; break;
    case "subclass": requestType = RequestType.SUBCLASS; break;
    case "equivalent": requestType = RequestType.EQUIVALENT; break;
    case "supeq": requestType = RequestType.SUPEQ; break;
    case "subeq": requestType = RequestType.SUBEQ; break;
    case "realize": requestType = RequestType.REALIZE; break;
    default: requestType = RequestType.SUBEQ; break;
    }

    Set classes = new HashSet<>();
    Set resultSet = Sets.newHashSet(Iterables.limit(queryEngine.getClasses(mOwlQuery, requestType, direct, labels), MAX_REASONER_RESULTS))
    resultSet.remove(df.getOWLNothing())
    resultSet.remove(df.getOWLThing())
    classes.addAll(classes2info(resultSet, ontology, ontUri))

    def end = System.currentTimeMillis()

    return classes;
  }


  Set runQuery(String mOwlQuery, String type) {
    return runQuery(mOwlQuery, type, false)
  }

  /** This returns the direct R-successors of a class C in O
      class and relations are given as String-IRIs
  */
  Set relationQuery(String relation, String cl) {
    Set classes = new HashSet<>();

    def vOntUri = ontUri

    // get the direct subclasses of cl
    Set<OWLClass> subclasses = queryEngine.getClasses(cl, RequestType.SUBCLASS, true, false)
    // These are all the classes for which the R some C property holds
    String query1 = "<$relation> SOME $cl"
    Set<OWLClass> mainResult = queryEngine.getClasses(query1, RequestType.SUBCLASS, true, false)
    // Now remove all classes that are not specific to cl (i.e., there is a more specific class in which the R-edge can be created)
    subclasses.each { sc ->
      String query2 = "$relation SOME " + sc.toString()
      def subResult = queryEngine.getClasses(query2, RequestType.SUBCLASS, true, false)
      mainResult = mainResult - subResult
    }
    classes.addAll(classes2info(mainResult, ontology, ontUri))
    return classes
  }

  Map<String, QueryEngine> getQueryEngine() {
    return this.queryEngine
  }

  /**
   * @return the oManager
   */
  OWLOntologyManager getoManager() {
    return oManager
  }

  /**
   * @return the ontologies
   */
  def getOntology() {
    return ontology
  }

  /**
   * Get the axiom count of all the ontologies
   */
  Map getStats(String oString) {
    def stats = []
    OWLOntology ont = ontology
    stats = [
      'axiomCount': 0,
	     'classCount': ont.getClassesInSignature(true).size()
    ]
    AxiomType.TBoxAxiomTypes.each { ont.getAxioms(it, true).each { stats.axiomCount += 1 } }
    AxiomType.RBoxAxiomTypes.each { ont.getAxioms(it, true).each { stats.axiomCount += 1 } }

    return stats
  }
  
  HashMap getInfoObjectProperty(String uriObjectProperty) {
    HashMap objectProperties = new HashMap<String, String>()
    OWLObjectProperty objectProperty = df.getOWLObjectProperty(IRI.create(uriObjectProperty));
    Iterator<OWLAnnotationAssertionAxiom> jt = EntitySearcher.getAnnotationAssertionAxioms(objectProperty, ontology).iterator();
    OWLAnnotationAssertionAxiom axiom;
    while (jt.hasNext()) {
      axiom = jt.next();
      if (axiom.getProperty().isLabel()) {
	OWLLiteral value = (OWLLiteral) axiom.getValue();
	objectProperties.put('classURI', axiom.getSubject().toString());
	objectProperties.put('label', value.getLiteral().toString());
      }
    }
    return objectProperties;
  }

  /**
   * Gets the sub object properties from the ontology given
   * oString This paramater represents the id of the ontology.
   * rootObjectProperty This parameter represents the root object property asked.
   */
  Set getObjectProperties(String rootObjectProperty) {
    Set objectProperties = new HashSet();
    if ((rootObjectProperty != null) && (rootObjectProperty.length() > 0)) {
      StructuralReasoner structuralReasoner = new StructuralReasoner(ontology, new SimpleConfiguration(), BufferingMode.NON_BUFFERING)
      OWLObjectProperty objectProperty = df.getOWLObjectProperty(IRI.create(rootObjectProperty))
      Set<OWLObjectPropertyExpression> properties = structuralReasoner.getSubObjectProperties(objectProperty, true).getFlattened()

      for (OWLObjectPropertyExpression expression : properties) {
	Iterator<OWLAnnotationAssertionAxiom> jt = EntitySearcher.getAnnotationAssertionAxioms(expression.getNamedProperty(), structuralReasoner.getRootOntology()).iterator()
	OWLAnnotationAssertionAxiom axiom
	HashMap subProperty = new HashMap<String, String>();
	while (jt.hasNext()) {
	  axiom = jt.next();
	  if (axiom.getProperty().isLabel()) {
	    OWLLiteral value = (OWLLiteral) axiom.getValue();
	    subProperty.put('classURI', axiom.getSubject().toString());
	    subProperty.put('label', value.getLiteral().toString());
	    objectProperties.add(subProperty);
	  }
	}
      }
    }
    return objectProperties;
  }

  /**
   * Retrieve the list of objects properties
   */
  HashMap getObjectProperties() {
    HashMap objectProperties = new HashMap<String, String>()
    StructuralReasoner structuralReasoner = new StructuralReasoner(ontology, new SimpleConfiguration(), BufferingMode.NON_BUFFERING)
    getRecursiveObjectProperties(objectProperties, df.getOWLTopObjectProperty(), structuralReasoner)
    return objectProperties
  }

  private void getRecursiveObjectProperties(HashMap objectProperties, OWLObjectProperty rootObjectProperty, OWLReasoner reasoner) {
    Set<OWLObjectPropertyExpression> properties = reasoner.getSubObjectProperties(rootObjectProperty, true).getFlattened()

    if (properties.empty) {
      return;
    }
    for (OWLObjectPropertyExpression expression : properties) {
      Iterator<OWLAnnotationAssertionAxiom> jt = EntitySearcher.getAnnotationAssertionAxioms(expression.getNamedProperty(), reasoner.getRootOntology()).iterator();
      OWLAnnotationAssertionAxiom axiom;
      while (jt.hasNext()) {
	axiom = jt.next();
	if (axiom.getProperty().isLabel()) {
	  OWLLiteral value = (OWLLiteral) axiom.getValue()
	  objectProperties.put(value.getLiteral().toString(), "<" + axiom.getSubject().toString() + ">")
	}
      }
      getRecursiveObjectProperties(objectProperties, expression.getNamedProperty(), reasoner)
    }
  }
}

