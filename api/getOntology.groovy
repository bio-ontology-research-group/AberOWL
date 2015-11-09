import groovy.json.*

if(!application) {
  application = request.getApplication(true)
}
def ontology = request.getParameter('ontology')
def rManager = application.rManager

if(ontology) {
  def record = rManager.oBase.getOntology(ontology);

  record.submissions.each { a, y ->
    record.submissions[a] = rManager.WEB_ROOT + 'onts/' + y
  }

  response.contentType = 'application/json'
  print new JsonBuilder(record).toString()
}
