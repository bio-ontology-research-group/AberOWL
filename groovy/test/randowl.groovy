@Grapes([
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.0.1'),
          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.1'),
          @Grab(group='com.googlecode.json-simple', module='json-simple', version='1.1.1'),
          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
	  @GrabConfig(systemClassLoader=true)
	])

import groovy.json.*

println "Generating queries"

def oInput = new File('labels.json')

def labels = new JsonSlurper().setType(JsonParserType.CHARACTER_SOURCE).parseText(oInput.text)
def allOntologies = labels.keySet()
def owlQueries = new HashMap()
def random = new Random()

println "starting"

for(String ontology : allOntologies) {
  def oLabels = labels[ontology]
  owlQueries.put(ontology, [])

  if(oLabels.size() == 0) {
    continue;
  }

  println ontology + ': ' + oLabels.size()
  // 25 basic class pulls
  for(int x=0;x<300;x++) {
    def randomLabel = oLabels[random.nextInt(oLabels.size())]

    def type = 'equivalent'
    if(x > 100) {
      type = 'subclass'
    } else if(x > 200) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'class': 'simple', 
      'query': fixLabel(randomLabel)
    ])
  }

  // 25 A and B
  for(int y=0;y<300;y++) {
    def labelOne = oLabels[random.nextInt(oLabels.size())]
    def labelTwo = oLabels[random.nextInt(oLabels.size())]

    def type = 'equivalent'
    if(y > 100) {
      type = 'subclass'
    } else if(y > 200) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'class': 'conjunctive', 
      'query': fixLabel(labelOne) + ' and ' + fixLabel(labelTwo)
    ])
  }

  // 25 A some B
  for(int z=0;z<300;z++) {
    def labelOne = oLabels[random.nextInt(oLabels.size())]
    def labelTwo = oLabels[random.nextInt(oLabels.size())]

    def type = 'equivalent'
    if(z > 100) {
      type = 'subclass'
    } else if(z > 200) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'class': 'some',
      'query': fixLabel(labelOne) + ' some ' + fixLabel(labelTwo)
    ])
  }

  // 25 A and B some C
  for(int a=0;a<300;a++) {
    def labelOne = oLabels[random.nextInt(oLabels.size())]
    def labelTwo = oLabels[random.nextInt(oLabels.size())]
    def labelThree = oLabels[random.nextInt(oLabels.size())]

    def type = 'equivalent'
    if(a > 100) {
      type = 'subclass'
    } else if(a > 200) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'class': 'conjunctive_some',
      'query': fixLabel(labelOne) + ' and ' + fixLabel(labelTwo) + ' some ' + fixLabel(labelThree)
    ])
  }

  println "Generated " + ontology + " queries"
}

println owlQueries
def oOutput = new File('queries.json')
oOutput.write(new JsonBuilder(owlQueries).toPrettyString())
println "Done"

def fixLabel(term) {
  if(term.contains(' ') && !term.contains('\'')) {
    term = '\'' + term + '\''
  }
  return term
}
