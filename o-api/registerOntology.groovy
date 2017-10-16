import groovy.json.*
import org.json.simple.JSONValue;
import java.util.concurrent.*

import src.util.Util

if(!application) {
  application = request.getApplication(true)
}

ConcurrentHashMap omap = application.omap

def params = Util.extractParams(request)
def ontology = params.ontology
def uri = params.uri
omap[ontology] = uri

def resp = [:]
resp["success"] = true
print new JsonBuilder(resp).toString()
