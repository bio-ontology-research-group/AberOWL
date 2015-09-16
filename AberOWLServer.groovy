@Grapes([
          @Grab(group='javax.servlet', module='javax.servlet-api', version='3.1.0'),
          @Grab(group='org.eclipse.jetty', module='jetty-server', version='9.3.0.M2'),
          @Grab(group='org.eclipse.jetty', module='jetty-servlet', version='9.3.0.M2'),
          @Grab(group='redis.clients', module='jedis', version='2.5.2'),
          @Grab(group='com.google.code.gson', module='gson', version='2.3.1'),
          @Grab(group='org.apache.lucene', module='lucene-queryparser', version='5.2.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),

          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-api', version='4.0.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-apibinding', version='4.0.2'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-impl', version='4.0.2'),

          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
          @Grab(group='org.apache.lucene', module='lucene-core', version='5.2.1'),
          @Grab(group='org.apache.lucene', module='lucene-analyzers-common', version='5.2.1'),
          @Grab(group='aopalliance', module='aopalliance', version='1.0'),
	  @GrabConfig(systemClassLoader=true)
	])
@Grab(group='javax.el', module='javax.el-api', version='3.0.0')
 
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import src.*

def startServer() {
  def server = new Server(55555)
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
  context.addServlet(GroovyServlet, '/api/retrieveRSuccessors.groovy')
  context.setAttribute('version', '0.1')
  context.setAttribute("rManager", new RequestManager(true))

  server.start()
}
println org.semanticweb.owlapi.util.VersionInfo.getVersionInfo();
startServer()
