// Gets the relational direct successors

import groovy.json.*
import src.util.Util

if(!application) {
  application = request.getApplication(true)
}

def params = Util.extractParams(request)
def relation = params.relation
def qClass = params.class
def ontology = application.ontology
//def version = params.version
def rManager = application.rManager

if (!relation || !qClass || !ontology) {
  return
}
if (version == null) {
  version = -1
}

try {
  def results = new HashMap()
  def out = rManager.relationQuery(relation, qClass)

  results['result'] = out
  response.contentType = 'application/json'
  print new JsonBuilder(results).toString()
} catch(Exception e) {
  response.setStatus(400)
  println new JsonBuilder([ 'err': true, 'message': 'Generic query error: ' + e.getMessage() ]).toString() 
}
