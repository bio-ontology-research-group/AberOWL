// Run a query and ting

import groovy.json.*

if(!application) {
  application = request.getApplication(true)
}
def query = request.getParameter('query')
def type = request.getParameter('type')
def ontology = request.getParameter('ontology')
def direct = request.getParameter('direct')
def labels = request.getParameter('labels')
def rManager = application.rManager

if(type == null) {
  type = 'all'
}
if(ontology == null) {
  ontology = ''
}
if(direct == null) {
  direct = ''
}
direct = direct.toBoolean()
if(labels == null) {
  labels = 'false'
}
labels = labels.toBoolean()

try {
  def results = new HashMap()
  def start = System.currentTimeMillis()

  def out = rManager.runQuery(query, type, ontology, direct, labels)

  def end = System.currentTimeMillis()

  results.put('time', (end - start))
  results.put('result', out)

  response.contentType = 'application/json'
  print new JsonBuilder(results).toString()
} catch(Exception e) {
  print e
}
