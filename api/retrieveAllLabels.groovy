// Run a query and ting

import groovy.json.*
import org.apache.log4j.*
import groovy.util.logging.*

if(!application) {
  application = request.getApplication(true)
}

def ontology = request.getParameter('ontology')
def rManager = application.rManager

if(ontology) {
  query = java.net.URLDecoder.decode(query, "UTF-8")
  def output = new TreeSet()
  try {
    def results = [:]
    //def results = []
    def start = System.currentTimeMillis()
    def bq = new BooleanQuery()
    //    bq.add(new TermQuery(new Term('class', query)), BooleanClause.Occur.MUST);
    bq.add(new TermQuery(new Term('ontology', ontology)), BooleanClause.Occur.MUST);

    def results = rManager.searcher.search(bq, 100000).scoreDocs
    results.each { result ->
      def hitDoc = rManager.searcher.doc(result.doc)
      hitDoc.each { fieldName ->
	if (fieldName.name in ["label", "synonym"]) {
	  hitDoc.getValues(fieldName.name).each {
	    output.add(it)
	  }
	}
      }
    }
  }
  print new JsonBuilder(output).toString()
} else {
  response.setStatus(400)
  println new JsonBuilder([ 'err': true, 'message': 'Missing parameters. Please refer to the API documentation.' ]).toString() 
}

