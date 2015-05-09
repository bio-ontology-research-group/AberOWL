package src

import org.semanticweb.owlapi.model.*
import org.semanticweb.owlapi.expression.OWLEntityChecker

public class BasicEntityChecker implements OWLEntityChecker {
   private final OWLDataFactory dFactory;


    public BasicEntityChecker(OWLDataFactory dFactory) {
        this.dFactory = dFactory;
    }

    @Override
    public OWLClass getOWLClass(String name) {
        println "getting " + new IRI(name)
        println "result wos " + dFactory.getOWLClass(new IRI(name))
        return dFactory.getOWLClass(new IRI(name))
    }


    @Override
    public OWLDataProperty getOWLDataProperty(String name) {
        return dFactory.getOWLDataProperty(new IRI(name))
    }


    @Override
    public OWLDatatype getOWLDatatype(String name) {
        return dFactory.getOWLDataType(new IRI(name))
    }


    @Override
    public OWLNamedIndividual getOWLIndividual(String name) {
        return dFactory.getOWLNamedIndividual(new IRI(name))
    }


    @Override
    public OWLObjectProperty getOWLObjectProperty(String name) {
        return dFactory.getOWLObjectProperty(new IRI(name))
    }

    @Override
    public OWLAnnotationProperty getOWLAnnotationProperty(String name) {
        return dFactory.getOWLAnnotationProperty(new IRI(name))
    }
}
