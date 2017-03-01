
package src.util;

import groovy.json.*
import org.eclipse.jetty.server.Request

class Util {
  public static extractParams(Request request) throws IOException {
    if ("POST".equalsIgnoreCase(request.getMethod())) {
        Scanner s = new Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
        return new JsonSlurper().parseText(s.hasNext() ? s.next() : "");
    } else {
	def params = [:]
        request.getParameterNames().each { params[it] = request.getParameter(it) }
        return params
    }
  }
}
