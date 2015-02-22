@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0')

import groovyx.net.http.HTTPBuilder
import java.util.concurrent.*
import groovyx.gpars.ParallelEnhancer
import groovyx.gpars.GParsPool

def oInput = new File('queries.json')
def queries = new JsonSlurper().parseText(oInput.text)
def allOntologies = labels.keySet()

for(String ontology : allOntologies) {
  println "[TEST] Starting " + ontology + " test"
  def start = System.currentTimeMillis()

  GParsPool.withPool(20) {
    queries[ontology].eachParallel { line ->
      def equiv = new HTTPBuilder()
      equiv.get( uri: 'http://localhost:30003/api/runQuery.groovy', query: [ 'query': line, 'ontology': ontology 'type': 'equivalent' ] ) { eResp, c ->
        println c
        equiv.shutdown()
      }

      def sub = new HTTPBuilder()
      sub.get( uri: 'http://localhost:30003/api/runQuery.groovy', query: [ 'query': line, 'ontology': ontology, 'type': 'subclass' ] ) { eResp, c ->
        println c 
        sub.shutdown()
      }

      def sup = new HTTPBuilder()
      sup.get( uri: 'http://localhost:30003/api/runQuery.groovy', query: [ 'query': line, 'ontology': ontology, 'type': 'superclass' ] ) { eResp, c ->
        println c 
        sup.shutdown()
      }
    }
  }

  def end = System.currentTimeMillis()
  println('[TEST] Took ' + (end - start) + 'ms to get ' + queries[ontology].size() + ' queries from ' + ontology)
}
