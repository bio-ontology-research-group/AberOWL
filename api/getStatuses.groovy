import groovy.json.*
import org.json.simple.JSONValue;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import src.util.Util

if(!application) {
  application = request.getApplication(true)
}

def rManager = application.rManager

response.contentType = 'application/json'
print JSONValue.toJSONString(rManager.loadStati)
