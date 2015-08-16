import groovy.json.*

def API_KEY = '7LWB1EK24e8Pj7XorQdG9FnsxQA3H41VDKIxN1BeEv5n'

def name = request.getParameter('name')
def sVersion = request.getParameter('version')
def rManager = application.rManager

def result = [
  'err': null,
  'msg': 'Loading'
]

print new JsonBuilder(result).toString()

if(sVersion == null) {
	sVersion = '-1';
}

try{
	def version = Integer.parseInt(sVersion);
	rManager.reloadOntology(name,version)
}catch(Exception e){
	print e
}
// Get result and whatnot here
