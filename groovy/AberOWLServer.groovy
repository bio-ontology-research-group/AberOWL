@Grapes([
	  @Grab('org.eclipse.jetty:jetty-server:9.0.0.M5'),
	  @Grab('org.eclipse.jetty:jetty-servlet:9.0.0.M5'),
	  @Grab('javax.servlet:javax.servlet-api:3.0.1'),
	  @GrabExclude('org.eclipse.jetty.orbit:javax.servlet:3.0.0.v201112011016'),
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.0.1'),
          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.1'),
	  @GrabConfig(systemClassLoader=true)
	])
 
import groovy.sparql.*
 
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import uk.ac.aber.lus11.sparqowlapi.request.* 
import uk.ac.aber.lus11.sparqowlapi.util.* 
import groovy.json.*
import org.eclipse.jetty.server.Server
import org.eclipse.jetty.servlet.*
import groovy.servlet.*
import javax.servlet.http.*
import javax.servlet.ServletConfig

class DoPhenoServer extends HttpServlet {
  public final static String disfilename = "doid2hpo-fulltext.txt"
  def requestHandler
  def context
  void init(ServletConfig config) {
    super.init(config)
    context = config.servletContext
  }

  void service(HttpServletRequest request, HttpServletResponse response) {
    requestHandler.binding = new ServletBinding(request, response, context)
    use (ServletCategory) {
      requestHandler.call()
    }
  }

  void run(int port) {
    println "Starting Server"
    def jetty = new Server(port)
    println "Adding Servlet"
    def context = new ServletContextHandler(jetty, '/', ServletContextHandler.SESSIONS)
 
    context.resourceBase = '.'  
    println "adding new context"
    context.addServlet(GroovyServlet, '/AberOWLServlet.groovy')
    context.setAttribute('version', '1.0')  

    // I don't know what these do
    // context.setAttribute("dis2name", dis2name)
    // context.setAttribute("pheno2name", pheno2name)
    // context.setAttribute("dismap", dismap)
    // context.setAttribute("donames", donames)
    // context.setAttribute("name2doid", name2doid)

    println "Starting Jetty"
    jetty.start()
  }

  public static void main(args) {
    DoPhenoServer s = new DoPhenoServer()
    s.run(30003)
  }
}

