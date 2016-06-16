import groovy.json.*
import src.util.Util
import org.eclipse.jetty.server.*

if(!application) {
  application = request.getApplication(true)
}
//println request.getAttribute("org.eclipse.jetty.server.Server").getRequestLog().isStarted()
//println application.getServer()
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
} else {
  response.setStatus(404)
  println new JsonBuilder([ 'err': true, 'message': 'Ontology not found.' ]).toString() 
}
