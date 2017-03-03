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
import groovyx.gpars.ParallelEnhancer
import groovyx.gpars.GParsPool

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
  // max classes returned by query; to prevent DoS; TODO: replace by paging!

  private static final URL = 'http://10.81.0.162:9200/'

  int loadedOntologies = 0
  int attemptedOntologies = 0
  int noFileError = 0;
  int importError = 0;
  int parseError = 0;
  int otherError = 0;
  def lCount = 0
  def dCount = 0
  OWLOntologyManager oManager;
  List<OWLAnnotationProperty> aProperties = new ArrayList<>();
  OWLDataFactory df = OWLManager.getOWLDataFactory();
  OntologyDatabase oBase = new OntologyDatabase()


  def ontologies = new ConcurrentHashMap()
  def ontologyManagers = new ConcurrentHashMap()
  def queryEngines = new ConcurrentHashMap()
  def loadStati = new ConcurrentHashMap()
  def oldOntologies = new ConcurrentHashMap()

  def static search(def type, def map) {

    def url = 'http://10.81.0.162:9200'
    def http = new HTTPBuilder(url)
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

  RequestManager(boolean reason) {

    //    println "Loading ontologies"
    loadOntologies();
    loadAnnotations();
    if (reason) {
      def thread = Thread.start {
	createReasoner()
      }
      thread.join()
    }
    println "Loading of ontologies finished; AberOWL is ready for service."
  }

  Set<String> listOntologies() {
    return ontologies.keySet();
  }

  List<String> queryNames(String query, String ontUri) {
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

  Set<String> queryOntologies(String query) {
    if (query) {
      String[] fields = ['name', 'lontology', 'description']

      def oQuery = query
      def omap = [:]
      omap = ["query": ["bool" : ["should" : []]]]
      fields.each { f ->
	def m = ["match": ["${f}": ["query":oQuery]]]
	omap.query.bool.should << m
      }
      def hits = search("ontology", omap)
      def ret = []
      hits.hits.hits.each { 
	def temp = it._source
	temp.value = temp.first_label
	temp.data = temp["class"]
	ret << temp
      }
      return ret.sort { it.name.size() }
    } else {
      return []
    }
  }


  /**
   * Load a new or replace an existing ontology
   *
   * @param name corresponding to name of the ontology in the database
   */
  void reloadOntology(String name, int version) {
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
    if (!ontologies.get(oRec.id)) {
      newO = true
    }
    try {
      OWLOntologyManager lManager = OWLManager.createOWLOntologyManager()
      OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
      config.setFollowRedirects(true)
      config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)
      println 'trying to update current version of ontology'
      def fSource = new FileDocumentSource(new File('../ontologies/' + name + '/live/' + name + '.owl'))
      def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config)
      println "Updated ontology: " + oRec.id
      
      ontologies.put(oRec.id, ontology)
      ontologyManagers.put(oRec.id, lManager)

      reloadOntologyAnnotations(oRec.id)

      if (newO) {
        loadedOntologies++
      }
      List<String> langs = new ArrayList<>();
      Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
      for (OWLAnnotationProperty annotationProperty : this.aProperties) {
        preferredLanguageMap.put(annotationProperty, langs);
      }
      OWLReasonerFactory reasonerFactory = new ElkReasonerFactory()
      createOntologyReasoner(oRec.id, reasonerFactory, preferredLanguageMap)

      // May be replaced with any reasoner using the standard interface
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
  void loadOntologies() throws OWLOntologyCreationException, IOException {
    def pool = null
    GParsPool.withPool(64) { p ->
      pool = p
      def allOnts = oBase.allOntologies()
      allOnts.eachParallel { oRec ->
        attemptedOntologies+=1
	if (oRec.id in ["CHEBI"]) {
	  //if (true) {
	  try {
	    if (oRec.lastSubDate == 0) {
	      return;
	    }
	    println "Loading " + oRec.id + " [" + loadedOntologies + "/" + allOnts.size() + "]"
	    OWLOntologyManager lManager = OWLManager.createOWLOntologyManager();
	    OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
	    config.setFollowRedirects(true);
	    config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
	    def fSource = new FileDocumentSource(new File('../ontologies/' + oRec.id + '/live/' + oRec.id + '.owl'))
	    def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config);
	    ontologies.put(oRec.id, ontology)
	    ontologyManagers.put(oRec.id, lManager)
	    
	    loadedOntologies+=1
	    println "Successfully loaded " + oRec.id + " [" + loadedOntologies + "/" + allOnts.size() + "]"
	    loadStati.put(oRec.id, ['status': 'loaded'])
	  } catch (OWLOntologyAlreadyExistsException E) {
	    if (oRec && oRec.id) {
	      println 'DUPLICATE ' + oRec.id
	    }
	  } catch (OWLOntologyInputSourceException e) {
	    if (oRec && oRec.id) {
	      loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
	    }
	    noFileError+=1
	  } catch (IOException e) {
	    println "Can't load external import for " + oRec.id
	    if (oRec && oRec.id) {
	      loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
	    }
	    importError+=1
	  } catch (OWLOntologyCreationIOException e) {
	    println "Failed to load imports for " + oRec.id
	    if (oRec && oRec.id) {
	      loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
	    }
	    importError+=1
	  } catch (UnparsableOntologyException e) {
	    println "Failed to parse ontology " + oRec.id
	    //          e.printStackTrace()
	    if (oRec && oRec.id) {
	      loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
	    }
	    parseError+=1
	  } catch (UnloadableImportException e) {
	    println "Failed to load imports for " + oRec.id
	    if (oRec && oRec.id) {
	      loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
	    }
	    importError++
	      } catch (Exception e) {
	    println oRec.id + ' other'
	    if (oRec && oRec.id) {
	      loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
	    }
	    otherError+=1
	  }
	}
      }
    }
  }

  void createOntologyReasoner(String k, OWLReasonerFactory reasonerFactory, Map preferredLanguageMap) {
    OWLOntology ontology
    try {
      ontology = ontologies[k]
      OWLOntologyManager manager = ontologyManagers[k]
      /* Configure Elk */
      ReasonerConfiguration eConf = ReasonerConfiguration.getConfiguration()
      eConf.setParameter(ReasonerConfiguration.NUM_OF_WORKING_THREADS, this.ELK_THREADS)
      eConf.setParameter(ReasonerConfiguration.INCREMENTAL_MODE_ALLOWED, "true")
      //eConf.setParameter(ReasonerConfiguration.INCREMENTAL_TAXONOMY, "true")

      /* OWLAPI Reasoner config, no progress monitor */
      OWLReasonerConfiguration rConf = new ElkReasonerConfiguration(ElkReasonerConfiguration.getDefaultOwlReasonerConfiguration(new NullReasonerProgressMonitor()), eConf)
      OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology, rConf);
      oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

      def sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);

      // dispose of old reasoners, close the threadpool
      queryEngines[k]?.getoReasoner()?.dispose()

      // check if there are many many unsatisfiable classes, then switch to structural reasoner
      if (oReasoner.getEquivalentClasses(df.getOWLNothing()).getEntitiesMinusBottom().size() >= MAX_UNSATISFIABLE_CLASSES) {
	oReasoner.dispose()
        StructuralReasonerFactory sReasonerFactory = new StructuralReasonerFactory()
        oReasoner = sReasonerFactory.createReasoner(ontology)
        loadStati[k] = ['status': 'incoherent']
        this.queryEngines[k] = new QueryEngine(oReasoner, sForm)
        println "Successfully classified but switched to structural reasoner " + k + " [" + this.queryEngines.size() + "/" + ontologies.size() + "]"
      } else {
        this.queryEngines[k] = new QueryEngine(oReasoner, sForm)
        println "Successfully classified " + k + " [" + this.queryEngines.size() + "/" + ontologies.size() + "]"
        loadStati[k] = ['status': 'classified']
      }
    } catch (InconsistentOntologyException e) {
      println "inconsistent ontology " + k
      try {
	oReasoner.dispose()
        StructuralReasonerFactory sReasonerFactory = new StructuralReasonerFactory()
        OWLReasoner sr = sReasonerFactory.createReasoner(ontology)
        def sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, ontologyManagers.get(k))
        this.queryEngines[k] = new QueryEngine(sr, sForm)
        loadStati[k] = ['status': 'inconsistent', 'message': e.getMessage()]
      } catch (Exception E) {
        println "Terminal error with $k"
        E.printStackTrace()
      }
    } catch (java.lang.IndexOutOfBoundsException e) {
      println "Failed " + k
      e.printStackTrace()
      loadStati[k] = ['status': 'unloadable', 'message': e.getMessage()]
    } catch (Exception e) {
      println "Failed " + k
      e.printStackTrace()
      loadStati[k] = ['status': 'unloadable', 'message': e.getMessage()]
    }
  }

  /**
   * Create and run the reasoning on the loaded OWL ontologies, creating a QueryEngine for each.
   */
  void createReasoner() {
    println "REASONING"
    List<String> langs = new ArrayList<>();
    Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
    for (OWLAnnotationProperty annotationProperty : this.aProperties) {
      preferredLanguageMap.put(annotationProperty, langs);
    }

    OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
    // May be replaced with any reasoner using the standard interface
    GParsPool.withPool(64) {
      ontologies.eachParallel { k, oRec ->
        try {
          createOntologyReasoner(k, reasonerFactory, preferredLanguageMap)
        } catch (Exception E) {
          println "Exception encountered when reasoning $k: " + E
        }
      }
    }
    println "REASONED"
  }

  /**
   * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider for a given ontology.
   */
  void reloadOntologyAnnotations(id) {
    OWLDataFactory factory = ontologyManagers.get(id).getOWLDataFactory();
    OWLAnnotationProperty rdfsLabel = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI());
    aProperties.add(rdfsLabel);
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym")));
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym")));
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym")));
  }

  /**
   * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider for all ontologies.
   */
  void loadAnnotations() {
    println "Making annotations"
    for (String id : ontologyManagers.keySet()) { // For some reason .each doesn't work here
      reloadOntologyAnnotations(id)
    }
  }

  Set classes2info(Set<OWLClass> classes, OWLOntology o, String uri) {
    ArrayList result = new ArrayList<HashMap>();
    println classes.size()
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
  Set runQuery(String mOwlQuery, String type, String ontUri, int version, boolean direct, boolean labels) {
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
    if (ontUri == null || ontUri.length() == 0) { // query all the ontologies in the repo
      Iterator<String> it = queryEngines.keySet().iterator();
      while (it.hasNext() && classes.size() < MAX_QUERY_RESULTS) {
        String oListString = it.next();
        QueryEngine queryEngine = queryEngines.get(oListString);
        OWLOntology ontology = ontologies.get(oListString);
        Set resultSet = Sets.newHashSet(Iterables.limit(queryEngine.getClasses(mOwlQuery, requestType, direct, labels), MAX_REASONER_RESULTS))
        resultSet.remove(df.getOWLNothing());
        resultSet.remove(df.getOWLThing());
        classes.addAll(classes2info(resultSet, ontology, oListString))
      }
    } else if (this.queryEngines.get(ontUri) == null) { // download the ontology and query
      Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>()
      for (OWLAnnotationProperty annotationProperty : this.aProperties) {
        preferredLanguageMap.put(annotationProperty, new ArrayList<String>())
      }
      try {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager()
        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
        config.setFollowRedirects(true)
        config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(ontUri)), config)
        OWLReasonerFactory reasonerFactory = new ElkReasonerFactory()
        OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology)
        oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY)
        def sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager)
        Set resultSet = new QueryEngine(oReasoner, sForm).getClasses(mOwlQuery, requestType, direct, labels)
        resultSet.remove(df.getOWLNothing())
        resultSet.remove(df.getOWLThing())
        classes.addAll(classes2info(resultSet, ontology, ontUri))
	oReasoner.dispose()
      } catch (OWLOntologyCreationException E) {
        // E.printStackTrace();
      } 
    } else { // query one single ontology
      QueryEngine queryEngine = queryEngines.get(ontUri);
      OWLOntology ontology = ontologies.get(ontUri)
      //      println queryEngine.getClasses(mOwlQuery, requestType, direct, labels)
      Set resultSet = Sets.newHashSet(Iterables.limit(queryEngine.getClasses(mOwlQuery, requestType, direct, labels), MAX_REASONER_RESULTS))
     //Set<OWLClass> resultSet = queryEngine.getClasses(mOwlQuery, requestType, direct, labels)
      resultSet.remove(df.getOWLNothing())
      resultSet.remove(df.getOWLThing())
      classes.addAll(classes2info(resultSet, ontology, ontUri))
    }

    def end = System.currentTimeMillis()
    //    println(mOwlQuery + ' ' + type + ' took: ' + (end - start) + 'ms')

    return classes;
  }


  Set runQuery(String mOwlQuery, String type, String ontUri) {
    return runQuery(mOwlQuery, type, ontUri, false)
  }

  /** This returns the direct R-successors of a class C in O
   class and relations are given as String-IRIs
   */
  Set relationQuery(String relation, String cl, String ontUri, Integer version) {
    Set classes = new HashSet<>();

    QueryEngine queryEngine = queryEngines.get(ontUri);
    def vOntUri = ontUri
    if (version >= 0) {
      vOntUri = ontUri + "_" + version
    }

    if (!ontologies.containsKey(vOntUri)) {
      reloadOntology(ontUri, version)
    }

    OWLOntology ontology = ontologies.get(ontUri)

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

  Map<String, QueryEngine> getQueryEngines() {
    return this.queryEngines;
  }

  /**
   * @return the oManager
   */
  OWLOntologyManager getoManager() {
    return oManager;
  }

  /**
   * @return the ontologies
   */
  Map<String, OWLOntology> getOntologies() {
    return ontologies;
  }

  /**
   * Get the axiom count of all the ontologies
   */
  Map getStats(String oString) {
    def stats = []
    if (oString == null || oString.length() == 0) { // query all the ontologies in the repo
      stats = [
	'aCount'    : 0, // Axiom count
	       'cCount'    : 0, // Class count
	       'oCount'    : ontologies.size(),
	       'noFileError': noFileError,
	       'importError': importError,
	       'parseError': parseError,
	       'otherError': otherError,
	       'lCount'    : lCount,
	       'dCount'    : dCount
      ]
      
      for (String id : ontologies.keySet()) { // For some reason .each doesn't work here
        OWLOntology oRec = ontologies.get(id);
        stats.aCount += oRec.getAxiomCount()
        stats.cCount += oRec.getClassesInSignature(true).size()
      }
      
    } else {
      OWLOntology ont = ontologies.get(oString);
      stats = [
	'axiomCount': 0,
	       'classCount': ont.getClassesInSignature(true).size()
      ]
      AxiomType.TBoxAxiomTypes.each { ont.getAxioms(it, true).each { stats.axiomCount += 1 } }
      AxiomType.RBoxAxiomTypes.each { ont.getAxioms(it, true).each { stats.axiomCount += 1 } }
    }
    
    return stats
  }
  
  HashMap getInfoObjectProperty(String oString, String uriObjectProperty) {
    HashMap objectProperties = new HashMap<String, String>();
    if ((oString != null) && (oString.length() > 0)) {
      if (ontologies.containsKey(oString)) {
        OWLOntology ontology = ontologies.get(oString);
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
      }
    }
    return objectProperties;
  }

  /**
   * Gets the sub object properties from the ontology given
   * oString This paramater represents the id of the ontology.
   * rootObjectProperty This parameter represents the root object property asked.
   */
  Set getObjectProperties(String oString, String rootObjectProperty) {
    Set objectProperties = new HashSet();
    if ((oString != null) && (oString.length() > 0) && (rootObjectProperty != null) && (rootObjectProperty.length() > 0)) {
      if (ontologies.containsKey(oString)) {
        OWLOntology ontology = ontologies.get(oString);
        StructuralReasoner structuralReasoner = new StructuralReasoner(ontology, new SimpleConfiguration(), BufferingMode.NON_BUFFERING);
        OWLObjectProperty objectProperty = df.getOWLObjectProperty(IRI.create(rootObjectProperty));
        Set<OWLObjectPropertyExpression> properties = structuralReasoner.getSubObjectProperties(objectProperty, true).getFlattened();

        for (OWLObjectPropertyExpression expression : properties) {
          Iterator<OWLAnnotationAssertionAxiom> jt = EntitySearcher.getAnnotationAssertionAxioms(expression.getNamedProperty(), structuralReasoner.getRootOntology()).iterator();
          OWLAnnotationAssertionAxiom axiom;
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
    }
    return objectProperties;
  }

  /**
   * Retrieve the list of objects properties
   */
  HashMap getObjectProperties(String oString) {
    HashMap objectProperties = new HashMap<String, String>();
    if ((oString != null) && (oString.length() > 0)) {
      if (ontologies.containsKey(oString)) {
        OWLOntology ontology = ontologies.get(oString);
        StructuralReasoner structuralReasoner = new StructuralReasoner(ontology, new SimpleConfiguration(), BufferingMode.NON_BUFFERING);
        getRecursiveObjectProperties(objectProperties, df.getOWLTopObjectProperty(), structuralReasoner);
      }
    }
    return objectProperties;
  }

  private void getRecursiveObjectProperties(HashMap objectProperties, OWLObjectProperty rootObjectProperty, OWLReasoner reasoner) {
    Set<OWLObjectPropertyExpression> properties = reasoner.getSubObjectProperties(rootObjectProperty, true).getFlattened();

    if (properties.empty) {
      return;
    }
    for (OWLObjectPropertyExpression expression : properties) {
      Iterator<OWLAnnotationAssertionAxiom> jt = EntitySearcher.getAnnotationAssertionAxioms(expression.getNamedProperty(), reasoner.getRootOntology()).iterator();
      OWLAnnotationAssertionAxiom axiom;
      while (jt.hasNext()) {
        axiom = jt.next();
        if (axiom.getProperty().isLabel()) {
          OWLLiteral value = (OWLLiteral) axiom.getValue();
          objectProperties.put(value.getLiteral().toString(), "<" + axiom.getSubject().toString() + ">");
        }
      }

      getRecursiveObjectProperties(objectProperties, expression.getNamedProperty(), reasoner);

    }
    return;
  }
}
