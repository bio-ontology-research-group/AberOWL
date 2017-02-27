import groovy.json.*
import org.json.simple.JSONValue;

import src.util.Util

def rManager = application.rManager
response.contentType = 'application/json'

def params = Util.extractParams(request)
def ontology = params.ontology

if(ontology) {
  //  query = java.net.URLDecoder.decode(query, "UTF-8")
  try {
    //def results = []
    def output = [:]
    def start = System.currentTimeMillis()

    def fQuery = ["query": ["bool":["must":[]]]]
    def ll = []
    ll << ["term" : ["ontology" : ontology]]
    ll.each {
      fQuery.query.bool.must << it
    }

    def hits = RequestManager.search("owlclass", fQuery)

    def hitDoc = hits.hits.hits[0]._source
    output = [:].withDefault { new TreeSet() }
    hitDoc.keySet().each { fieldName ->
      if (! (fieldName.name in ["oldVersion", "first_label", "AberOWL-catch-all"])) {
	hitDoc[fieldName].each {
	  output[prefixUrls(fieldName)].add(prefixUrls2(it))
	}
      }
    }
  } catch (Exception E) {}
  print new JsonBuilder(output).toString()
} else {
  def keys = rManager.queryEngines.keySet().toArray().sort(false)
  print new JsonBuilder(keys).toString()
}

