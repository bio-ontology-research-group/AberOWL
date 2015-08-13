import groovy.json.JsonBuilder
import org.json.simple.JSONValue;

if(!application) {
    application = request.getApplication(true)
}

def ontology = request.getParameter('ontology')
def rManager = application.rManager

if(ontology) {
    def objectProperties = rManager.getObjectProperties(ontology)
    response.contentType = 'application/json'
    print new JsonBuilder(objectProperties).toString();
}
