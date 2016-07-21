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
def query = request.getParameter('query');
def version = request.getParameter('version');
def ontology = request.getParameter('ontology');
def objectProperty = request.getParameter('objectProperty');
def rManager = application.rManager;

if(query && version && ontology && objectProperty){
  try {
    def results = new HashMap()
    def start = System.currentTimeMillis()
    def out = rManager.runSparqlQuery(ontology,version,query,objectProperty);
    def end = System.currentTimeMillis()
    results.put('time', (end - start))
    results.put('result', out)
    response.contentType = 'application/json'
    print new JsonBuilder(results).toString()
  } catch(Exception e) {
    e.printStackTrace()
    print new JsonBuilder([:]).toString()
  }
} else {
  response.setStatus(400)
  println new JsonBuilder([ 'err': true, 'message': 'Missing parameters. Please refer to the API documentation.' ]).toString() 
}


