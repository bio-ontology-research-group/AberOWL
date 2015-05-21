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
    def resultMap = [:] 
    
    def out = rManager.runQuery(query, 'equivalent', ontology, true, false)
    resultMap['classes'] = out[0]

    def thingFound = false
    while(thingFound == false) {
      out = rManager.runQuery(query, 'superclass', ontology, true, false)

      if(!out || (out && out[0].owlClass == '<http://www.w3.org/2002/07/owl%23Thing>')) {
        thingFound = true
        break
      } else {
        query = out[0].owlClass
      }

      resultMap = [ 'classes': out, 'children': resultMap ]
    }

    response.contentType = 'application/json'
    print new JsonBuilder(resultMap).toString()
  } catch(Exception e) {
    print e
  }
} else {
  println 'missing stuff'
}


