package uk.ac.aber.lus11.sparqowlapi.request;


import java.io.*;
import org.semanticweb.owlapi.model.OWLClass;


public class MyOWLClassInformation implements Serializable {

    public OWLClass owlClass ;
    public String classURI ;
    public String ontologyURI ;
    public String label ;
    public String definition ;

}