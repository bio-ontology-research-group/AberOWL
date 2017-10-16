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

// main proxy

Logger.getRootLogger().setLevel(Level.ERROR)

PORT = 55560

def startServer() {
  Server server = new Server(PORT)
  def context = new ServletContextHandler(server, '/', ServletContextHandler.SESSIONS)
  context.resourceBase = '.'

  ConcurrentHashMap omap = new ConcurrentHashMap(1000, 0.7, 64) // initial size, load level, concurrency

  context.resourceBase = '.'
  context.addServlet(GroovyServlet, '/o-api/registerOntology.groovy')
  context.addServlet(GroovyServlet, '/o-api/getOmap.groovy')
  context.addServlet(GroovyServlet, '/o-api/runQuery.groovy')
  context.addServlet(GroovyServlet, '/o-api/queryOntologies.groovy')
  context.addServlet(GroovyServlet, '/o-api/getClass.groovy')
  context.addServlet(GroovyServlet, '/o-api/queryNames.groovy')
  context.addServlet(GroovyServlet, '/o-api/getStats.groovy')
  context.addServlet(GroovyServlet, '/o-api/getStatuses.groovy')
  context.addServlet(GroovyServlet, '/o-api/listOntologies.groovy')
  context.addServlet(GroovyServlet, '/o-api/reloadOntology.groovy')
  context.addServlet(GroovyServlet, '/o-api/findRoot.groovy')
  context.addServlet(GroovyServlet, '/o-api/getObjectProperties.groovy')
  context.addServlet(GroovyServlet, '/o-api/getOntology.groovy')
  context.addServlet(GroovyServlet, '/o-api/retrieveRSuccessors.groovy')
  context.addServlet(GroovyServlet, '/o-api/retrieveAllLabels.groovy')
  context.setAttribute('version', '0.9.9')
  context.setAttribute('omap', omap)
  server.start()
}

startServer()
