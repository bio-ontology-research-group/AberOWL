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

import com.google.common.collect.*

class RequestManager {
  private static final WEB_ROOT = 'http://aber-owl.net/'
  private static final ELK_THREADS = "8"
  private static final MAX_UNSATISFIABLE_CLASSES = 500

  private static final MAX_QUERY_RESULTS = 25000
  // max classes returned by query; to prevent DoS; TODO: replace by paging!

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
  OWLDataFactory df = OWLManager.getOWLDataFactory();
  OntologyDatabase oBase = new OntologyDatabase()


  def ontologies = new ConcurrentHashMap()
  def ontologyManagers = new ConcurrentHashMap()
  def queryEngines = new ConcurrentHashMap()
  def loadStati = new ConcurrentHashMap()
  def oldOntologies = new ConcurrentHashMap()

  // Index things
  RAMDirectory index = new RAMDirectory()
  IndexSearcher searcher
  IndexWriterConfig iwc
  IndexWriter writer

  RequestManager(boolean reason) {
    this.iwc = new IndexWriterConfig(new WhitespaceAnalyzer())
    iwc.setOpenMode(OpenMode.CREATE_OR_APPEND)
    this.writer = new IndexWriter(index, iwc)

    //    println "Loading ontologies"
    loadOntologies();
    loadAnnotations();
    if (reason) {
      createReasoner();
    }
    loadIndex();

    println "Loading of ontologies finished; AberOWL is ready for service."

    // Unload old versions of ontologies
    // remove temporarily...
    /*
    new Timer().schedule({
      removeOldOntologies()
    } as TimerTask, 600000, 600000) // 10 minutes
    */
  }

  void removeOldOntologies() {
    //    def iwc = new IndexWriterConfig(new WhitespaceAnalyzer())
    //    iwc.setOpenMode(OpenMode.CREATE_OR_APPEND)
    //    IndexWriter writer = new IndexWriter(index, iwc)
    def curTime = (int) (System.currentTimeMillis() / 1000L)

    oldOntologies.each { id, time ->
      if (curTime - time > 3600000) { // if it's older than an hour /
        ontologies.remove(id)
        ontologyManagers.remove(id)
        queryEngines.remove(id)
        loadStati.remove(id)
        writer.deleteDocuments(new Term('ontology', id))
        oldOntologies.remove(id)
      }
    }

    writer.commit()
  }

  Set<String> listOntologies() {
    return ontologies.keySet();
  }

  List<String> queryNames(String query, String ontUri) {
    String[] fields = ['label', 'ontology', 'oboid', 'definition', 'synonym', 'AberOWL-catch-all', 'AberOWL-sublass', 'AberOWL-equivalent']
    // List<String> fList = []
    // MultiFields.getFields(DirectoryReader.open(index))?.each {
    //   def s = it.toString()?.toLowerCase()
    //   if (s.contains("label") || s.contains("ontology") || s.contains("definition") || s.contains("description") || s.contains("oboid") || s.contains("synonym")) {
    // 	fList << it
    //   }
    // }
    //    String[] allFields = fList.toArray(new String[fList.size()])
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

    //query = oQuery.toLowerCase().split().collect({ 'first_label:' + classic.QueryParser.escape(it) + '*' }).join(' AND ')
    //    query = oQuery.toLowerCase().split().collect({ classic.QueryParser.escape(it) + '*' }).join(' AND ')
    //    query = oQuery.toLowerCase().split().collect({ classic.QueryParser.escape(it) +'*' }).join(' AND ')


    def parser = new classic.MultiFieldQueryParser(fields, new WhitespaceAnalyzer(), boostVals)
    def queryList = []
    oQuery.toLowerCase().split().each {
      def ll = new LinkedHashSet()
      boostVals.each { l, v ->
        ll.add(parser.parse(l + ":" + classic.QueryParser.escape(it) + "^" + v))
      }
      queryList << new DisjunctionMaxQuery(ll, 0.1)
    }


    if (ontUri && ontUri != '') {
      queryList << parser.parse('ontology:' + ontUri)
      queryList << parser.parse('oldVersion:' + false)
    } else {
      queryList << parser.parse('oldVersion:' + false)
    }

    def fQuery = new BooleanQuery()
    queryList.each { q ->
      fQuery.add(q, BooleanClause.Occur.MUST)
    }
    //    def fQuery = parser.parse(query)
    def hits = searcher.search(fQuery, 1000, Sort.RELEVANCE, true, true).scoreDocs
    def ret = []

    hits?.each { h ->
      //      println searcher.explain(fQuery, h.doc).toString()
      def hitDoc = searcher.doc(h.doc)
      def label = hitDoc.get('label')
      def ontology = hitDoc.get('ontology')
      def iri = hitDoc.get('class')
      def fLabel = hitDoc.get('first_label')
      def definition = hitDoc.get('definition')
      if (label && label.indexOf(' ') != -1) {
        label = "'" + label + "'"
      }
      ret.add([
              'label'     : label,
              'iri'       : iri,
              'ontology'  : ontology,
              'first_label': fLabel,
              'definition': definition,

              // Make jquery happy
              'value'     : fLabel,
              'data'      : iri
      ])
    }

    return ret
  }

  Set<String> queryOntologies(String query) {
    if (query) {
      String[] fields = ['name', 'lontology', 'description']
      def oQuery = classic.QueryParser.escape(query.toLowerCase())

      query = oQuery.toLowerCase().split().collect({ classic.QueryParser.escape(it) + '*' }).join(' AND ')
      
      def parser
      parser = new classic.MultiFieldQueryParser(fields, new WhitespaceAnalyzer())
      query += ' AND type:ontology'
      
      //println query
      
      def fQuery = parser.parse(query)
      //println fQuery
      def hits = searcher.search(fQuery, 1000).scoreDocs
      def ret = []
      
      hits.each { h ->
	def hitDoc = searcher.doc(h.doc)
	def name = hitDoc.get('name')
	def ontology = hitDoc.get('ontology')
	def description = hitDoc.get('description')
	ret.add([
		  'name': name,
		 'uri' : ontology,
		 'description': description,
		])
      }
      
      return ret.sort { it.name.size() }
    } else {
      return []
    }
  }


  void reloadOntologyIndex(String uri, IndexWriter index, boolean isOldVersion) {
    def ont = ontologies.get(uri)
    def oReasoner = queryEngines.get(uri)?.getoReasoner()
    def manager = ont.getOWLOntologyManager()
    def labels = [
            // Labels
            df.getRDFSLabel(),
            df.getOWLAnnotationProperty(new IRI('http://www.w3.org/2004/02/skos/core#prefLabel')),
            df.getOWLAnnotationProperty(new IRI('http://purl.obolibrary.org/obo/IAO_0000111'))
    ]
    def synonyms = [
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

    // Add record for the ontology itself
    println 'getting ' + uri
    def info = oBase.getOntology(uri)
    println 'got ' + info.id
    def oDoc = new Document()
    // Storing seperate lower case versions of the field seems dumb
    oDoc.add(new Field('ontology', uri, TextField.TYPE_STORED))
    oDoc.add(new Field('lontology', uri.toLowerCase(), TextField.TYPE_STORED))
    oDoc.add(new Field("oldVersion", isOldVersion.toString(), TextField.TYPE_STORED))
    oDoc.add(new Field('type', 'ontology', TextField.TYPE_STORED))
    oDoc.add(new Field('name', info.name, TextField.TYPE_STORED))
    oDoc.add(new Field('lname', info.name.toLowerCase(), TextField.TYPE_STORED))
    if (info.description) {
      oDoc.add(new Field('ldescription', info.description.toLowerCase(), TextField.TYPE_STORED))
      oDoc.add(new Field('description', info.description, TextField.TYPE_STORED))
    }
    index.addDocument(oDoc)

    // Readd all classes for this ont

    OWLOntologyImportsClosureSetProvider mp = new OWLOntologyImportsClosureSetProvider(manager, ont)
    OWLOntologyMerger merger = new OWLOntologyMerger(mp, false)
    def iOnt = merger.createMergedOntology(manager, IRI.create("http://test.owl"))

    // set up the renderer for the axioms
    def sProvider = new AnnotationValueShortFormProvider(
            Collections.singletonList(df.getRDFSLabel()),
            Collections.<OWLAnnotationProperty, List<String>> emptyMap(),
            manager);
    def manSyntaxRenderer = new AberOWLSyntaxRendererImpl()
    manSyntaxRenderer.setShortFormProvider(sProvider)

    iOnt.getClassesInSignature(true).each { iClass -> // OWLClass
      def cIRI = iClass.getIRI().toString()
      def firstLabelRun = true
      def lastFirstLabel = null
      def doc = new Document()
      Field f = null
      //To indicate that it is a old version
      f = new Field('ontology', uri, TextField.TYPE_STORED)
      doc.add(f)
      // make ontologies searchable
      f = new Field('AberOWL-catch-all', uri.toLowerCase(), TextField.TYPE_STORED)
      doc.add(f)
      f = new Field('type', 'class', TextField.TYPE_STORED)
      doc.add(f)
      f = new Field('class', cIRI, TextField.TYPE_STORED)
      doc.add(f)
      f = new Field("oldVersion", isOldVersion.toString(), TextField.TYPE_STORED)
      doc.add(f)

      /* check if this class is a leaf node in the taxonomy if (oReasoner && oReasoner.getSubClasses(iClass, true).isBottomSingleton()) { f = new Field("isLeafNode","true", TextField.TYPE_STORED)
      } else {
	f = new Field("isLeafNode","false", TextField.TYPE_STORED)
      }
      */
      //      doc.add(f)

      /* get the axioms */
      EntitySearcher.getSuperClasses(iClass, iOnt).each { cExpr -> // OWL Class Expression
        //if (! cExpr.isClassExpressionLiteral()) {
        f = new Field('AberOWL-subclass', manSyntaxRenderer.render(cExpr), TextField.TYPE_STORED)
        doc.add(f)
        f = new Field('AberOWL-catch-all', manSyntaxRenderer.render(cExpr), TextField.TYPE_STORED)
        doc.add(f)
        //}
      }
      EntitySearcher.getEquivalentClasses(iClass, iOnt).each { cExpr -> // OWL Class Expression
        //if (! cExpr.isClassExpressionLiteral()) {
        f = new Field('AberOWL-equivalent', manSyntaxRenderer.render(cExpr), TextField.TYPE_STORED)
        doc.add(f)
        f = new Field('AberOWL-catch-all', manSyntaxRenderer.render(cExpr), TextField.TYPE_STORED)
        doc.add(f)
        //}
      }


      def deprecated = false
      def annoMap = [:].withDefault { new TreeSet() }
      EntitySearcher.getAnnotations(iClass, iOnt).each { anno ->
        if (anno.isDeprecatedIRIAnnotation()) {
          deprecated = true
        }
        def aProp = anno.getProperty()
        if (!(aProp in labels || aProp in definitions || aProp in synonyms)) {
          if (anno.getValue() instanceof OWLLiteral) {
            def aVal = anno.getValue().getLiteral()?.toLowerCase()
            def aLabels = []
            if (EntitySearcher.getAnnotations(aProp, iOnt, df.getRDFSLabel()).size() > 0) {
              EntitySearcher.getAnnotations(aProp, iOnt, df.getRDFSLabel()).each { l ->
                def lab = l.getValue().getLiteral().toLowerCase()
                annoMap[lab].add(aVal)
              }
            } else {
              annoMap[aProp.toString()?.replaceAll("<", "")?.replaceAll(">", "")].add(aVal)
            }
          }
        }
      }
      annoMap.each { k, v ->
        v.each { val ->
          f = new Field(k, val, TextField.TYPE_STORED)
          doc.add(f)
          f = new Field("AberOWL-catch-all", val, TextField.TYPE_STORED)
          doc.add(f)
        }
      }

      // generate OBO-style ID for the index
      def oboId = ""
      if (cIRI.lastIndexOf("/") > -1) {
        oboId = cIRI.substring(cIRI.lastIndexOf("/") + 1)
      }
      if (cIRI.lastIndexOf("#") > -1) {
        oboId = cIRI.substring(cIRI.lastIndexOf("#") + 1)
      }
      if (oboId.length() > 0) {
        oboId = oboId.replaceAll("_", ":").toLowerCase()
        f = new Field('oboid', oboId, StringField.TYPE_STORED)
        doc.add(f)
      }


      def xrefs = []
      /* this was a workaround, bug should be fixed now in the ontologies
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
      */
      synonyms.each {
        EntitySearcher.getAnnotationAssertionAxioms(iClass, iOnt).each { ax ->
          if (ax.getProperty() == it) {
            //	EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
            if (ax.getValue() instanceof OWLLiteral) {
              def val = (OWLLiteral) ax.getValue()
              def label = val.getLiteral().toLowerCase()

              f = new Field('synonym', label, TextField.TYPE_STORED)
              doc.add(f)
            }
          }
        }
      }
      def hasLabel = false
      labels.each {
        EntitySearcher.getAnnotationAssertionAxioms(iClass, iOnt).each { ax ->
          if (ax.getProperty() == it) {
            //	EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
            if (ax.getValue() instanceof OWLLiteral) {
              def val = (OWLLiteral) ax.getValue()
              def label = val.getLiteral().toLowerCase()
              if (label) {
                f = new Field('label', label, TextField.TYPE_STORED)
                doc.add(f)
                hasLabel = true
                if (firstLabelRun) {
                  lastFirstLabel = label;
                }
              }
            }
          }
        }
        if (lastFirstLabel) {
          f = new Field('first_label', lastFirstLabel, TextField.TYPE_STORED)
          doc.add(f)
          firstLabelRun = false
        }
      }
      definitions.each {
        EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
          if (annotation.getValue() instanceof OWLLiteral) {
            def val = (OWLLiteral) annotation.getValue()
            def label = val.getLiteral().toLowerCase()
            f = new Field('definition', label, TextField.TYPE_STORED)
            doc.add(f)
            if (annotation != null) {
              dCount += 1
            }
          }
        }
      }
      if (!hasLabel) {
        f = new Field('label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED)
        doc.add(f) // add remainder
      }
      if (!lastFirstLabel) {
        f = new Field('first_label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED)
        doc.add(f)
      }
      if (!deprecated) {
        index.addDocument(doc)
      }
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
        if (it.getProperty().getIRI() == new IRI('http://www.geneontology.org/formats/oboInOwl#hasDbXref')) {
          it.getAnnotations().each {
            def label = it.getValue().getLiteral().toLowerCase()
            if (!xrefs.contains(label)) {
              xrefs << label
            }
          }
        }
      }

      def annoMap = [:].withDefault { new TreeSet() }
      EntitySearcher.getAnnotations(iClass, iOnt).each { anno ->
        def aProp = anno.getProperty()
        if (anno.getValue() instanceof OWLLiteral) {
          def aVal = anno.getValue().getLiteral()?.toLowerCase()
          def aLabels = []
          if (EntitySearcher.getAnnotations(aProp, iOnt, df.getRDFSLabel()).size() > 0) {
            EntitySearcher.getAnnotations(aProp, iOnt, df.getRDFSLabel()).each { l ->
              def lab = l.getValue().getLiteral().toLowerCase()
              annoMap[lab].add(aVal)
            }
          } else {
            annoMap[aProp.toString()].add(aVal)
          }
        }
      }

      annoMap.each { k, v ->
        v.each { val ->
          doc.add(new Field(k, val, TextField.TYPE_STORED))
        }
      }

      labels.each {
        EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
          if (annotation.getValue() instanceof OWLLiteral) {
            def val = (OWLLiteral) annotation.getValue()
            def label = val.getLiteral().toLowerCase()

            if (!xrefs.contains(label)) {
              doc.add(new Field('label', label, TextField.TYPE_STORED))
              if (firstLabelRun) {
                lastFirstLabel = label;
              }
              if (annotation != null) {
                lCount += 1
              }
            }
          }
        }

        if (lastFirstLabel) {
          doc.add(new Field('first_label', lastFirstLabel, TextField.TYPE_STORED))
          firstLabelRun = false
        }
      }
      definitions.each {
        EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
          if (annotation.getValue() instanceof OWLLiteral) {
            def val = (OWLLiteral) annotation.getValue()
            def label = val.getLiteral().toLowerCase()

            doc.add(new Field('definition', label, TextField.TYPE_STORED))
            if (annotation != null) {
              dCount += 1
            }
          }
        }
      }

      doc.add(new Field('label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED)) // add remainder
      if (!lastFirstLabel) {
        doc.add(new Field('first_label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED))
      }
      index.addDocument(doc)
    }
  }

  void loadIndex() {
    loadIndex('', false)
  }

  // Adding a parameter to Load Index to indicate that it is an old version
  void loadIndex(String ontology, boolean isOldVersion) {
    //If the ontology is new version then is not indexed.
    //    def iwc = new IndexWriterConfig(new WhitespaceAnalyzer())
    //    iwc.setOpenMode(OpenMode.CREATE_OR_APPEND)
    //    IndexWriter writer = new IndexWriter(index, iwc)

    if (ontology == '') {
      println "Loading index"
      GParsPool.withPool {
        ontologies.keySet().eachParallel { uri ->
          reloadOntologyIndex(uri, writer, isOldVersion)
        }
      }
    } else {
      reloadOntologyIndex(ontology, writer, isOldVersion)
    }
    writer.commit()
    searcher = new IndexSearcher(DirectoryReader.open(index))
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
      if ((version >= 0) && (version < oRec.submissions.size())) {
	// do nothing
	
	/*

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
        oldOntologies.put(newId, (int) (System.currentTimeMillis() / 1000L)) //

        println "Updated ontology: " + newId + " version: " + version

        reloadOntologyAnnotations(newId)
        //loadIndex(name)
        loadIndex(newId, newO)
	*/
      } else { //In other case the actual ontology will be updated.
        println 'trying to update current version of ontology'
        def fSource = new FileDocumentSource(new File('onts/' + oRec.submissions[oRec.lastSubDate.toString()]))
        def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config)
        ontologies.put(oRec.id, ontology)
        ontologyManagers.put(oRec.id, lManager)
        println "Updated ontology: " + oRec.id

        reloadOntologyAnnotations(oRec.id)
        //loadIndex(name)
        loadIndex(name, newO)
      }

      if (newO) {
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

      OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
      // May be replaced with any reasoner using the standard interface

      createOntologyReasoner(oRec.id, reasonerFactory, preferredLanguageMap)
    } catch (OWLOntologyInputSourceException e) {
      println "input source exception for " + oRec.id
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
    GParsPool.withPool(23) { p ->
      pool = p
      def allOnts = oBase.allOntologies()
      allOnts.eachParallel { oRec ->
        attemptedOntologies++
        try {
          if (oRec.lastSubDate == 0) {
            return;
          }
          println "Loading " + oRec.id + " [" + loadedOntologies + "/" + allOnts.size() + "]"
          OWLOntologyManager lManager = OWLManager.createOWLOntologyManager();
          OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration();
          config.setFollowRedirects(true);
          config = config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT);
          def fSource = new FileDocumentSource(new File('onts/' + oRec.submissions[oRec.lastSubDate.toString()]))
          def ontology = lManager.loadOntologyFromOntologyDocument(fSource, config);
          ontologies.put(oRec.id, ontology)
          ontologyManagers.put(oRec.id, lManager)

          loadedOntologies++
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
          noFileError++
        } catch (IOException e) {
          println "Can't load external import for " + oRec.id
          if (oRec && oRec.id) {
            loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
          }
          importError++
        } catch (OWLOntologyCreationIOException e) {
          println "Failed to load imports for " + oRec.id
          if (oRec && oRec.id) {
            loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
          }
          importError++
        } catch (UnparsableOntologyException e) {
          println "Failed to parse ontology " + oRec.id
          //          e.printStackTrace()
          if (oRec && oRec.id) {
            loadStati.put(oRec.id, ['status': 'unloadable', 'message': e.getMessage()])
          }
          parseError++
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
          otherError++
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
        println "Successfully classified " + k + " [" + this.queryEngines.size() + "/" + ontologies.size() + "]"
        this.queryEngines[k] = new QueryEngine(oReasoner, sForm)
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
    GParsPool.withPool {
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
    for (def c : classes) {
      def info = [
              "owlClass"  : c.toString(),
              "classURI"  : c.getIRI().toString(),
              "ontologyURI": uri.toString(),
              "remainder" : c.getIRI().getFragment(),
              "label"     : null,
              "definition": null,
              "deprecated": false
      ];

      def bq = new BooleanQuery()
      bq.add(new TermQuery(new Term('class', c.getIRI().toString())), BooleanClause.Occur.MUST);
      bq.add(new TermQuery(new Term('ontology', uri.toString())), BooleanClause.Occur.MUST);

      for (OWLAnnotation annotation : EntitySearcher.getAnnotations(c, o)) {
        if (annotation.isDeprecatedIRIAnnotation()) {
          info['deprecated'] = true
        }
      }
      if (!info['deprecated']) { // ignore all deprecated classes! TODO: trigger this by query flag

        def scoreDocs = searcher.search(bq, 1).scoreDocs
        if ((scoreDocs != null) && (scoreDocs.length > 0)) {
          def dResult = scoreDocs[0]
          def hitDoc = searcher.doc(dResult.doc)
          hitDoc.each {
            info['label'] = hitDoc.get('first_label')
          }
          hitDoc.getFields().each { field ->
            def fName = field.name()
            if (fName != "AberOWL-catch-all") {
              info[fName] = new LinkedHashSet()
              hitDoc.getValues(fName).each { fVal ->
                info[fName].add(fVal)
              }
            }
          }
          info['definition'] = hitDoc.get('definition')
        }

        /*
          for (OWLAnnotation annotation : EntitySearcher.getAnnotations(c, o, df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115")))) {
          if (annotation.getValue() instanceof OWLLiteral) {
          OWLLiteral val = (OWLLiteral) annotation.getValue();
          info['definition'] = val.getLiteral() ;
          }
          }
        */
        if (info['label'] == null) { // add but make deprecated
          info['label'] = info['remainder']
          info['deprecated'] = true
        }
        if (info['label'] != null) {
          result.add(info);
        }
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
      /* TODO: add paging */
      while (it.hasNext() && classes.size() < MAX_QUERY_RESULTS) {
        String oListString = it.next();
        QueryEngine queryEngine = queryEngines.get(oListString);
        OWLOntology ontology = ontologies.get(oListString);
        Set resultSet = Sets.newHashSet(Iterables.limit(queryEngine.getClasses(mOwlQuery, requestType, direct, labels), MAX_QUERY_RESULTS))
        resultSet.remove(df.getOWLNothing());
        resultSet.remove(df.getOWLThing());
        classes.addAll(classes2info(resultSet, ontology, oListString));
      }
    } else if (queryEngines.get(ontUri) == null) { // download the ontology and query
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
      def vOntUri = ontUri
      if (version >= 0) {
        vOntUri = ontUri + "_" + version;
      }

      if (!ontologies.containsKey(vOntUri)) {
        reloadOntology(ontUri, version)
      }

      OWLOntology ontology = ontologies.get(ontUri)
      //println(String.valueOf(queryEngine)+"-->"+mOwlQuery+"-->"+requestType+"-->"+direct+"-->"+labels);
      Set resultSet = Sets.newHashSet(Iterables.limit(queryEngine.getClasses(mOwlQuery, requestType, direct, labels), MAX_QUERY_RESULTS))
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

/*  public ArrayList sparqlTest(String graphName,String concept,List<String> properties){
        ArrayList result = new ArrayList<HashMap>();
        SparqlAdaptor adaptor =  new SparqlAdaptor();
        List<String> instances = adaptor.querySparql(graphName,concept, properties);
        if(instances){
            for(def c : instances) {
                def info = [
                        "owlClass"  : c,
                        "label"     : c.substring(c.lastIndexOf("/")+1,c.length())
                ];
                result.add(info);
            }
        }
        return(result);
    }*/

}
