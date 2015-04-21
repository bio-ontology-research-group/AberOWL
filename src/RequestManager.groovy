package src

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import org.semanticweb.owlapi.owllink.*;

import org.apache.lucene.search.Hits;
import org.apache.lucene.search.Query;
import org.apache.lucene.document.Field;
import org.apache.lucene.search.Searcher;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.document.Document;
import org.apache.lucene.store.RAMDirectory;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.analysis.standard.StandardAnalyzer;

import java.util.concurrent.*
import util.*;
import db.*;
import groovyx.gpars.ParallelEnhancer
import groovyx.gpars.GParsPool

class RequestManager {
  int loadedOntologies = 0;
  int attemptedOntologies = 0;
  int noFileError = 0;
  int importError = 0;
  int parseError = 0;
  int otherError = 0;
  OWLOntologyManager oManager;
  List<OWLAnnotationProperty> aProperties = new ArrayList<>();
  OWLDataFactory df = OWLManager.getOWLDataFactory() ;
  OntologyDatabase oBase = new OntologyDatabase()

  def ontologies = new ConcurrentHashMap();
  def ontologyManagers = new ConcurrentHashMap();
  def queryEngines = new ConcurrentHashMap();

  // Index things
  RAMDirectory index = new RAMDirectory()
          
  RequestManager(boolean reason) {
    println "Loading ontologies"
    loadOntologies();
    loadAnnotations();
    loadLabels();
    if(reason) {
      createReasoner();
    }
  }
      
  Set<String> listOntologies() {
    return ontologies.keySet() ;
  }
      
  Set<String> queryNames(String query, String ontUri) {
    query = query.toLowerCase() ;
    Set<String> results = new LinkedHashSet<>() ;
    SuggestTree tree = null ;
    if (ontUri == null || ontUri.length()==0) { // query allLabels
      tree = allLabels ;
    } else { // query ontUri
      tree = labels.get(ontUri) ;
    }
    if(tree !=null) {
      Node n = tree.autocompleteSuggestionsFor(query) ;
      if (n != null) {
        for (int i = 0 ; i < n.listLength() ; i++) {
          String elem = n.listElement(i) ;
          String elemForOWL ;
          if (elem.indexOf(" ")>-1) {
            elemForOWL = "'"+elem+"'";
          } else {
            elemForOWL = elem ;
          }
          Map<String, Set<String>> s2id = null ;
          if  (ontUri == null || ontUri.length()==0) {
            s2id = allLabels2id ;
          } else {
            s2id = labels2id.get(ontUri) ;
          }
          for (String id : s2id.get(elem)) {
            results.add(elemForOWL) ;
          }
        }
      }
    }
    return results;
  }

  void reloadOntologyIndex(String uri, IndexWriter index) {
    def ont = ontologies.get(uri)
    
    ont.getImportsClosure().each { iOnt -> 
      iOnt.getClassesInSignature.each { iClass ->
        def cIRI = iClass.getIRI().toString()
        iClass.getAnnotations(iOnt, df.getRDFSLabel()).each { annotation ->
          if(annotation.getValue() instanceof OWLLiteral) {
            def val = (OWLLiteral) annotation.getValue()
            def label = val.getLiteral().toLowerCase()
            
            def doc = new Document()
            doc.add(new Field('ontology', uri, Field.Store.YES, Field.Index.YES))
            doc.add(new Field('class', cIRI, Field.Store.YES, Field.Index.YES))
            doc.add(new Field('label', label, Field.Store.YES, Field.Index.YES))
            index.addDocument(doc)
          }
        }
      }
      iOnt.getObjectPropertiesInSignature(true).each { iClass ->
        def cIRI = iClass.getIRI().toString()
        iClass.getAnnotations(iOnt, df.getRDFSLabel()).each { annotation ->
          if(annotation.getValue() instanceof OWLLiteral) {
            def val = (OWLLiteral) annotation.getValue()
            def label = val.getLiteral().toLowerCase()
            
            def doc = new Document()
            doc.add(new Field('ontology', uri, Field.Store.YES, Field.Index.YES))
            doc.add(new Field('class', cIRI, Field.Store.YES, Field.Index.YES))
            doc.add(new Field('label', label, Field.Store.YES, Field.Index.YES))
            index.addDocument(doc)
          }
        }
      }
    }
  }

  void loadIndex() {
    IndexWriter writer = new IndexWriter(index, new StandardAnalyzer(), true)
    for (String uri : ontologies.keySet()) {
      reloadOntologyIndex(uri, index)
    }
    writer.optimize()
    writer.close()

    searcher = new IndexSearcher(index)
  }

  /**
   * Load a new or replace an existing ontology
   *
   * @param name corresponding to name of the ontology in the database
   */
  void reloadOntology(String name) {
    def oRec = oBase.getOntology(name, false)
    if(!oRec) {
      return null
    }
    if(oRec.lastSubDate == 0) {
      return null
    }
    boolean newO = false
    if(!ontologies.get(oRec.id)) {
      newO = true
    }

    try {
      OWLOntologyManager lManager = OWLManager.createOWLOntologyManager()
      OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration()
      config.setFollowRedirects(true)
      config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT)
      def fSource = new FileDocumentSource(new File('onts/'+oRec.submissions[oRec.lastSubDate.toString()]))
      def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config)
      ontologies.put(oRec.id, ontology)
      ontologyManagers.put(oRec.id, lManager)

      println "Updated ontology: " + oRec.id
      if(newO) {
        loadedOntologies++
      }

      reloadOntologyAnnotations(oRec.id)
      loadIndex() // TODO: reload only one instead of rewriting the whole index!

      List<String> langs = new ArrayList<>();
      Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
      for (OWLAnnotationProperty annotationProperty : this.aProperties) {
        preferredLanguageMap.put(annotationProperty, langs);
      }

      OWLReasonerFactory reasonerFactory = new ElkReasonerFactory(); // May be replaced with any reasoner using the standard interface
      createOntologyReasoner(oRec.id, reasonerFactory, preferredLanguageMap)
    } catch(OWLOntologyInputSourceException e) {
      println "input source exception for " + oRec.id
    } catch(IOException e) {
      println "IOException exception for " + oRec.id
    } catch(Exception e) {
      e.printStackTrace()
    }
  }

  /**
   * Create the ontology manager and load it with the given ontology.
   * 
   * @throws OWLOntologyCreationException, IOException
   */
  void loadOntologies() throws OWLOntologyCreationException, IOException {
    GParsPool.withPool {
      def allOnts = oBase.allOntologies()
      allOnts.eachParallel { oRec ->
        attemptedOntologies++
        try {
          if(oRec.lastSubDate == 0) {
            return;
          }
          OWLOntologyManager lManager = OWLManager.createOWLOntologyManager();
          OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration() ;
          config.setFollowRedirects(true) ;
          config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT) ;
          def fSource = new FileDocumentSource(new File('onts/'+oRec.submissions[oRec.lastSubDate.toString()]))
          def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config);
          ontologies.put(oRec.id ,ontology)
          ontologyManagers.put(oRec.id, lManager)

          loadedOntologies++
          println "Successfully loaded " + oRec.id + " ["+loadedOntologies+"/"+allOnts.size()+"]"
        } catch (OWLOntologyAlreadyExistsException E) {
          // do nothing
          println 'DUPLICATE ' + oRec.id
        } catch (OWLOntologyInputSourceException e) {
          println "File not found for " + oRec.id
          noFileError++
        } catch (IOException e) {
          println "Can't load external import for " + oRec.id 
          importError++
        } catch(OWLOntologyCreationIOException e) {
          println "Failed to load imports for " + oRec.id
          importError++
        } catch(UnparsableOntologyException e) {
          println "Failed to parse ontology " + oRec.id
          parseError++
        } catch(UnloadableImportException e) {
          println "Failed to load imports for " + oRec.id
          importError++
        } catch (Exception E) {
          println oRec.id + ' other'
          otherError++
        }
      }
    }
  }

  void createOntologyReasoner(String k, OWLReasonerFactory reasonerFactory, Map preferredLanguageMap) {
    try {
      OWLOntology ontology = ontologies.get(k) ;
      OWLOntologyManager manager = ontologyManagers.get(k) ;
      OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology);
      oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);

      NewShortFormProvider sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);
      this.queryEngines.put(k, new QueryEngine(oReasoner, sForm));
    } catch(InconsistentOntologyException e) {
      println "inconsistent ontology " + k
    } catch (java.lang.IndexOutOfBoundsException e) {
      println "Failed " + k
    } catch (Exception e) {
      println "Failed " + k
      e.printStackTrace()
    }
  }
      
  /**
   * Create and run the reasoning on the loaded OWL ontologies, creating a QueryEngine for each.
   */
  void createReasoner() {
    println "REASING"
    List<String> langs = new ArrayList<>();
    Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
    for (OWLAnnotationProperty annotationProperty : this.aProperties) {
      preferredLanguageMap.put(annotationProperty, langs);
    }

    OWLReasonerFactory reasonerFactory = new ElkReasonerFactory(); // May be replaced with any reasoner using the standard interface
    GParsPool.withPool {
      ontologies.eachParallel { k, oRec ->
        createOntologyReasoner(k, reasonerFactory, preferredLanguageMap)
      }
    }
    println "REASONED"
  }

  /**
   * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider for a given ontology.
   */
  void reloadOntologyAnnotations(id) {
    OWLDataFactory factory = ontologyManagers.get(id).getOWLDataFactory();
    OWLAnnotationProperty rdfsLabel = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()) ;                                                       
    aProperties.add(rdfsLabel);
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym"))) ;
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym"))) ;
    aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"))) ;
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
    for(def c : classes) {
      def info = [
        "owlClass": c.toString(),
        "classURI": c.getIRI().toString(),
        "ontologyURI": uri.toString(),
        "remainder": c.getIRI().getFragment(),
        "label": null,
        "definition": null 
      ];

      for (OWLOntology ont : o.getImportsClosure()) {
        for (OWLAnnotation annotation : c.getAnnotations(ont, df.getRDFSLabel())) {
          if (annotation.getValue() instanceof OWLLiteral) {
            OWLLiteral val = (OWLLiteral) annotation.getValue();
            info['label'] = val.getLiteral() ;
          }
        }
        for (OWLAnnotation annotation : c.getAnnotations(o, df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115")))) {
          if (annotation.getValue() instanceof OWLLiteral) {
            OWLLiteral val = (OWLLiteral) annotation.getValue();
            info['definition'] = val.getLiteral() ;
          }
        }
      }
      /* definition */
      result.add(info);
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
  Set runQuery(String mOwlQuery, String type, String ontUri) {
    def start = System.currentTimeMillis()

    type = type.toLowerCase()
    def requestType

    switch(type) {
      case "superclass": requestType = RequestType.SUPERCLASS; break;
      case "subclass": requestType = RequestType.SUBCLASS; break;
      case "equivalent": requestType = RequestType.EQUIVALENT; break;
      case "supeq": requestType = RequestType.SUPEQ; break;
      case "subeq": requestType = RequestType.SUBEQ; break;
      default: requestType = RequestType.SUBEQ; break;
    }

    Set classes = new HashSet<>();
    if (ontUri == null || ontUri.length() == 0) { // query all the ontologies in the repo
      Iterator<String> it = queryEngines.keySet().iterator() ;
      while (it.hasNext()) {
        String oListString = it.next() ;
        QueryEngine queryEngine = queryEngines.get(oListString) ;
        OWLOntology ontology = ontologies.get(oListString) ;
          Set<OWLClass> resultSet = queryEngine.getClasses(mOwlQuery, requestType) ;
          resultSet.remove(df.getOWLNothing()) ;
          resultSet.remove(df.getOWLThing()) ;
          classes.addAll(classes2info(resultSet, ontology, oListString)) ;
      }
    } else if (queryEngines.get(ontUri) == null) { // download the ontology and query
      Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
      for (OWLAnnotationProperty annotationProperty : this.aProperties) {
        preferredLanguageMap.put(annotationProperty, new ArrayList<String>());
      }
      try {
        OWLOntologyManager manager = OWLManager.createOWLOntologyManager() ;
        OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration() ;                                                                            
        config.setFollowRedirects(true) ;                                                                                                                         
        config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT) ;                                                                           
        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(ontUri)), config);
        OWLReasonerFactory reasonerFactory = new ElkReasonerFactory(); 
        OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology);
        oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
        NewShortFormProvider sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);
        Set<OWLClass> resultSet = new QueryEngine(oReasoner, sForm).getClasses(mOwlQuery, requestType) ;
        resultSet.remove(df.getOWLNothing()) ;
        resultSet.remove(df.getOWLThing()) ;
        classes.addAll(classes2info(resultSet, ontology, ontUri)) ;
      } catch (OWLOntologyCreationException E) {
        E.printStackTrace() ;
      }
    } else { // query one single ontology
      QueryEngine queryEngine = queryEngines.get(ontUri) ;
      OWLOntology ontology = ontologies.get(ontUri) ;
        Set<OWLClass> resultSet = queryEngine.getClasses(mOwlQuery, requestType) ;
        resultSet.remove(df.getOWLNothing()) ;
        resultSet.remove(df.getOWLThing()) ;
        classes.addAll(classes2info(resultSet, ontology, ontUri)) ;
    }

    def end = System.currentTimeMillis()
    println(mOwlQuery + ' ' + type + ' took: ' + (end - start) + 'ms')

    return classes;
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
        'aCount': 0, // Axiom count
        'cCount': 0, // Class count
        'oCount': ontologies.size(),
        'noFileError': noFileError,
        'importError': importError,
        'parseError': parseError,
        'otherError': otherError
      ];

      for (String id : ontologies.keySet()) { // For some reason .each doesn't work here
        OWLOntology oRec = ontologies.get(id) ;
        stats.aCount += oRec.getAxiomCount()
        stats.cCount += oRec.getClassesInSignature(true).size()
      }

      println stats
    } else {
      OWLOntology ont = ontologies.get(oString) ;
      stats = [
        'axiomCount': 0,
        'classCount': ont.getClassesInSignature(true).size()
      ]
      AxiomType.TBoxAxiomTypes.each { ont.getAxioms(it, true).each { stats.axiomCount += 1 } }
      AxiomType.RBoxAxiomTypes.each { ont.getAxioms(it, true).each { stats.axiomCount += 1 } }
    }

    return stats
  }
}
