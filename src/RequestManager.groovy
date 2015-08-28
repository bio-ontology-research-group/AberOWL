package src

import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import org.semanticweb.owlapi.owllink.*;
import org.semanticweb.owlapi.util.*;
import org.semanticweb.owlapi.search.*;

import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.*
import org.apache.lucene.queryparser.simple.*
import org.apache.lucene.search.highlight.*
import org.apache.lucene.index.IndexWriterConfig.OpenMode

import java.util.concurrent.*
import java.util.timer.*
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
  def lCount = 0
  def dCount = 0
  OWLOntologyManager oManager;
  List<OWLAnnotationProperty> aProperties = new ArrayList<>();
  OWLDataFactory df = OWLManager.getOWLDataFactory() ;
  OntologyDatabase oBase = new OntologyDatabase()

  def ontologies = new ConcurrentHashMap()
  def ontologyManagers = new ConcurrentHashMap()
  def queryEngines = new ConcurrentHashMap()
  def loadStati = new ConcurrentHashMap()
  def oldOntologies = new ConcurrentHashMap()

  // Index things
  RAMDirectory index = new RAMDirectory()
  IndexSearcher searcher
          
  RequestManager(boolean reason) {
    println "Loading ontologies"
    loadOntologies();
    loadAnnotations();
    loadIndex();
    if(reason) {
      createReasoner();
    }

    // Unload old versions of ontologies
    new Timer().schedule({
      removeOldOntologies()
    } as TimerTask, 600000, 600000) // 10 minutes
  }

  void removeOldOntologies() {
    def iwc = new IndexWriterConfig(new WhitespaceAnalyzer())
    iwc.setOpenMode(OpenMode.CREATE_OR_APPEND)
    IndexWriter writer = new IndexWriter(index, iwc)
    def curTime = (int) (System.currentTimeMillis() / 1000L)

    oldOntologies.each { id, time ->
      if(curTime - time > 3600000) { // if it's older than an hour /
        ontologies.remove(id)
        ontologyManagers.remove(id)
        queryEngines.remove(id)
        loadStati.remove(id)
        writer.deleteDocuments(new Term('ontology', id))
        oldOntologies.remove(id)
      }
    }

    index.close()
  }
      
  Set<String> listOntologies() {
    return ontologies.keySet() ;
  }
      
  Set<String> queryNames(String query, String ontUri) {
    String[] fields = ['label', 'ontology']
    def oQuery = query

    //query = oQuery.toLowerCase().split().collect({ 'first_label:' + classic.QueryParser.escape(it) + '*' }).join(' AND ')
    query = oQuery.toLowerCase().split().collect({ 'label:' + classic.QueryParser.escape(it) + '*' }).join(' AND ')

    def parser
    if(ontUri && ontUri != '') {
      parser = new classic.MultiFieldQueryParser(fields, new WhitespaceAnalyzer())
      query += ' AND ontology:' + ontUri+ ' AND oldVersion:'+ false;
    } else {
      parser = new classic.QueryParser('label', new WhitespaceAnalyzer())
    }

    def fQuery = parser.parse(query)
    def hits = searcher.search(fQuery, 1000).scoreDocs
    def ret = []

    hits.each { h -> 
      def hitDoc = searcher.doc(h.doc)
      def label = hitDoc.get('label') 
      def ontology = hitDoc.get('ontology') 
      def iri = hitDoc.get('class') 
      def fLabel = hitDoc.get('first_label') 
      if(label.indexOf(' ') != -1) {
        label = "'" + label + "'"
      }
      ret.add([
        'label': label,
        'iri': iri,
        'ontology': ontology,
        'first_label': fLabel,

        // Make jquery happy
        'value': fLabel,
        'data': iri
      ])
    }

    return ret.sort { it.label.size() }
  }

  void reloadOntologyIndex(String uri, IndexWriter index,boolean isOldVersion) {
    def ont = ontologies.get(uri)
    def labels = [
      // Labels
      df.getRDFSLabel(), 
      df.getOWLAnnotationProperty(new IRI('http://www.w3.org/2004/02/skos/core#prefLabel')),
      df.getOWLAnnotationProperty(new IRI('http://purl.obolibrary.org/obo/IAO_0000111')),

      // Synonyms
      df.getOWLAnnotationProperty(new IRI('http://www.w3.org/2004/02/skos/core#altLabel')),
      df.getOWLAnnotationProperty(new IRI('http://purl.obolibrary.org/obo/IAO_0000118')),
      df.getOWLAnnotationProperty(new IRI('http://www.geneontology.org/formats/oboInOwl#hasExactSynonym')),
      df.getOWLAnnotationProperty(new IRI('http://www.geneontology.org/formats/oboInOwl#hasSynonym')),
      df.getOWLAnnotationProperty(new IRI('http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym')),
      df.getOWLAnnotationProperty(new IRI('http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym'))
    ]
    def definitions = [
      df.getOWLAnnotationProperty(new IRI('http://purl.obolibrary.org/obo/IAO_0000115')),
      df.getOWLAnnotationProperty(new IRI('http://www.w3.org/2004/02/skos/core#definition')),
      df.getOWLAnnotationProperty(new IRI('http://purl.org/dc/elements/1.1/description')),
      df.getOWLAnnotationProperty(new IRI('http://www.geneontology.org/formats/oboInOwl#hasDefinition'))
    ]

    index.deleteDocuments(new Term('ontology', uri))

    // Readd all classes for this ont
    ont.getImportsClosure().each { iOnt -> // OWLOntology
      iOnt.getClassesInSignature(true).each { iClass -> // OWLClass
        def cIRI = iClass.getIRI().toString()
        def firstLabelRun = true
        def lastFirstLabel = null
        def doc = new Document()
        //To indicate that it is a old version
        doc.add(new Field('ontology', uri, TextField.TYPE_STORED))
        doc.add(new Field('class', cIRI, TextField.TYPE_STORED))
        doc.add(new Field("oldVersion",isOldVersion.toString(), TextField.TYPE_STORED))

        def xrefs = []
        EntitySearcher.getAnnotationAssertionAxioms(iClass, iOnt).each {
          if(it.getProperty().getIRI() == new IRI('http://www.geneontology.org/formats/oboInOwl#hasDbXref')) {
            it.getAnnotations().each {
              def label = it.getValue().getLiteral().toLowerCase()
              if(!xrefs.contains(label)) {
                xrefs << label
              }
            }
          }
        }

        labels.each {
          EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
            if(annotation.getValue() instanceof OWLLiteral) {
              def val = (OWLLiteral) annotation.getValue()
              def label = val.getLiteral().toLowerCase()

              if(!xrefs.contains(label)) {
                doc.add(new Field('label', label, TextField.TYPE_STORED))
                if(firstLabelRun) {
                  lastFirstLabel = label;
                }
                if(annotation != null) {
                  lCount += 1
                }
              }
            }
          }
          if(lastFirstLabel) {
            doc.add(new Field('first_label', lastFirstLabel, TextField.TYPE_STORED))
            firstLabelRun = false
          }
        }
        definitions.each {
          EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
            if(annotation.getValue() instanceof OWLLiteral) {
              def val = (OWLLiteral) annotation.getValue()
              def label = val.getLiteral().toLowerCase()
              doc.add(new Field('definition', label, TextField.TYPE_STORED))
              if(annotation != null) {
                dCount += 1
              }
            }
          }
        }

        doc.add(new Field('label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED)) // add remainder
        if(!lastFirstLabel) {
          doc.add(new Field('first_label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED))
        }
        index.addDocument(doc)
      }

      iOnt.getObjectPropertiesInSignature(true).each { iClass ->
        def cIRI = iClass.getIRI().toString()
        def firstLabelRun = true
        def lastFirstLabel = null
        def doc = new Document()
        doc.add(new Field('ontology', uri, TextField.TYPE_STORED))
        doc.add(new Field('class', cIRI, TextField.TYPE_STORED))

        def xrefs = []
        EntitySearcher.getAnnotationAssertionAxioms(iClass, iOnt).each {
          if(it.getProperty().getIRI() == new IRI('http://www.geneontology.org/formats/oboInOwl#hasDbXref')) {
            it.getAnnotations().each {
              def label = it.getValue().getLiteral().toLowerCase()
              if(!xrefs.contains(label)) {
                xrefs << label
              }
            }
          }
        }

        labels.each {
          EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
            if(annotation.getValue() instanceof OWLLiteral) {
              def val = (OWLLiteral) annotation.getValue()
              def label = val.getLiteral().toLowerCase()
              
              if(!xrefs.contains(label)) {
                doc.add(new Field('label', label, TextField.TYPE_STORED))
                if(firstLabelRun) {
                  lastFirstLabel = label;
                }
                if(annotation != null) {
                  lCount += 1
                }
              }
            }
          }
          
          if(lastFirstLabel) {
            doc.add(new Field('first_label', lastFirstLabel, TextField.TYPE_STORED))
            firstLabelRun = false
          }
        }
        definitions.each {
          EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
            if(annotation.getValue() instanceof OWLLiteral) {
              def val = (OWLLiteral) annotation.getValue()
              def label = val.getLiteral().toLowerCase()
              
              doc.add(new Field('definition', label, TextField.TYPE_STORED))
              if(annotation != null) {
                dCount += 1
              }
            }
          }
        }

        doc.add(new Field('label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED)) // add remainder
        if(!lastFirstLabel) {
          doc.add(new Field('first_label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED))
        }
        index.addDocument(doc)
      }
    }
  }

  void loadIndex() {
    loadIndex('',false)
  }

// Adding a parameter to Load Index to indicate that it is an old version
  void loadIndex(String ontology,boolean isOldVersion) {
      //If the ontologie is new version then is not indexed.
      def iwc = new IndexWriterConfig(new WhitespaceAnalyzer())
      iwc.setOpenMode(OpenMode.CREATE_OR_APPEND)
      IndexWriter writer = new IndexWriter(index, iwc)

      if (ontology == '') {
        for (String uri : ontologies.keySet()) {
          reloadOntologyIndex(uri, writer,isOldVersion)
        }
      } else {
        reloadOntologyIndex(ontology, writer,isOldVersion)
      }
      writer.close()
      searcher = new IndexSearcher(DirectoryReader.open(index))
  }

  /**
   * Load a new or replace an existing ontology
   *
   * @param name corresponding to name of the ontology in the database
   */
  void reloadOntology(String name,int version) {
    def oRec = oBase.getOntology(name, false)
    if(!oRec) {
      return
    }
    if(oRec.lastSubDate == 0) {
      return
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
      if((version>=0)&&(version<oRec.submissions.size())) {
        //I am not proud of this, but it is the only way to order the versions.
        //I have preferred to not do many changes on the code
        def list = oRec.submissions.keySet().sort();
        //Firstly, It is necessary to get the timestamp
        def timestamp = list.get(version);
        def fSource = new FileDocumentSource(new File('onts/' + oRec.submissions.get(timestamp)))
        def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config)
        //Load the different version of the ontology with a different key
        def newId = oRec.id + '_' + version;
        ontologies.put(newId, ontology)
        ontologyManagers.put(newId, lManager)
        oldOntologies.put(newId, (int) (System.currentTimeMillis() / 1000L))

        println "Updated ontology: " + newId + " version: "+version

        reloadOntologyAnnotations(newId)
        //loadIndex(name)
        loadIndex(newId,newO)

      }else{//In other case the actual ontology will be updated.
        def fSource = new FileDocumentSource(new File('onts/'+oRec.submissions[oRec.lastSubDate.toString()]))
        def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config)
        ontologies.put(oRec.id, ontology)
        ontologyManagers.put(oRec.id, lManager)
        println "Updated ontology: " + oRec.id

        reloadOntologyAnnotations(oRec.id)
        //loadIndex(name)
        loadIndex(name,newO)

      }

      if(newO) {
        loadedOntologies++
      }
      //reloadOntologyAnnotations(oRec.id)
      //loadIndex(name)
      //loadIndex(name,newO)

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
   * Create the ontology manager and load it with the given ontology.
   *
   * @throws OWLOntologyCreationException, IOException
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
          loadStati.put(oRec.id, 'loaded')
        } catch (OWLOntologyAlreadyExistsException E) {
          if(oRec && oRec.id) {
            println 'DUPLICATE ' + oRec.id
          }
        } catch (OWLOntologyInputSourceException e) {
          println "File not found for " + oRec.id
          if(oRec && oRec.id) {
            loadStati.put(oRec.id, 'unloadable')
          }
          noFileError++
        } catch (IOException e) {
          println "Can't load external import for " + oRec.id 
          if(oRec && oRec.id) {
            loadStati.put(oRec.id, 'unloadable')
          }
          importError++
        } catch(OWLOntologyCreationIOException e) {
          println "Failed to load imports for " + oRec.id
          if(oRec && oRec.id) {
            loadStati.put(oRec.id, 'unloadable')
          }
          importError++
        } catch(UnparsableOntologyException e) {
          println "Failed to parse ontology " + oRec.id
          e.printStackTrace()
          if(oRec && oRec.id) {
            loadStati.put(oRec.id, 'unloadable')
          }
          parseError++
        } catch(UnloadableImportException e) {
          println "Failed to load imports for " + oRec.id
          if(oRec && oRec.id) {
            loadStati.put(oRec.id, 'unloadable')
          }
          importError++
        } catch (Exception E) {
          println oRec.id + ' other'
          if(oRec && oRec.id) {
            loadStati.put(oRec.id, 'unloadable')
          }
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

      def sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);
      this.queryEngines.put(k, new QueryEngine(oReasoner, sForm));

      println "Successfully classified " + k + " ["+this.queryEngines.size()+"/"+ontologies.size()+"]"
      loadStati.put(k, 'classified')
    } catch(InconsistentOntologyException e) {
      println "inconsistent ontology " + k
      e.printStackTrace()
      loadStati.put(k, 'inconsistent')
    } catch (java.lang.IndexOutOfBoundsException e) {
      println "Failed " + k
      e.printStackTrace()
      loadStati.put(k, 'unloadable')
    } catch (Exception e) {
      println "Failed " + k
      e.printStackTrace()
      loadStati.put(k, 'unloadable')
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
        "definition": null,
        "deprecated": false
      ];

      def bq = new BooleanQuery()
      bq.add(new TermQuery(new Term('class', c.getIRI().toString())), BooleanClause.Occur.MUST);
      bq.add(new TermQuery(new Term('ontology', uri.toString())), BooleanClause.Occur.MUST);

      //def dResult = searcher.search(bq, 1).scoreDocs[0]
      def scoreDocs = searcher.search(bq, 1).scoreDocs
      if((scoreDocs!=null)&&(scoreDocs.length>0)) {
        def dResult = scoreDocs[0]
        def hitDoc = searcher.doc(dResult.doc)
        //def output = [:]
        hitDoc.each {
          info['label'] = hitDoc.get('first_label')
        }
      }
      for (OWLAnnotation annotation : EntitySearcher.getAnnotations(c, o)) {
        if(annotation.isDeprecatedIRIAnnotation()) {
          info['deprecated'] = true
        }
      }

      for (OWLAnnotation annotation : EntitySearcher.getAnnotations(c, o, df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115")))) {
        if (annotation.getValue() instanceof OWLLiteral) {
          OWLLiteral val = (OWLLiteral) annotation.getValue();
          info['definition'] = val.getLiteral() ;
        }
      }

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
  Set runQuery(String mOwlQuery, String type, String ontUri,int version, boolean direct, boolean labels) {
    def start = System.currentTimeMillis()

    type = type.toLowerCase()
    def requestType
    switch(type) {
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
      Iterator<String> it = queryEngines.keySet().iterator() ;
      while (it.hasNext()) {
        String oListString = it.next() ;
        QueryEngine queryEngine = queryEngines.get(oListString) ;
        OWLOntology ontology = ontologies.get(oListString) ;
          Set<OWLClass> resultSet = queryEngine.getClasses(mOwlQuery, requestType, direct, labels)
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
        def sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);
        Set<OWLClass> resultSet = new QueryEngine(oReasoner, sForm).getClasses(mOwlQuery, requestType, direct, labels) ;
        resultSet.remove(df.getOWLNothing()) ;
        resultSet.remove(df.getOWLThing()) ;
        classes.addAll(classes2info(resultSet, ontology, ontUri)) ;
      } catch (OWLOntologyCreationException E) {
        E.printStackTrace();
      }
    } else { // query one single ontology
      QueryEngine queryEngine = queryEngines.get(ontUri);
      def vOntUri = ontUri
      if(version>=0) {
        vOntUri = ontUri+"_"+version;
      }

      if(!ontologies.containsKey(vOntUri)) {
        reloadOntology(ontUri, version)
      }

      OWLOntology ontology = ontologies.get(ontUri)
      println(String.valueOf(queryEngine)+"-->"+mOwlQuery+"-->"+requestType+"-->"+direct+"-->"+labels);
      Set<OWLClass> resultSet = queryEngine.getClasses(mOwlQuery, requestType, direct, labels)
      resultSet.remove(df.getOWLNothing())
      resultSet.remove(df.getOWLThing())
      classes.addAll(classes2info(resultSet, ontology, ontUri))
    }

    def end = System.currentTimeMillis()
    println(mOwlQuery + ' ' + type + ' took: ' + (end - start) + 'ms')

    return classes;
  }

  Set runQuery(String mOwlQuery, String type, String ontUri) {
    return runQuery(mOwlQuery, type, ontUri, false)
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
        'otherError': otherError,
        'lCount': lCount,
        'dCount': dCount
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
  /**
   * Gets the object properties from the ontology given
   * oString This paramater represents the id of the ontology.
   */
  HashMap getObjectProperties(String oString){
    HashMap objectProperties = new HashMap<String,String>();
    if((oString!=null)&&(oString.length()>0)){
      if(ontologies.containsKey(oString)) {
        OWLOntology ont = ontologies.get(oString);
        Iterator <OWLObjectProperty> it = ont.getObjectPropertiesInSignature(true).iterator();
        OWLObjectProperty property;
        while(it.hasNext()) {
          property = it.next();
          Iterator<OWLAnnotationAssertionAxiom> jt = EntitySearcher.getAnnotationAssertionAxioms(property, ont).iterator();
          OWLAnnotationAssertionAxiom axiom;
          while(jt.hasNext()) {
            axiom = jt.next();
            if (axiom.getProperty().isLabel()) {
              OWLLiteral value = (OWLLiteral) axiom.getValue();
              objectProperties.put(value.getLiteral().toString(),"<"+axiom.getSubject().toString()+">");
            }
          }
        }
      }
    }
    return objectProperties;
  }

}
