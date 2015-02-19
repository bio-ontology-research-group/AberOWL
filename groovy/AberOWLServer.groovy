@Grapes([
          @Grab('org.eclipse.jetty:jetty-server:9.0.0.M5'),
          @Grab('org.eclipse.jetty:jetty-servlet:9.0.0.M5'),
          @Grab('javax.servlet:javax.servlet-api:3.0.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
          @GrabExclude('org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016'),
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.0.1'),
          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.1'),
          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
	  @GrabConfig(systemClassLoader=true)
	])
 
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*

def startServer() {
  def server = new Server(30003)
  def context = new ServletContextHandler(server, '/', ServletContextHandler.SESSIONS)

  context.resourceBase = '.'
  context.addServlet(GroovyServlet, '/api/runQuery.groovy')
  context.setAttribute('version', '0.1')
  context.setAttribute("rManager", new RequestManager())

  server.start()
}

startServer()
