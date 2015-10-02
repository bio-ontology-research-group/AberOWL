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


def PREFIX_MAP = [
  "http://www.geneontology.org/formats/oboInOwl#" : "oboInOwl:",
  "http://purl.org/dc/elements/1.1/" : "dc:",
  "http://protege.stanford.edu/plugins/owl/protege#" : "protege:",
  "http://purl.org/dc/terms/" : "dc:",
  "http://purl.org/dc/elements/1.1/" : "dc:",
  "http://purl.org/dc/terms/" : "dc:",
  "http://purl.org/vocab/vann/" : "vann:",
  "http://purl.org/spar/cito/" : "cito:",
  "http://purl.obolibrary.org/obo/" : "obo:",
  "http://www.w3.org/2004/02/skos/core" : "skos:",
  "http://semanticscience.org/resource/" : "sio:"
]

def prefixUrls = { String s ->
  PREFIX_MAP.keySet().each { prefix ->
    if (s.startsWith(prefix)) {
      s = s.replaceAll(prefix, PREFIX_MAP[prefix])
    }
  }
  s
}

if(ontology) {
  def ont = rManager.ontologies.get(ontology)
  def stats = [
    'rBoxAxiomCount': 0,
	       'tBoxAxiomCount': 0,
	       'totalAxiomCount': 0,
	       'unsatisfiableClassesCount':0,
	       'logicalAxiomCount': 0,
	       'complexity': 0,
	       'annotations':[:],
	       'classCount': ont.getClassesInSignature(true).size(),
	       'loaded': rManager.ontologies.get(ontology) != null,
	       'consistent': rManager.queryEngines.get(ontology) != null
  ]

  def unsatis = rManager.runQuery("<http://www.w3.org/2002/07/owl#Nothing>", "equivalent", ontology, -1, false, false)
  stats.unsatisfiableClassesCount = unsatis.size()
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

  ont.getAnnotations().each { a ->
    try {
      def prop = a.getProperty().toString()?.replaceAll("<","")?.replaceAll(">","")
      prop = prefixUrls(prop)
      def val = a.getValue()?.getLiteral()?.toString()
      stats['annotations'][prop] = val
    } catch (Exception E) {}
  }
  
  response.contentType = 'application/json'
  print JSONValue.toJSONString(stats)
} else {
  stats = rManager.getStats()
  response.contentType = 'application/json'
  print JSONValue.toJSONString(stats)
}
