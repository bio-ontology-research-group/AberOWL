import groovy.json.*
import src.util.Util

if(!application) {
  application = request.getApplication(true)
}
def params = Util.extractParams(request)
def ontology = params.ontology
def rManager = application.rManager

if(ontology) {
  def record = rManager.oBase.getOntology(ontology);

  record.submissions.each { a, y ->
    record.submissions[a] = rManager.WEB_ROOT + 'onts/' + y
  }

  response.contentType = 'application/json'
  print new JsonBuilder(record).toString()
}
