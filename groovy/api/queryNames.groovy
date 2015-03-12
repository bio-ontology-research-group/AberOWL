// Run a query and ting

import groovy.json.*

import org.json.simple.JSONValue;
import com.google.gson.Gson;

import uk.ac.aber.lus11.sparqowlapi.util.*

if(!application) {
  application = request.getApplication(true)
}
def query = request.getParameter('term')
def ontology = request.getParameter('ontology')
def rManager = application.rManager
def labels2id = rManager.labels2id

query = query.toLowerCase() ;
Set<String> results = new LinkedHashSet<>() ;
def tree = null ;
if (ontology == null || ontology.length()==0) { // query allLabels
    tree = rManager.allLabels ;
} else { // query ontology
    tree = rManager.labels.get(ontology) ;
}

if (tree !=null) {
    SuggestTree.Node n = tree.autocompleteSuggestionsFor(query) ;
    if (n != null) {
        for (int i = 0 ; i < n.listLength() ; i++) {
            String elem = n.listElement(i) ;
            String elemForOWL ;
            if (elem.indexOf(" ")>-1) {
                elemForOWL = "'"+elem+"'";
            } else {
                elemForOWL = elem ;
            }
            Map<String, Set<String>> s2id = null ;
            if  (ontology == null || ontology.length()==0) {
                s2id = rManager.allLabels2id ;
            } else {
                s2id = labels2id.get(ontology) ;
            }
            for (String id : s2id.get(elem)) {
                results.add(elemForOWL) ;
            }
        }
    }
}

print new JsonBuilder(results).toString()
