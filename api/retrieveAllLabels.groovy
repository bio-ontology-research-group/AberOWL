package src
// Run a query and ting

import groovy.json.*
import org.apache.log4j.*
import groovy.util.logging.*

if(!application) {
  application = request.getApplication(true)
}

def ontology = application.ontology
def rManager = application.rManager

if(ontology) {
  query = java.net.URLDecoder.decode(ontology, "UTF-8")
  def output = new TreeSet()
  def results = [:]
  def start = System.currentTimeMillis()
  def fQuery = ["query": ["bool":["must":[]]]]
  def ll = []
  ll << ["term" : ["ontology" : query]]
  ll.each {
    fQuery.query.bool.must << it
  }
  
  def hits = RequestManager.search("owlclass", fQuery)
  
  def hitDoc = hits.hits.hits.collect { it._source }
  hitDoc.each { result ->
    result.each { fieldName, value ->
      if (fieldName in ["label", "synonym"]) {
	value.each {
	  output.add(it)
	}
      }
    }
  }
  print new JsonBuilder(output).toString()
} else {
  response.setStatus(400)
  println new JsonBuilder([ 'err': true, 'message': 'Missing parameters. Please refer to the API documentation.' ]).toString() 
}

