import groovy.json.*
import org.json.simple.JSONValue;

import org.apache.lucene.analysis.*
import org.apache.lucene.analysis.standard.StandardAnalyzer
import org.apache.lucene.analysis.core.WhitespaceAnalyzer
import org.apache.lucene.document.*
import org.apache.lucene.index.*
import org.apache.lucene.store.*
import org.apache.lucene.util.*
import org.apache.lucene.search.*
import org.apache.lucene.queryparser.*
import org.apache.lucene.queryparser.simple.*
import org.apache.lucene.search.highlight.*
import org.apache.lucene.index.IndexWriterConfig.OpenMode

def rManager = application.rManager
response.contentType = 'application/json'

def ontology = request.getParameter('ontology')

if(ontology) {
  //  query = java.net.URLDecoder.decode(query, "UTF-8")
  def output = new TreeSet()
  try {
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
  } catch (Exception E) {}
  print new JsonBuilder(output).toString()
} else {
  def keys = rManager.queryEngines.keySet().toArray().sort(false)
  print new JsonBuilder(keys).toString()
}

