@Grapes([
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.0.1'),
          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
	  @GrabConfig(systemClassLoader=true)
	])

import groovy.json.*

def rManager = new RequestManager(false)

def allOntologies = rManager.labels2id.keySet().toArray()
def labels = new HashMap()

for(String ontology : allOntologies) {
  labels.put(ontology, rManager.labels2id[ontology].keySet().toArray())
}

def oOutput = new File('labels.json')
oOutput.write(new JsonBuilder(labels).toPrettyString())
