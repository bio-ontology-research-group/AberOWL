// Run a query and ting

import groovy.json.*
import org.apache.log4j.*
import groovy.util.logging.*


if(!application) {
  application = request.getApplication(true)
}

if (!application.log) {
  Logger log = Logger.getInstance(getClass())
  log.level = Level.INFO
  // add an appender to log to file
  log.addAppender(new FileAppender(new TTCCLayout(), 'queries.log', true, true, 32768))
  application.log = log
  log.info 'Logger created'
}
def log = application.log

def query = request.getParameter('query')
def type = request.getParameter('type')
def ontology = request.getParameter('ontology')
def direct = request.getParameter('direct')
def labels = request.getParameter('labels')
def rManager = application.rManager


if(type == null) {
  type = 'all'
}
if(ontology == null) {
  ontology = ''
}
if(direct == null) {
  direct = ''
}
direct = direct.toBoolean()
if(labels == null) {
  labels = 'false'
}
labels = labels.toBoolean()

try {
  def results = new HashMap()
  def start = System.currentTimeMillis()

  def out = rManager.runQuery(query, type, ontology, direct, labels)

  
  def end = System.currentTimeMillis()

  results.put('time', (end - start))
  results.put('result', out)

  def logstring = ""
  logstring += query?:""
  logstring += "\t"+(type?:"")
  logstring += "\t"+(ontology?:"")
  logstring += "\t"+(direct?:"")
  logstring += "\t"+(labels?:"")
  logstring += "\t"+(out.size()?:"")
  logstring += "\t"+((end - start)?:"")
  log.info logstring

  response.contentType = 'application/json'
  print new JsonBuilder(results).toString()
} catch(Exception e) {
  print e
}
