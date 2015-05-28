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
def rManager = application.rManager

if(ontology && query) {
  try {
    def results = new HashMap()
    def start = System.currentTimeMillis()

    def bq = new BooleanQuery()
    bq.add(new TermQuery(new Term('class', query)), BooleanClause.Occur.MUST);
    bq.add(new TermQuery(new Term('ontology', ontology)), BooleanClause.Occur.MUST);

    def result = rManager.searcher.search(bq, 1).scoreDocs[0]
    def hitDoc = rManager.searcher.doc(result.doc)
    def output = [:]
    hitDoc.each {
      output[it.name] = hitDoc.get(it.name)
    }

    response.contentType = 'application/json'
    print new JsonBuilder(output).toString()
  } catch(Exception e) {
    print e
  }
} else {
  println 'missing stuff'
}


