
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

response.contentType = 'application/json'
try {
def out = rManager.runQuery(query, type, ontology)
print JSONValue.toJSONString(out);
} catch(Exception e) {
  print 'err'
}
