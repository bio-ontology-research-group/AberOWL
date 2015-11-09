
@Grapes([
          @Grab(group='javax.servlet', module='javax.servlet-api', version='3.1.0'),
          @Grab(group='org.eclipse.jetty', module='jetty-server', version='9.3.0.M2'),
          @Grab(group='org.eclipse.jetty', module='jetty-servlet', version='9.3.0.M2'),
          @Grab(group='redis.clients', module='jedis', version='2.5.2'),
          @Grab(group='com.google.code.gson', module='gson', version='2.3.1'),
          @Grab(group='org.apache.lucene', module='lucene-queryparser', version='5.2.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),

          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='4.1.0-RC2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.1.0-RC2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.1.0-RC2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.1.0-RC2'),

          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
          @Grab(group='org.apache.lucene', module='lucene-core', version='5.2.1'),
          @Grab(group='org.apache.lucene', module='lucene-analyzers-common', version='5.2.1'),
          @Grab(group='org.apache.lucene', module='lucene-misc', version='5.2.1'),
          @Grab(group='aopalliance', module='aopalliance', version='1.0'),
	  @GrabConfig(systemClassLoader=true)
	])
@Grab(group='javax.el', module='javax.el-api', version='3.0.0')
 
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
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

import org.apache.lucene.misc.*
import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.search.similarities.*
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




loadedOntologies = 0
attemptedOntologies = 0
noFileError = 0
importError = 0
parseError = 0
otherError = 0
lCount = 0
dCount = 0
oManager = null
aProperties = new ArrayList<>()
df = OWLManager.getOWLDataFactory() 
oBase = new OntologyDatabase()

List<String> queryNames(String query, String ontUri) {
  String[] fields = ['label', 'ontology', 'oboid', 'definition', 'synonym', 'AberOWL-catch-all']
  // List<String> fList = []
  // MultiFields.getFields(DirectoryReader.open(index))?.each {
  //   def s = it.toString()?.toLowerCase()
  //   if (s.contains("label") || s.contains("ontology") || s.contains("definition") || s.contains("description") || s.contains("oboid") || s.contains("synonym")) {
  // 	fList << it
  //   }
  // }
  //    String[] allFields = fList.toArray(new String[fList.size()])
  def oQuery = query
  Map boostVals = ['label':100,
		   'ontology':1,
		   'oboid':10000,
		   'definition':1,
		   'synonym':10,
		   'AberOWL-catch-all':0.01
		  ]
    
  //query = oQuery.toLowerCase().split().collect({ 'first_label:' + classic.QueryParser.escape(it) + '*' }).join(' AND ')
  //    query = oQuery.toLowerCase().split().collect({ classic.QueryParser.escape(it) + '*' }).join(' AND ')
  //    query = oQuery.toLowerCase().split().collect({ classic.QueryParser.escape(it) +'*' }).join(' AND ')
    
  def parser = new classic.MultiFieldQueryParser(fields, new WhitespaceAnalyzer(), boostVals)
  def queryList = []
  oQuery.toLowerCase().split().each {
    def ll = new LinkedHashSet()
    boostVals.each { l, v ->
      ll.add(parser.parse(""+l+":"+classic.QueryParser.escape(it) +''+"^"+v))
    }
    queryList << new DisjunctionMaxQuery(ll, 0.1)
  }
				       // 	 boostVals.each { l, v ->
				       // 	   ll << "("+l+":"+classic.QueryParser.escape(it) +'*'+")^"+v
				       // 	 }
				       // 	 "("+ll.join(" OR ")+")"
				       // }).join(' AND ')

  if(ontUri && ontUri != '') {
    queryList << parser.parse('ontology:' + ontUri)
    queryList << parser.parse('oldVersion:'+false)
  } else {
    queryList << parser.parse('oldVersion:'+false)
  }

  def fQuery = new BooleanQuery()
  queryList.each { q ->
    fQuery.add(q, BooleanClause.Occur.MUST)
  }
  println "Query: $fQuery"
  //  def hits = searcher.search(fQuery, 10000, Sort.RELEVANCE, true, true).scoreDocs
  def hits = searcher.search(fQuery, 10000, Sort.RELEVANCE, true, true).scoreDocs
  def ret = []
    
  hits?.each { h ->
    def hitDoc = searcher.doc(h.doc)
    println hitDoc.get('label') 
    println searcher.explain(fQuery, h.doc).toString()
    def label = hitDoc.get('label') 
    def ontology = hitDoc.get('ontology') 
    def iri = hitDoc.get('class') 
    def fLabel = hitDoc.get('first_label') 
    def definition = hitDoc.get('definition')
    if(label && label.indexOf(' ') != -1) {
      label = "'" + label + "'"
    }
    ret.add([
	      'label': label
	    ])
  }

  return ret
}

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
          //loadStati.put(oRec.id, 'loaded')
        } catch (OWLOntologyAlreadyExistsException E) {
          if(oRec && oRec.id) {
            println 'DUPLICATE ' + oRec.id
          }
        } catch (OWLOntologyInputSourceException e) {
          println "File not found for " + oRec.id 
          if(oRec && oRec.id) {
            //loadStati.put(oRec.id, 'unloadable')
          }
          noFileError++
        } catch (IOException e) {
          println "Can't load external import for " + oRec.id 
          if(oRec && oRec.id) {
            //loadStati.put(oRec.id, 'unloadable')
          }
          importError++
        } catch(OWLOntologyCreationIOException e) {
          println "Failed to load imports for " + oRec.id
          if(oRec && oRec.id) {
            //loadStati.put(oRec.id, 'unloadable')
          }
          importError++
        } catch(UnparsableOntologyException e) {
          println "Failed to parse ontology " + oRec.id
          e.printStackTrace()
          if(oRec && oRec.id) {
            //loadStati.put(oRec.id, 'unloadable')
          }
          parseError++
        } catch(UnloadableImportException e) {
          println "Failed to load imports for " + oRec.id
          if(oRec && oRec.id) {
            //loadStati.put(oRec.id, 'unloadable')
          }
          importError++
        } catch (Exception E) {
          println oRec.id + ' other'
          if(oRec && oRec.id) {
            //loadStati.put(oRec.id, 'unloadable')
          }
          otherError++
        }
      }
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

      if(ontology == '') {
        println "Loading index"
        for(String uri : ontologies.keySet()) {
          reloadOntologyIndex(uri, writer, isOldVersion)
        }
      } else {
        reloadOntologyIndex(ontology, writer, isOldVersion)
      }
      writer.commit()
      searcher = new IndexSearcher(DirectoryReader.open(index))
  }

  void reloadOntologyIndex(String uri, IndexWriter index, boolean isOldVersion) {
    def ont = ontologies.get(uri)
    def manager = ont.getOWLOntologyManager()
    def labels = [
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
    if(info.description) {
      oDoc.add(new Field('ldescription', info.description.toLowerCase(), TextField.TYPE_STORED))
      oDoc.add(new Field('description', info.description, TextField.TYPE_STORED))
    }
    index.addDocument(oDoc)

    // Readd all classes for this ont

    OWLOntologyImportsClosureSetProvider mp = new OWLOntologyImportsClosureSetProvider(manager, ont)
    OWLOntologyMerger merger = new OWLOntologyMerger(mp, false)
    def iOnt = merger.createMergedOntology(manager, IRI.create("http://test.owl"))

    def sProvider = new AnnotationValueShortFormProvider(
      Collections.singletonList(df.getRDFSLabel()),
      Collections.<OWLAnnotationProperty, List<String>>emptyMap(),
      manager);
    def manSyntaxRenderer = new ManchesterOWLSyntaxOWLObjectRendererImpl()
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
      f = new Field("AberOWL-catch-all", uri.toLowerCase(), TextField.TYPE_STORED)
      doc.add(f)
      f= new Field('type', 'class', TextField.TYPE_STORED)
      doc.add(f)
      f = new Field('class', cIRI, TextField.TYPE_STORED)
      doc.add(f)
      f = new Field("oldVersion",isOldVersion.toString(), TextField.TYPE_STORED)
      doc.add(f)

      /* get the axioms */
      EntitySearcher.getSubClasses(iClass, iOnt).each { cExpr -> // OWL Class Expression
	if (! cExpr.isClassExpressionLiteral()) {
	  f = new Field('AberOWL-subclass', manSyntaxRenderer.render(cExpr), TextField.TYPE_STORED)
	  doc.add(f)
	}
      }
      EntitySearcher.getEquivalentClasses(iClass, iOnt).each { cExpr -> // OWL Class Expression
	if (! cExpr.isClassExpressionLiteral()) {
	  f = new Field('AberOWL-equivalent', manSyntaxRenderer.render(cExpr), TextField.TYPE_STORED)
	  doc.add(f)
	}
      }
      
      def deprecated = false
      def annoMap = [:].withDefault { new TreeSet() }
      EntitySearcher.getAnnotations(iClass, iOnt).each { anno ->
	if(anno.isDeprecatedIRIAnnotation()) {
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
	      annoMap[aProp.toString()?.replaceAll("<","")?.replaceAll(">","")].add(aVal)
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
      if (cIRI.lastIndexOf("/")>-1) {
	oboId = cIRI.substring(cIRI.lastIndexOf("/")+1)
      }
      if (cIRI.lastIndexOf("#")>-1) {
	oboId = cIRI.substring(cIRI.lastIndexOf("#")+1)
      }
      if (oboId.length()>0) {
	oboId = oboId.replaceAll("_",":").toLowerCase()
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
	EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
	  if(annotation.getValue() instanceof OWLLiteral) {
	    def val = (OWLLiteral) annotation.getValue()
	    def label = val.getLiteral().toLowerCase()
	    
	    f = new Field('synonym', label, TextField.TYPE_STORED)
	    doc.add(f)
	  }
	}
      }
      def hasLabel = false
      labels.each {
	EntitySearcher.getAnnotationAssertionAxioms(iClass, iOnt).each { ax ->
	  if (ax.getProperty() == it) {
	    
	    if(ax.getValue() instanceof OWLLiteral) {
	      def val = (OWLLiteral) ax.getValue()
	      def label = val.getLiteral().toLowerCase()
	      if (oboId == "go:0006915") {
		println label
	      }
	      if (label) {
		f = new Field('label', label, TextField.TYPE_STORED)
		doc.add(f)
		hasLabel = true
		if(firstLabelRun) {
		  lastFirstLabel = label;
		}
	      }
	    }
	  }
	}
	if(lastFirstLabel) {
	  f = new Field('first_label', lastFirstLabel, TextField.TYPE_STORED)
	  doc.add(f)
	  firstLabelRun = false
	}
      }
      definitions.each {
	EntitySearcher.getAnnotations(iClass, iOnt, it).each { annotation -> // OWLAnnotation
	  if(annotation.getValue() instanceof OWLLiteral) {
	    def val = (OWLLiteral) annotation.getValue()
	    def label = val.getLiteral().toLowerCase()
	    f = new Field('definition', label, TextField.TYPE_STORED)
	    doc.add(f)
	    if(annotation != null) {
	      dCount += 1
	    }
	  }
	}
      }
      if (! hasLabel) {
	f = new Field('label', iClass.getIRI().getFragment().toString().toLowerCase(), TextField.TYPE_STORED)
	doc.add(f) // add remainder
      }
      if(!lastFirstLabel) {
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
	if(it.getProperty().getIRI() == new IRI('http://www.geneontology.org/formats/oboInOwl#hasDbXref')) {
	  it.getAnnotations().each {
	    def label = it.getValue().getLiteral().toLowerCase()
	    if(!xrefs.contains(label)) {
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


ontologies = new ConcurrentHashMap()
ontologyManagers = new ConcurrentHashMap()
queryEngines = new ConcurrentHashMap()
loadStati = new ConcurrentHashMap()
oldOntologies = new ConcurrentHashMap()

// Index things
index = new RAMDirectory()
searcher = null
IndexWriterConfig iwc = new IndexWriterConfig(new WhitespaceAnalyzer())
iwc.setOpenMode(OpenMode.CREATE_OR_APPEND)
writer = new IndexWriter(index, iwc)


println "Loading ontologies"
loadOntologies()

loadIndex()

println queryNames(args[0], "GO")
