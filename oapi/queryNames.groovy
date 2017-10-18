import src.util.Util
import java.util.concurrent.*
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.ContentType
import groovy.json.*


if(!application) {
  application = request.getApplication(true)
}
ConcurrentHashMap omap = application.omap
	    
def params = Util.extractParams(request)
def ontology = params.ontology
if (omap[ontology] != null) {
  def http = new HTTPBuilder(omap[ontology])
  def resp = http.get( path: '/api/queryNames.groovy', query : params )
  print new JsonBuilder(resp).toString()
} else {
  def res = [:]
  res["status"] = "error"
  res["success"] = false
  res["message"] = "Ontology not found."
  print new JsonBuilder(res).toString()
}
