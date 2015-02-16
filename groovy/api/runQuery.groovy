
// Run a query and ting

import groovy.json.*

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

print new JsonBuilder(rManager.runQuery(query, type, ontology)).toString()
