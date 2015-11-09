if(!application) {
  application = request.getApplication(true)
}
def ontology = request.getParameter('ontology')
def rManager = application.rManager

if(ontology) {
  def record = rManager.oBase.getOntology(ontology);

  record.submissions.each { a, y ->
    record.submissions[y] = RequestManager.WebRoot
  }

  response.contentType = 'application/json'
  print new JsonBuilder(output).toString()
}
