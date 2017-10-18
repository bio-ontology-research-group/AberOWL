import groovy.json.JsonBuilder
import org.json.simple.JSONValue;
import src.util.Util

if(!application) {
    application = request.getApplication(true)
}

def params = Util.extractParams(request)
def ontology = application.ontology
def objectProperty = params.rootObjectProperty
def rManager = application.rManager

if(objectProperty) {
  def objectProperties = rManager.getObjectProperties(objectProperty).sort {it.label}
    response.contentType = 'application/json'
    print new JsonBuilder(objectProperties)
} else {
    def objectProperties = rManager.getObjectProperties()
    response.contentType = 'application/json'
    print new JsonBuilder(objectProperties)
}
