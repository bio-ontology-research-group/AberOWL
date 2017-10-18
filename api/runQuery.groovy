// Run a query and ting

import groovy.json.*
import org.apache.log4j.*
import groovy.util.logging.*
import src.util.Util

if(!application) {
  application = request.getApplication(true)
}
if (!application.log) {
  Logger log = Logger.getInstance(getClass())
  log.level = Level.INFO
  // add an appender to log to file
  log.addAppender(new RollingFileAppender(new TTCCLayout(), 'queries.log', true))
  application.log = log
  log.info 'Logger created'
}
def log = application.log
def params = Util.extractParams(request)

def query = params.query
def type = params.type
def direct = params.direct
def labels = params.labels
def sVersion = null
def rManager = application.rManager

if(type == null) {
  type = 'all'
}
if(sVersion == null || sVersion == '0') {
  sVersion = '-1';
}
if(direct == null) {
  direct = "false"
}
if (direct == "true") {
  direct = true
} else {
  direct = false
}

if(labels == null) {
  labels = 'false'
}
if (labels == 'false') {
  labels = false
} else {
  labels = true
}

response.contentType = 'application/json'

try {
  def results = new HashMap()
  def start = System.currentTimeMillis()
  def iVersion = Integer.parseInt(sVersion);

  def out = rManager.runQuery(query, type, direct, labels)
  def end = System.currentTimeMillis()
  results.put('time', (end - start))
  results.put('result', out)

  def logstring = ""
  logstring += query?:""
  logstring += "\t"+(type?:"")
  logstring += "\t"+(direct?:"")
  logstring += "\t"+(labels?:"")
  logstring += "\t"+(out.size()?:"")
  logstring += "\t"+((end - start)?:"")
  log.info logstring

  results['result'] = results['result'].sort { a,b ->
    if((a.containsKey("label"))&&(b.containsKey("label"))) {
      return (a.get("label").compareTo(b.get("label")));
    }else if(a.containsKey("label")){
      return(1);
    }else if(b.containsKey("label")){
      return(-1);
    }else{
      return(0);
    }
  }
  print new JsonBuilder(results).toString()
} catch(java.lang.IllegalArgumentException e) {
  response.setStatus(400)
  print new JsonBuilder([ 'error': true, 'message': 'Ontology not found.' ]).toString() 
} catch(org.semanticweb.owlapi.manchestersyntax.renderer.ParserException e) {
  response.setStatus(400)
  print new JsonBuilder([ 'error': true, 'message': 'Query parsing error: ' + e.getMessage() ]).toString() 
} catch(Exception e) {
  response.setStatus(400)
  print new JsonBuilder([ 'error': true, 'message': 'Generic query error: ' + e.getMessage() ]).toString() 
}

