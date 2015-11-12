import groovy.json.JsonBuilder
import org.json.simple.JSONValue;

if(!application) {
    application = request.getApplication(true)
}

def ontology = request.getParameter('ontology')
def objectProperty = request.getParameter('rootObjectProperty');
def rManager = application.rManager
if((objectProperty)&&(ontology)) {
  def objectProperties = rManager.getObjectProperties(ontology,objectProperty).sort {it.label}
    response.contentType = 'application/json'
    print new JsonBuilder(objectProperties)
}else if(ontology){
    def objectProperties = rManager.getObjectProperties(ontology)
    response.contentType = 'application/json'
    print new JsonBuilder(objectProperties)
}
