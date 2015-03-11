
// Run a query and ting

import groovy.json.*

import org.json.simple.JSONValue;
import com.google.gson.Gson;

if(!application) {
  application = request.getApplication(true)
}
def query = request.getParameter('query')
def type = request.getParameter('type')
def ontology = request.getParameter('ontology')
def rManager = application.rManager

if(type == null) {
  type = 'all'
}
if(ontology == null) {
  ontology = ''
}

try {
  def results = new HashMap()
  def start = System.currentTimeMillis()

  def out = rManager.runQuery(query, type, ontology)

  def end = System.currentTimeMillis()

  results.put('time', (end - start))
  results.put('result', out)

  response.contentType = 'application/json'
      /*Gson gson = new Gson();
      println gson.toJson(results);*/
  //print JSONValue.toJSONString(results)
  print new JsonBuilder(results).toString()
} catch(Exception e) {
  print e
}
