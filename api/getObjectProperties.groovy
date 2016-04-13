import groovy.json.JsonBuilder
import org.json.simple.JSONValue;
import src.util.Util

if(!application) {
    application = request.getApplication(true)
}

def params = Util.extractParams(request)
def ontology = params.ontology
def objectProperty = params.rootObjectProperty
def rManager = application.rManager

if(objectProperty && ontology) {
  def objectProperties = rManager.getObjectProperties(ontology,objectProperty).sort {it.label}
    response.contentType = 'application/json'
    print new JsonBuilder(objectProperties)
}else if(ontology){
    def objectProperties = rManager.getObjectProperties(ontology)
    response.contentType = 'application/json'
    print new JsonBuilder(objectProperties)
} else {
  response.setStatus(400)
  println new JsonBuilder([ 'err': true, 'message': 'Missing parameters. Please refer to the API documentation.' ]).toString() 
}
