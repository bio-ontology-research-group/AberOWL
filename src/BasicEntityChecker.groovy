package src

import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.expression.OWLEntityChecker

public class BasicEntityChecker implements OWLEntityChecker {
   private final OWLDataFactory dFactory;
   private final OWLOntology ontology;


    public BasicEntityChecker(OWLDataFactory dFactory, OWLOntology ontology) {
        this.dFactory = dFactory;
        this.ontology = ontology;
    }

    @Override
    public OWLClass getOWLClass(String name) {
        name = name.replaceAll("<","").replaceAll(">","") 
        def iri = new IRI(name)
        def result = null
        if(ontology.containsClassInSignature(iri)) {
          result = dFactory.getOWLClass(iri)
        }
        return result
    }


    @Override
    public OWLDataProperty getOWLDataProperty(String name) {
        name = name.replaceAll("<","").replaceAll(">","") 
        def iri = new IRI(name)
        def result = null
        if(ontology.containsDataPropertyInSignature(iri)) {
          result = dFactory.getOWLDataProperty(iri)
        }
        return result
    }


    @Override
    public OWLDatatype getOWLDatatype(String name) {
        name = name.replaceAll("<","").replaceAll(">","") 
        def iri = new IRI(name)
        def result = null
        if(ontology.containsDataTypeInSignature(iri)) {
          result = dFactory.getOWLDataType(iri)
        }
        return result
    }


    @Override
    public OWLNamedIndividual getOWLIndividual(String name) {
        name = name.replaceAll("<","").replaceAll(">","") 
        def iri = new IRI(name)
        def result = null
        if(ontology.containsIndividualInSignature(iri)) {
          result = dFactory.getOWLNamedIndividual(iri)
        }
        return result
    }


    @Override
    public OWLObjectProperty getOWLObjectProperty(String name) {
        name = name.replaceAll("<","").replaceAll(">","") 
        def iri = new IRI(name)
        def result = null
        if(ontology.containsObjectPropertyInSignature(iri)) {
          result = dFactory.getOWLObjectProperty(iri)
        }
        return result
    }

    @Override
    public OWLAnnotationProperty getOWLAnnotationProperty(String name) {
        name = name.replaceAll("<","").replaceAll(">","") 
        def iri = new IRI(name)
        def result = null
        if(ontology.containsAnnotationPropertyInSignature(iri)) {
          result = dFactory.getOWLAnnotationProperty(iri)
        }
        return result
    }
}
