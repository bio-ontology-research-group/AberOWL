// Run a query and ting

//import src.util.Util
import groovy.json.*
import org.eclipse.jetty.server.Request
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.ContentType


def extractParams(Request request) throws IOException {
  if ("POST".equalsIgnoreCase(request.getMethod())) {
    Scanner s = new Scanner(request.getInputStream(), "UTF-8").useDelimiter("\\A");
    return new JsonSlurper().parseText(s.hasNext() ? s.next() : "");
  } else {
    def params = [:]
    request.getParameterNames().each { params[it] = request.getParameter(it) }
        return params
  }
}

if(!application) {
  application = request.getApplication(true)
}
def params = extractParams(request)

def query = params.query
def objectProperty = params.objectProperty
def rManager = application.rManager;

def PREFIX_MAP = [
  "http://www.geneontology.org/formats/oboInOwl#" : "oboInOwl:",
  "http://purl.org/dc/elements/1.1/" : "dc:",
  "http://protege.stanford.edu/plugins/owl/protege#" : "protege:",
  "http://purl.org/dc/terms/" : "dc:",
  "http://purl.org/dc/elements/1.1/" : "dc:",
  "http://purl.org/dc/terms/" : "dc:",
  "http://purl.org/vocab/vann/" : "vann:",
  "http://purl.org/spar/cito/" : "cito:",
  "http://purl.org/sig/ont/fma/" : "fma:",
  "http://purl.obolibrary.org/obo/" : "obo:",
  "http://www.w3.org/2004/02/skos/core" : "skos:",
  "http://semanticscience.org/resource/" : "sio:"
]

def prefixUrls = { String s ->
  PREFIX_MAP.keySet().each { prefix ->
    if (s.startsWith(prefix)) {
      s = s.replaceAll(prefix, PREFIX_MAP[prefix])
    }
  }
  s
}

def PREFIX_MAP2 = [
  "reactome:" : ["http://www.reactome.org/content/query?cluster=true&q=", true],
  "pmid:" : ["http://www.ncbi.nlm.nih.gov/pubmed/", false],
  "nci:" : ["http://aber-owl.net/ontology/NCIT#!http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23", true],
  "ncit:" : ["http://aber-owl.net/ontology/NCIT#!http%3A%2F%2Fncicb.nci.nih.gov%2Fxml%2Fowl%2FEVS%2FThesaurus.owl%23", true],
  "url:" : ["", false],
  "msh:" : ["http://aber-owl.net/ontology/RH-MESH#!http%3A%2F%2Fphenomebrowser.net%2Fontologies%2Fmesh%2Fmesh.owl%23", true],
  "mesh:" : ["http://aber-owl.net/ontology/RH-MESH#!http%3A%2F%2Fphenomebrowser.net%2Fontologies%2Fmesh%2Fmesh.owl%23", true]
]

def prefixUrls2 = { String s ->
  PREFIX_MAP2.keySet().each { prefix ->
    if (s.startsWith(prefix)) {
      def last = s.replaceAll(prefix, "")
      if (PREFIX_MAP2[prefix][1]) {
	last = last.toUpperCase()
      }
      s = '<a href="'+PREFIX_MAP2[prefix][0]+last+'">'+prefix+last+'</a>'
    }
  }
  s
}

def search(def type, def map) {
  def url = 'http://127.0.0.1:9200'
  def http = new HTTPBuilder(url)
  def j = new groovy.json.JsonBuilder(map)
  try {
    def t 
    http.post( path: '/aberowl/'+type+'/_search', body: j.toPrettyString() ) { resp, reader ->
      t = reader
    }
    http.shutdown()
    return t
  } catch (Exception E) {
    E.printStackTrace()
    println j.toPrettyString()
  }
}

if(ontology && query) {
  query = java.net.URLDecoder.decode(query, "UTF-8")
  try {
    def results = [:]
    //def results = []
    def start = System.currentTimeMillis()

    def fQuery = ["query": ["bool":["must":[]]]]
    def ll = []
    ll << ["term" : ["class" : query]]
    ll << ["term" : ["ontology" : ontology]]
    ll.each {
      fQuery.query.bool.must << it
    }

    def hits = search("owlclass", fQuery)

    def hitDoc = hits.hits.hits[0]._source
    def output = [:].withDefault { new TreeSet() }
    hitDoc.keySet().each { fieldName ->
      if (! (fieldName in ["oldVersion", "first_label", "AberOWL-catch-all"])) {
	if (hitDoc[fieldName] instanceof List) {
	  hitDoc[fieldName].each {
	    output[prefixUrls(fieldName)].add(prefixUrls2(it))
	  }
	} else {
	  output[prefixUrls(fieldName)].add(prefixUrls2(hitDoc[fieldName]))
	}
      }
    }
    output = output.sort { x, y ->
      if (x.key == 'label') {
	-1
      } else if (y.key == 'label') {
	1
      } else if (x.key == 'definition' && y.key != 'label') {
	-1
      } else if (x.key == 'oboid' && y.key != 'label' && y.key != 'definition') {
	-1
      } else if (x.key == 'AberOWL-equivalent' && y.key != 'oboid' && y.key != 'label' && y.key != 'definition') {
	-1
      } else if (x.key == 'AberOWL-subclass' && y.key != 'oboid' && y.key != 'label' && y.key != 'definition') {
	-1
      } else if (x.key == 'AberOWL-disjoint' && y.key != 'oboid' && y.key != 'label' && y.key != 'definition') {
	-1
      } else {
	x.key.compareTo(y.key)
      }
    }
    
    def translateKeys = ["AberOWL-equivalent" : "EquivalentTo:", "AberOWL-subclass" : "SubClassOf:", "AberOWL-disjoint" : "DisjointWith:"]
    output = output.inject ([:]) { map, v -> 
      if (translateKeys[v.key]) {
	map[ translateKeys[ v.key ] ] = v.value.sort{it.length()}.inject("", {s, val -> s + "<div id='man-axiom'>"+val+"</div>"})
      } else {
	map[ v.key ] = v.value
      }
      map 
    }
    
    response.contentType = 'application/json'
    print new JsonBuilder(output).toString()
  } catch(Exception e) {
    
    print new JsonBuilder([:]).toString()
  }
}else if(ontology && objectProperty){
  try {

    def result = rManager.getInfoObjectProperty(ontology,objectProperty);
    def output = [:]
    result.keySet().each { key ->
      output[key] = result.get(key)
    }
    response.contentType = 'application/json'
    print new JsonBuilder(output).toString()
  } catch(Exception e) {
    print new JsonBuilder([:]).toString()
  }
}else {
  response.setStatus(400)
  println new JsonBuilder([ 'err': true, 'message': 'Missing parameters. Please refer to the API documentation.' ]).toString() 
}


