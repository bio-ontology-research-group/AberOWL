// Run a query and ting

import groovy.json.*

import org.json.simple.JSONValue;
import com.google.gson.Gson;
import src.util.Util
import groovyx.net.http.HTTPBuilder

import util.*;

def search(def type, def map) {
  def url = 'http://10.81.0.162:9200'
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

  def m = ["query": ["bool":["must":[]]]]
  def ll = []
  ll << ["prefix" : ["synonym" : query]]
  ll << ["term" : ["ontology" : ontology]]
  ll.each {
    m.query.bool.must << it
  }
  def hits = search("owlclass", m)
  def results = hits.hits.hits.collect {it._source}
  
  results.each { h ->
    //    def output = [:].withDefault { new TreeSet() }
    def lab = h["label"]
    def url = h["class"]
    rmap[lab] = url
  }
  print new JsonBuilder(rmap.sort { it.key.length() })
}
