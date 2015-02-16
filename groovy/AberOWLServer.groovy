@Grapes([
          @Grab(group='org.mortbay.jetty', module='jetty-embedded', version='6.1.26'),
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.0.1'),
          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.1'),
          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
	  @GrabConfig(systemClassLoader=true)
	])
 
import org.mortbay.jetty.Server
import org.mortbay.jetty.servlet.*
import groovy.servlet.*
import javax.servlet.http.*
import javax.servlet.ServletConfig

class AberOWLServer extends HttpServlet {
  def requestHandler
  def context
  def binding

  void init(ServletConfig config) {
    super.init(config)
    context = config.servletContext
  }

  void service(HttpServletRequest request, HttpServletResponse response) {
    this.binding = new ServletBinding(request, response, context)
    use (ServletCategory) {
      requestHandler.call()
    }
  }

  static void run(int port, Closure requestHandler) {
    def servlet = new AberOWLServer('requestHandler': requestHandler)
    def jetty = new Server(port)
    def context = new Context(jetty, '/', Context.SESSIONS)
    context.addServlet(new ServletHolder(servlet), '/*')
    jetty.start()
    println "started server"
  }

  public static void main(args) {
    RequestManager r = new RequestManager()
    println "Attempted: " + r.attemptedOntologies
    println "Loaded: " + r.loadedOntologies
    def stats = r.getStats()
    stats.each{ k, v -> println "${k}:${v}" }

    AberOWLServer.run(30003) { ->
      println "yup"

          println "my path is ${request.pathInfo}"
              println "my params are $params"
    }
  }
}

