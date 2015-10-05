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

def res = rManager.queryNames(query, ontology).groupBy { it.label }
print new JsonBuilder(res)


//.sort { it.first_label.size() }).toString()
