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

if(ontology && query) {
  try {
    def results = new HashMap()
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
	  output[fieldName.name].add(it)
	}
      }
    }

    response.contentType = 'application/json'
    print new JsonBuilder(output).toString()
  } catch(Exception e) {
    print e
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
    print e
  }
}else {
  println 'missing stuff'
}


