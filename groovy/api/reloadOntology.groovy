import groovy.json.*

public final static String API_KEY = '7LWB1EK24e8Pj7XorQdG9FnsxQA3H41VDKIxN1BeEv5n'

def name = request.getParameter('name')
def rManager = application.rManager

def result = [
  'err': null,
  'msg': 'Loading'
]

print new JsonBuilder(results).toString()

rManager.reloadOntology(name)

// Get result and whatnot here
