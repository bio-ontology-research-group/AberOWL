@Grab(group='org.codehaus.groovy.modules.http-builder', module='http-builder', version='0.7' )
@Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0')

import groovyx.net.http.HTTPBuilder
import groovyx.net.http.*
import java.util.concurrent.*
import groovyx.gpars.ParallelEnhancer
import groovyx.gpars.GParsPool
import groovy.json.*

def oInput = new File('queries.json')
def queries = new JsonSlurper().parseText(oInput.text)
def allOntologies = queries.keySet()
def results = new HashMap()

for(String ontology : allOntologies) {
  println "[TEST] Starting " + ontology + " test"
  results.put(ontology, [])
  def start = System.currentTimeMillis()

  GParsPool.withPool {
    queries[ontology].eachParallel { line ->
      def equiv = new HTTPBuilder()
      try {
        equiv.get( uri: 'http://localhost:30003/api/runQuery.groovy', query: [ 'query': line.query, 'ontology': ontology, 'type': line.type ] ) { eResp, c ->
          println "[TEST] " + ontology + " GOT RESPONSE in " + c['time']
          results[ontology].add([
            'query': line.query,
            'type': line.type,
            'time': c['time']
          ])
          equiv.shutdown()
        }
      } catch (HttpResponseException e) {
        println "FAIL"
      }
    }
  }

  def end = System.currentTimeMillis()
  println('[TEST] Took ' + (end - start) + 'ms to get ' + queries[ontology].size() + ' queries from ' + ontology)
}

def oOutput = new File('results.json')
oOutput.write(new JsonBuilder(results).toPrettyString())
