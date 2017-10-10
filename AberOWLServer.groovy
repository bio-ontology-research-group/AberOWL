@Grapes([
          @Grab(group='javax.servlet', module='javax.servlet-api', version='3.1.0'),
          @Grab(group='org.eclipse.jetty', module='jetty-server', version='9.4.0.M1'),
          @Grab(group='org.eclipse.jetty', module='jetty-servlet', version='9.4.0.M1'),
          @Grab(group='redis.clients', module='jedis', version='2.5.2'),
          @Grab(group='com.google.code.gson', module='gson', version='2.3.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
	  //	  @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),

	  @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.3'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.3.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.3.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.3.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='4.3.2'),

	  @Grab(group='com.google.guava', module='guava', version='19.0'),

	  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1' ),

          @Grab(group='org.codehaus.gpars', module='gpars', version='1.2.1'),
          @Grab(group='aopalliance', module='aopalliance', version='1.0'),
	  @GrabConfig(systemClassLoader=true)
	])
 
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.*
import org.eclipse.jetty.server.handler.*
import groovy.servlet.*
import src.*
import java.util.concurrent.*
import org.apache.log4j.Logger
import org.apache.log4j.Level
import org.eclipse.jetty.server.nio.*
import org.eclipse.jetty.util.thread.*

Logger.getRootLogger().setLevel(Level.ERROR)

PORTRANGE = 62000..65000

def startServer(def ontId) {
  Server server = null
  def usedPort = -1
  for (Integer port : PORTRANGE) {
    if (!server) {
      try {
	println "Trying port $port..."
	server = new Server(port)
	usedPort = port
      } catch (Exception E) { 
	println E.getMessage()
      }
    }
  }
  if (!server) {
    System.err.println("Failed to create server, cannot open port.")
    System.exit(-1)
  }
  
  def context = new ServletContextHandler(server, '/', ServletContextHandler.SESSIONS)
  context.resourceBase = '.'

  def file = new File("../ontologies/"+ontId)
  println "Starting $ontId"
  def localErrorHandler = new ErrorHandler()
  localErrorHandler.setShowStacks(true)
  context.setErrorHandler(localErrorHandler)
  context.resourceBase = '.'
  //  context.setContextPath("/"+ontId+"/")
  context.addServlet(GroovyServlet, '/api/runQuery.groovy')
  context.addServlet(GroovyServlet, '/api/queryOntologies.groovy')
  context.addServlet(GroovyServlet, '/api/getClass.groovy')
  context.addServlet(GroovyServlet, '/api/queryNames.groovy')
  context.addServlet(GroovyServlet, '/api/getStats.groovy')
  context.addServlet(GroovyServlet, '/api/getStatuses.groovy')
  context.addServlet(GroovyServlet, '/api/listOntologies.groovy')
  context.addServlet(GroovyServlet, '/api/reloadOntology.groovy')
  context.addServlet(GroovyServlet, '/api/findRoot.groovy')
  context.addServlet(GroovyServlet, '/api/getObjectProperties.groovy')
  context.addServlet(GroovyServlet, '/api/getOntology.groovy')
  context.addServlet(GroovyServlet, '/api/retrieveRSuccessors.groovy')
  context.addServlet(GroovyServlet, '/api/retrieveAllLabels.groovy')
  //  context.addServlet(GroovyServlet, '/api/getSparql.groovy')

  context.setAttribute('port', usedPort)
  context.setAttribute('version', '0.2')
  context.setAttribute("rManager", new RequestManager(ontId))
  context.setAttribute("ontology", ontId)
  //  println "Added " + context.getContextPath() + ":\n" + context.getResourcePaths("/$ontId/")
  server.start()
}

def ontId = args[0]
startServer(ontId)
