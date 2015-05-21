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
    
    def fOut = rManager.runQuery(query, 'equivalent', ontology, true, false)
    def out = null
    resultMap['classes'] = fOut

    def thingFound = false
    while(thingFound == false) {
      out = rManager.runQuery(query, 'superclass', ontology, true, false)

      if(!out || (out && out[0].owlClass == '<http://www.w3.org/2002/07/owl#Thing>')) {
        thingFound = true
        break
      } else {
        query = out[0].owlClass
      }

      out[0].children = resultMap
      resultMap = [ 'classes': out ]
    }
    
    out = rManager.runQuery('<http://www.w3.org/2002/07/owl#Thing>', 'subclasses', ontology, true, false).findAll { it.owlClass != query }
    resultMap.classes += out
    resultMap['chosen'] = fOut[0]

    response.contentType = 'application/json'
    print new JsonBuilder(resultMap).toString()
  } catch(Exception e) {
    print e
  }
} else {
  println 'missing stuff'
}


