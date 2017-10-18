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
import groovy.json.*
import groovy.servlet.*
import src.*
import redis.clients.jedis.*
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

// main proxy
//Log.setLog(new StdErrLog())

PORT = 55560

def startServer() {

  Server server = new Server(PORT)
  def context = new ServletContextHandler(server, '/', ServletContextHandler.SESSIONS)
  context.resourceBase = '.'

  def localErrorHandler = new ErrorHandler()
  localErrorHandler.setShowStacks(true)
  context.setErrorHandler(localErrorHandler)
  context.resourceBase = '.'

  context.addServlet(GroovyServlet, '/oapi/registerOntology.groovy')
  context.addServlet(GroovyServlet, '/oapi/getOmap.groovy')

  context.addServlet(GroovyServlet, '/oapi/runQuery.groovy')
  // context.addServlet(GroovyServlet, '/oapi/queryOntologies.groovy')
  context.addServlet(GroovyServlet, '/oapi/getClass.groovy')
  context.addServlet(GroovyServlet, '/oapi/queryNames.groovy')
  // context.addServlet(GroovyServlet, '/oapi/getStats.groovy')
  // context.addServlet(GroovyServlet, '/oapi/getStatuses.groovy')
  // context.addServlet(GroovyServlet, '/oapi/listOntologies.groovy')
  context.addServlet(GroovyServlet, '/oapi/reloadOntology.groovy')
  // context.addServlet(GroovyServlet, '/oapi/findRoot.groovy')
  context.addServlet(GroovyServlet, '/oapi/getObjectProperties.groovy')
  // context.addServlet(GroovyServlet, '/oapi/getOntology.groovy')
  // context.addServlet(GroovyServlet, '/oapi/retrieveRSuccessors.groovy')
  context.addServlet(GroovyServlet, '/oapi/retrieveAllLabels.groovy')
  context.setAttribute('version', '0.9.9')
  context.setAttribute('omap', omap)
  server.start()
}

omap = new ConcurrentHashMap(1000, 0.7, 64) // initial size, load level, concurrency

Jedis jedis = new Jedis("localhost")
JsonSlurper slurper = new JsonSlurper()
if (jedis.get("omap")) {
  def json = slurper.parseText(jedis.get("omap"))
  omap.putAll(json)
}

startServer()
