// Run a query and ting

import src.util.Util

import groovy.json.*

if(!application) {
  application = request.getApplication(true)
}

def params = Util.extractParams(request)

def query = params.query
def ontology = params.ontology
def rManager = application.rManager

if(ontology && query) {
  query = java.net.URLDecoder.decode(query, "UTF-8")
  try {
    def resultMap = [:] 
    
    def fOut = rManager.runQuery(query, 'equivalent', ontology, -1, true, false).toArray()
    if (fOut.size() > 0) { // class found
      ArrayList out = null
      resultMap['classes'] = fOut
      resultMap['children'] = rManager.runQuery(query, 'subclass', ontology, -1, true, false).toArray()
      def thingFound = false
      while(thingFound == false) {
	out = rManager.runQuery(query, 'superclass', ontology, -1, true, false).toArray()
	
	//	if(!out || (out && out[0].owlClass == '<http://www.w3.org/2002/07/owl#Thing>')) {
	if(!out || (out && out.findAll{it.owlClass == '<http://www.w3.org/2002/07/owl#Thing>'}.size()>0)) {
	  thingFound = true
	} else {
	  def oldquery = query
	  query = out[0].owlClass
	  def nOut = rManager.runQuery(query, 'subclass', ontology, -1, true, false).findAll { it.owlClass != oldquery }.sort { it.label[0] }
	  resultMap.classes += nOut
	  out[0]["children"] = resultMap

	  resultMap = [ 'classes': out ]
	}
      }
      
      def nOut = rManager.runQuery('<http://www.w3.org/2002/07/owl#Thing>', 'subclass', ontology, -1, true, false).findAll { it.owlClass != query }.sort { it.label[0] }
      
      // THIS WORKS ON GOOD GROOVY VERSIONS > 2.0
      resultMap.classes += nOut
      
      resultMap['chosen'] = fOut[0]
      
    } else {
      resultMap['classes'] = rManager.runQuery('<http://www.w3.org/2002/07/owl#Thing>', 'subclass', ontology, -1, true, false).sort { it.label[0] }
    }
    response.contentType = 'application/json'
    print new JsonBuilder(resultMap).toString()
  } catch(Exception e) {
	e.printStackTrace()
  }
} else {
  println 'missing stuff'
}


