package sparqowlapi;

import org.semanticweb.owlapi.model.OWLOntologyCreationException;

/**
 *
 * @author Luke Slater (lus11@aber.ac.uk)
 */
public class SparqOwlAPI {

    /**
     * @param args the command line arguments
     * @throws org.semanticweb.owlapi.model.OWLOntologyCreationException
     */
    public static void main(String[] args) throws OWLOntologyCreationException, Exception {
        RequestManager oManager = new RequestManager("/home/reality/NetBeansProjects/SparqOwlAPI/wine.rdf");
        //SparqOwlServer sServer = new SparqOwlServer(oManager);
    }
    
}