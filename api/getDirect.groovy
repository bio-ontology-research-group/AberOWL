// Run a query and ting

import groovy.json.*

if(!application) {
  application = request.getApplication(true)
}
def query = request.getParameter('query')
def ontology = request.getParameter('ontology')
def type = request.getParameter('type')
def rManager = application.rManager

try {
  def results = new HashMap()
  def start = System.currentTimeMillis()

  if(type == 'sub') {
    def out = rManager.getDirectSubclasses(query, ontology)
  } else if(type == 'super') {
    def out = rManager.getDirectSuperclasses(query, ontology)
  }

  def end = System.currentTimeMillis()

  results.put('time', (end - start))
  results.put('result', out)

  response.contentType = 'application/json'
  print new JsonBuilder(results).toString()
} catch(Exception e) {
  print e
}
