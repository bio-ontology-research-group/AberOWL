// Gets the relational direct successors

import groovy.json.*

if(!application) {
  application = request.getApplication(true)
}

def relation = request.getParameter('relation')
def qClass = request.getParameter('class')
def ontology = request.getParameter('ontology')
def version = request.getParameter('version')
def rManager = application.rManager

if (!relation || !qClass || !ontology) {
  return
}
if (version == null) {
  version = -1
}

try {
  def results = new HashMap()
  def out = rManager.relationQuery(relation, qClass, ontology, Integer.parseInt(version))

  results['result'] = out
  response.contentType = 'application/json'
  print new JsonBuilder(results).toString()
} catch(Exception e) {
  print e.printStackTrace()
}
