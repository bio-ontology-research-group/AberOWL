// Run a query and ting

import groovy.json.*

import org.json.simple.JSONValue;
import com.google.gson.Gson;
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
import src.util.Util

import util.*;

if(!application) {
  application = request.getApplication(true)
}
def params = Util.extractParams(request)
def query = params.term
def ontology = params.ontology
def prefix = params.prefix?:"false"
def rManager = application.rManager

prefix = Boolean.valueOf(prefix)

response.contentType = 'application/json'

if (!prefix) {
  def res = rManager.queryNames(query, ontology).groupBy { it.label }
  print new JsonBuilder(res)
} else { // prefix query
  query = classic.QueryParser.escape(query) + '*'
  String[] fields = ["label", "synonym"]
  def parser = new classic.MultiFieldQueryParser(fields, new WhitespaceAnalyzer())
  def parser2 = new SimpleQueryParser(new WhitespaceAnalyzer(), "ontology")
  def q1 = parser.parse(query)
  def q2 = parser2.parse(ontology)
  def bq = new BooleanQuery()
  bq.add(q1, BooleanClause.Occur.MUST);
  bq.add(q2, BooleanClause.Occur.MUST);

  def rmap = [:]
  def result = rManager.searcher.search(bq, 100).scoreDocs
  result.each { h ->
    def hitDoc = rManager.searcher.doc(h.doc)
    //    def output = [:].withDefault { new TreeSet() }
    def lab = hitDoc.get("label")
    def url = hitDoc.get("class")
    rmap[lab] = url
  }
  print new JsonBuilder(rmap.sort { it.key.length() })
}
