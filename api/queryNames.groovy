// Run a query and ting

import groovy.json.*

import org.json.simple.JSONValue;
import com.google.gson.Gson;

import util.*;

if(!application) {
  application = request.getApplication(true)
}
def query = request.getParameter('term')
def ontology = request.getParameter('ontology')
def rManager = application.rManager

response.contentType = 'application/json'
print new JsonBuilder(rManager.queryNames(query, ontology)).toString()
