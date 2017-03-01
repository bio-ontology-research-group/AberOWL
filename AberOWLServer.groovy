@Grapes([
          @Grab(group='javax.servlet', module='javax.servlet-api', version='3.1.0'),
          @Grab(group='org.eclipse.jetty', module='jetty-server', version='9.4.0.M1'),
          @Grab(group='org.eclipse.jetty', module='jetty-servlet', version='9.4.0.M1'),
          @Grab(group='redis.clients', module='jedis', version='2.5.2'),
          @Grab(group='com.google.code.gson', module='gson', version='2.3.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
	  //	  @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),

	  @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.2.3'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.2.3'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.2.3'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-parsers', version='4.2.3'),

	  @Grab(group='com.google.guava', module='guava', version='19.0'),

	  @Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7.1' ),

          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
          @Grab(group='aopalliance', module='aopalliance', version='1.0'),
	  @GrabConfig(systemClassLoader=true)
	])
 
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.server.ServerConnector
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import src.*
import java.util.concurrent.*
import org.apache.log4j.Logger
import org.apache.log4j.Level
import org.eclipse.jetty.server.nio.*
import org.eclipse.jetty.util.thread.*

Logger.getRootLogger().setLevel(Level.ERROR)

PORT = new Integer(args[0])
def startServer() {
  Server server = new Server(PORT)
  server.getThreadPool().setMaxThreads(5000)
  server.getThreadPool().setIdleTimeout(5000)
  println server.getThreadPool()
  //  ServerConnector connector = new ServerConnector(server)

  //  def server = new Server(PORT)
  def context = new ServletContextHandler(server, '/', ServletContextHandler.SESSIONS)

  context.resourceBase = '.'
  context.addServlet(GroovyServlet, '/api/runQuery.groovy')
  context.addServlet(GroovyServlet, '/api/getClass.groovy')
  context.addServlet(GroovyServlet, '/api/queryNames.groovy')
  context.addServlet(GroovyServlet, '/api/queryOntologies.groovy')
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

  context.setAttribute('version', '0.1')
  context.setAttribute("rManager", new RequestManager(true))

  server.start()
}

println org.semanticweb.owlapi.util.VersionInfo.getVersionInfo();
startServer()
