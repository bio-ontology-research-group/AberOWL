// Run a stats and ting

import groovy.json.*
import org.json.simple.JSONValue;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.*
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;

if(!application) {
  application = request.getApplication(true)
}

def ontology = request.getParameter('ontology')
def rManager = application.rManager

if(ontology) {
	def ont = rManager.ontologies.get(ontology)
	def stats = [
	  'rBoxAxiomCount': 0,
	  'tBoxAxiomCount': 0,
	  'totalAxiomCount': 0,
	  'logicalAxiomCount': 0,
	  'complexity': 0,
	  'classCount': ont.getClassesInSignature(true).size(),
          'loaded': rManager.ontologies.get(ontology) != null,
	  'consistent': rManager.queryEngines.get(ontology) != null
	]

	AxiomType.TBoxAxiomTypes.each { ont.getAxioms(it, true).each { 
	  stats.totalAxiomCount += 1
	  stats.tBoxAxiomCount += 1
	} }
	AxiomType.RBoxAxiomTypes.each { ont.getAxioms(it, true).each { 
	  stats.totalAxiomCount += 1 
	  stats.rBoxAxiomCount += 1 
	} }

	stats.complexity = stats.totalAxiomCount / stats.classCount
	stats.logicalAxiomCount = ont.getLogicalAxiomCount()
response.contentType = 'application/json'
print JSONValue.toJSONString(stats)
} else {
	stats = rManager.getStats()
response.contentType = 'application/json'
print JSONValue.toJSONString(stats)
}

