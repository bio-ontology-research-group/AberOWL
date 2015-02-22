@Grapes([
          @Grab(group='org.slf4j', module='slf4j-log4j12', version='1.7.10'),
          @Grab(group='net.sourceforge.owlapi', module='owlapi-distribution', version='4.0.1'),
          @Grab(group='org.semanticweb.elk', module='elk-owlapi', version='0.4.1'),
          @Grab(group='org.codehaus.gpars', module='gpars', version='1.1.0'),
	  @GrabConfig(systemClassLoader=true)
	])

import groovy.json.*

println "Generating queries"

def oInput = new File('labels.json')
def labels = new JsonSlurper().parseText(oInput.text)
def allOntologies = labels.keySet()
def owlQueries = []

for(String ontology : allOntologies) {
  def oLabels = labels[ontology]
  owlQueries.put(ontology, [])

  // 25 basic class pulls
  for(int x=0;x<75;x++) {
    def randomLabel = oLabels[random.nextInt(oLabels.size())]
    
    def type = 'equivalent'
    if(x > 25) {
      type = 'subclass'
    } else if(x > 50) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'query': fixLabel(randomLabel)
    ])
  }

  // 25 A and B
  for(int y=0;y<75;y++) {
    def labelOne = oLabels[random.nextInt(oLabels.size())]
    def labelTwo = oLabels[random.nextInt(oLabels.size())]

    def type = 'equivalent'
    if(y > 25) {
      type = 'subclass'
    } else if(y > 50) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'query': owlQueries[ontology].add(fixLabel(labelOne) + ' and ' + fixLabel(labelTwo))
    ])
  }

  // 25 A some B
  for(int z=0;z<75;z++) {
    def labelOne = oLabels[random.nextInt(oLabels.size())]
    def labelTwo = oLabels[random.nextInt(oLabels.size())]

    def type = 'equivalent'
    if(z > 25) {
      type = 'subclass'
    } else if(z > 50) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'query': owlQueries[ontology].add(fixLabel(labelOne) + ' some ' + fixLabel(labelTwo))
    ])

  }

  // 25 A and B some C
  for(int a=0;a<75;a++) {
    def labelOne = oLabels[random.nextInt(oLabels.size())]
    def labelTwo = oLabels[random.nextInt(oLabels.size())]
    def labelThree = oLabels[random.nextInt(oLabels.size())]

    def type = 'equivalent'
    if(a > 25) {
      type = 'subclass'
    } else if(a > 50) {
      type = 'superclass'
    }

    owlQueries[ontology].add([
      'type': type,
      'query': owlQueries[ontology].add(fixLabel(labelOne) + ' and ' + fixLabel(labelTwo) + ' some ' + fixLabel(labelThree))
    ])
  }
}

def fixLabel(term) {
  if(term.contains(' ') && !term.contains('\'')) {
    term = '\'' + term + '\''
  }
  return term
}

def oOutput = new File('queries.json')
oOutput.write(new JsonBuilder(owlQueries).toPrettyString())

println "Done"
