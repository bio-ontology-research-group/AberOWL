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
@Grab(group='javax.el', module='javax.el-api', version='3.0.0')

 
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
import org.eclipse.jetty.util.log.Log
import org.eclipse.jetty.util.log.StdErrLog
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.ContentType

Log.setLog(new StdErrLog())
MASTER_SERVER = "http://10.254.145.14:55560/"
http = new HTTPBuilder(MASTER_SERVER)

def startServer(def ontId) {
  // finding free port
  ServerSocket s = new ServerSocket(0)
  def usedPort = s.getLocalPort()
  s.close()
  s.finalize()

  Server server = new Server(usedPort)
  if (!server) {
    System.err.println("Failed to create server, cannot open port.")
    System.exit(-1)
  }
  
  def context = new ServletContextHandler(server, '/', ServletContextHandler.SESSIONS)
  context.resourceBase = '.'

  println "Starting $ontId"
  def localErrorHandler = new ErrorHandler()
  localErrorHandler.setShowStacks(true)
  context.setErrorHandler(localErrorHandler)
  context.resourceBase = '.'
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
  context.setAttribute("ontology", ontId)
  context.setAttribute('port', usedPort)
  context.setAttribute('version', '0.2')
  server.start()
  println "Server started on "+server.getURI()+", registering..."
  def resp = http.get( path: '/oapi/registerOntology.groovy', query : [ ontology : ontId, uri : server.getURI() ] )
  println "Server response: " + resp
  println "Classifying..."

  context.setAttribute("rManager", new RequestManager(ontId))
}

def ontId = args[0]
startServer(ontId)
