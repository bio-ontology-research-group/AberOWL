import groovy.json.*
import org.json.simple.JSONValue;
import groovy.json.*
import org.eclipse.jetty.server.Request
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.ContentType
import java.util.concurrent.*

import src.util.Util

if(!application) {
  application = request.getApplication(true)
}

ConcurrentHashMap omap = application.omap

print new JsonBuilder(omap).toString()
