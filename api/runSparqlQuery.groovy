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
def concept = request.getParameter('concept')
def graphName = request.getParameter('graphName')
def objectsProperties = request.getParameter('objectProperties');
def rManager = application.rManager;

if(concept && graphName && objectsProperties){
  try {
    ArrayList<String> oProperties = new ArrayList<String>();
    oProperties.add(objectsProperties);
    def result = rManager.runSparqlQuery(graphName,concept,oProperties);
    response.contentType = 'application/json'
    print new JsonBuilder(result).toString()
  } catch(Exception e) {
    print new JsonBuilder([:]).toString()
  }
} else {
  response.setStatus(400)
  println new JsonBuilder([ 'err': true, 'message': 'Missing parameters. Please refer to the API documentation.' ]).toString() 
}


