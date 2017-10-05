// Run a query and ting

import groovy.json.*

import org.json.simple.JSONValue;
import com.google.gson.Gson;
import groovyx.net.http.HTTPBuilder
import groovyx.net.http.Method
import groovyx.net.http.ContentType

import util.*;

import src.util.Util

if(!application) {
  application = request.getApplication(true)
}
def params = Util.extractParams(request)
def query = params.term
def rManager = application.rManager

url = 'http://127.0.0.1:9200/' // Elastic endpoint

def search(def type, def map) {

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

Set<String> queryOntologies(String query) {
  if (query) {
    String[] fields = ['name', 'lontology', 'description']
    
    def oQuery = query
    def omap = [:]
    omap = ["query": ["bool" : ["should" : []]]]
    fields.each { f ->
      def m = ["match": ["${f}": ["query":oQuery]]]
      omap.query.bool.should << m
    }
    def hits = search("ontology", omap)
    def ret = []
    hits.hits.hits.each { 
      def temp = it._source
      temp.value = temp.first_label
      temp.data = temp["class"]
      ret << temp
    }
    return ret.sort { it.name.size() }
  } else {
    return []
  }
}



response.contentType = 'application/json'
print new JsonBuilder(queryOntologies(query)).toString()
