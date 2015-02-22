
// Run a query and ting

import groovy.json.*

import org.json.simple.JSONValue;

if(!application) {
  application = request.getApplication(true)
}

def query = request.getParameter('query')
def type = request.getParameter('type')
def ontology = request.getParameter('ontology')
def rManager = application.rManager

if(type == null) {
  type = 'all'
}
if(ontology == null) {
  ontology = ''
}

try {
  def results = new HashMap()
  def start = System.currentTimeMillis()

  def out = rManager.runQuery(query, type, ontology)

  def end = System.currentTimeMillis()

  results.put('time', (end - start))
  results.put('result', JSONValue.toJSONString(out))

  response.contentType = 'application/json'
  print JSONValue.toJSONString(results);
} catch(Exception e) {
  print 'err'
}
