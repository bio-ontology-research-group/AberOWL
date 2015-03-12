import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import org.semanticweb.owlapi.owllink.*;
import java.util.concurrent.*
import uk.ac.aber.lus11.sparqowlapi.util.*
import groovyx.gpars.ParallelEnhancer
import groovyx.gpars.GParsPool

class RequestManager {
  int k = 500 ; // for SuggestTree
  int maxLength = 10000 ; // for SuggestTree
  int loadedOntologies = 0;
  int attemptedOntologies = 0;
  int noFileError = 0;
  int importError = 0;
  int parseError = 0;
  int otherError = 0;
  OWLOntologyManager oManager;
  SuggestTree allLabels = new SuggestTree(k, new HashMap<String, Integer>());
  Map<String, Set<String>> allLabels2id = new HashMap<>() ;
  Map<String, SuggestTree> labels = new LinkedHashMap<>(); 
  Map<String, Map<String, Set<String>>> labels2id = new LinkedHashMap<>(); // ontUri -> label -> OWLClassIRI
  List<OWLAnnotationProperty> aProperties = new ArrayList<>();
  OWLDataFactory df = OWLManager.getOWLDataFactory() ;
  OntologyDatabase oBase = new OntologyDatabase()

  def ontologies = new ConcurrentHashMap();
  def ontologyManagers = new ConcurrentHashMap();
  def queryEngines = new ConcurrentHashMap();
          
  RequestManager(boolean reason) {
    println "Loading ontologies"
    loadOntologies();
    loadAnnotations();
    loadLabels();
    if(reason) {
      createReasoner();
    }
    println loadedOntologies
    println ontologies.size()
    getStats()
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
      SuggestTree.Node n = tree.autocompleteSuggestionsFor(query) ;
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

  void loadLabels() {
    for (String uri : ontologies.keySet()) {
      labels.put(uri, new SuggestTree(k, new HashMap<String, Integer>())) ;
      labels2id.put(uri, new LinkedHashMap<String, Set<String>>()) ;
      OWLOntology ont = ontologies.get(uri) ;
      for (OWLOntology o : ont.getImportsClosure()) {
        for (OWLClass c : o.getClassesInSignature(true)) {
          String classIRI = c.getIRI().toString() ;
          for (OWLAnnotation annotation : c.getAnnotations(o, df.getRDFSLabel())) {
            if (annotation.getValue() instanceof OWLLiteral) {
              OWLLiteral val = (OWLLiteral) annotation.getValue();
              String label = val.getLiteral() ;
              label = label.toLowerCase() ;
              try {
                allLabels.insert(label, maxLength - label.length()) ;
              } catch (Exception E) {}
              if (allLabels2id.get(label) == null) {
                allLabels2id.put(label, new LinkedHashSet<String>()) ;
              }
              allLabels2id.get(label).add(c.getIRI().toString()) ;
              if (labels2id.get(uri).get(label) == null) {
                labels2id.get(uri).put(label, new LinkedHashSet<String>()) ;
              }
              labels2id.get(uri).get(label).add(c.getIRI().toString()) ;
              try {
                labels.get(uri).insert(label, maxLength - label.length()) ;
              } catch (Exception E) {}
            }
          }
        }                                                                                                                                          
        for (OWLObjectProperty c : o.getObjectPropertiesInSignature(true)) {
          String classIRI = c.getIRI().toString() ;
          for (OWLAnnotation annotation : c.getAnnotations(o, df.getRDFSLabel())) {
            if (annotation.getValue() instanceof OWLLiteral) {
              OWLLiteral val = (OWLLiteral) annotation.getValue();
              String label = val.getLiteral() ;
              label = label.toLowerCase() ;
              try {
                allLabels.insert(label, maxLength - label.length()) ;
              } catch (Exception E) {}
              if (allLabels2id.get(label) == null) {
                allLabels2id.put(label, new LinkedHashSet<String>()) ;
              }
              allLabels2id.get(label).add(c.getIRI().toString()) ;
                              
              try {
                labels.get(uri).insert(label, maxLength - label.length()) ;
              } catch (Exception E) {}
              if (labels2id.get(uri).get(label) == null) {
                labels2id.get(uri).put(label, new LinkedHashSet<String>()) ;
              }
              labels2id.get(uri).get(label).add(c.getIRI().toString()) ;
            }
          }
        }                                                                                                                                          
      }                                                                                                                                            
    }
  }

  /**
   * Create the ontology manager and load it with the given ontology.
   * 
   * @param ontologyLink URI to the OWL ontology to be queried.
   * @throws OWLOntologyCreationException 
   */
  void loadOntologies() throws OWLOntologyCreationException, IOException {
    GParsPool.withPool {
      this.oBase.ontologies.eachParallel { k, oRec ->
      if(attemptedOntologies > 1) {
      return;
      }
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
       //   println "Successfully loaded " + oRec.id

          loadedOntologies++
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
        }
      }
    }
    println "REASONED"
  }
      
  /**
   * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider.
   */
  void loadAnnotations() {
    println "Making annotations" 
    for (String id : ontologyManagers.keySet()) { // For some reason .each doesn't work here
      OWLDataFactory factory = ontologyManagers.get(id).getOWLDataFactory();
      OWLAnnotationProperty rdfsLabel = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()) ;                                                       
      aProperties.add(rdfsLabel);
      aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym"))) ;
      aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym"))) ;
      aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"))) ;
    }
  }

  Set classes2info(Set<OWLClass> classes, OWLOntology o, String uri) {
    ArrayList result = new ArrayList<HashMap>();
    for(def c : classes) {
      def info = [
        "owlClass": c.toString(),
        "classURI": c.getIRI().toString(),
        "ontologyURI": uri.toString(),
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
