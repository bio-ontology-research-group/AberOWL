// Run a query and ting

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

import groovy.json.*

if(!application) {
  application = request.getApplication(true)
}
def query = request.getParameter('query')
def ontology = request.getParameter('ontology')
def objectProperty = request.getParameter('objectProperty');
def rManager = application.rManager;

def PREFIX_MAP = [
  "http://www.geneontology.org/formats/oboInOwl#" : "oboInOwl:",
  "http://purl.org/dc/elements/1.1/" : "dc:",
  "http://protege.stanford.edu/plugins/owl/protege#" : "protege:",
  "http://purl.org/dc/terms/" : "dc:",
  "http://purl.org/dc/elements/1.1/" : "dc:",
  "http://purl.org/dc/terms/" : "dc:",
  "http://purl.org/vocab/vann/" : "vann:",
  "http://purl.org/spar/cito/" : "cito:",
  "http://purl.obolibrary.org/obo/" : "obo:",
  "http://www.w3.org/2004/02/skos/core" : "skos:",
  "http://semanticscience.org/resource/" : "sio:"
]

def prefixUrls = { String s ->
  PREFIX_MAP.keySet().each { prefix ->
    if (s.startsWith(prefix)) {
      s = s.replaceAll(prefix, PREFIX_MAP[prefix])
    }
  }
  s
}

def PREFIX_MAP2 = [
  "reactome:" : ["http://www.reactome.org/content/query?cluster=true&q=", true],
  "pmid:" : ["http://www.ncbi.nlm.nih.gov/pubmed/", false],
  "nci:" : ["http://aber-owl.net/ontology/NCIT#!http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23", true],
  "ncit:" : ["http://aber-owl.net/ontology/NCIT#!http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23", true],
  "url:" : ["", false],
  "msh:" : ["http://aber-owl.net/ontology/RH-MESH#!http%3A%2F%2Fphenomebrowser.net%2Fontologies%2Fmesh%2Fmesh.owl%23", true],
  "mesh:" : ["http://aber-owl.net/ontology/RH-MESH#!http%3A%2F%2Fphenomebrowser.net%2Fontologies%2Fmesh%2Fmesh.owl%23", true]
]

def prefixUrls2 = { String s ->
  PREFIX_MAP2.keySet().each { prefix ->
    if (s.startsWith(prefix)) {
      def last = s.replaceAll(prefix, "")
      if (PREFIX_MAP2[prefix][1]) {
	last = last.toUpperCase()
      }
      s = '<a href="'+PREFIX_MAP2[prefix][0]+last+'">'+prefix+last+'</a>'
    }
  }
  s
}


if(ontology && query) {
  query = java.net.URLDecoder.decode(query, "UTF-8")
  try {
    def results = [:]
    //def results = []
    def start = System.currentTimeMillis()
    def bq = new BooleanQuery()
    bq.add(new TermQuery(new Term('class', query)), BooleanClause.Occur.MUST);
    bq.add(new TermQuery(new Term('ontology', ontology)), BooleanClause.Occur.MUST);

    def result = rManager.searcher.search(bq, 1).scoreDocs[0]
    def hitDoc = rManager.searcher.doc(result.doc)
    def output = [:].withDefault { new TreeSet() }
    hitDoc.each { fieldName ->
      if (! (fieldName.name in ["oldVersion", "first_label", "AberOWL-catch-all"])) {
	hitDoc.getValues(fieldName.name).each {
	  output[prefixUrls(fieldName.name)].add(prefixUrls2(it))
	}
      }
    }
    output = output.sort { x, y ->
      if (x.key == 'label') {
	-1
      } else if (y.key == 'label') {
	1
      } else if (x.key == 'definition' && y.key != 'label') {
	-1
      } else if (x.key == 'oboid' && y.key != 'label' && y.key != 'definition') {
	-1
      } else {
	x.key.compareTo(y.key)
      }
    }
    
    response.contentType = 'application/json'
    print new JsonBuilder(output).toString()
  } catch(Exception e) {
    
    print new JsonBuilder([:]).toString()
  }
}else if(ontology && objectProperty){
  try {

    def result = rManager.getInfoObjectProperty(ontology,objectProperty);
    def output = [:]
    result.keySet().each { key ->
      output[key] = result.get(key)
    }
    response.contentType = 'application/json'
    print new JsonBuilder(output).toString()
  } catch(Exception e) {
    print new JsonBuilder([:]).toString()
  }
}else {
  println 'missing stuff'
}


