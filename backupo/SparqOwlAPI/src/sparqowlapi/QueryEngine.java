/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package sparqowlapi;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.util.ShortFormProvider;

/**
 *
 * @author reality
 */
public class QueryEngine {
    OWLReasoner oReasoner;
    QueryParser parser;
    
    QueryEngine(OWLReasoner oReasoner, ShortFormProvider sProvider) {
        this.oReasoner = oReasoner;
        this.parser = new QueryParser(oReasoner.getRootOntology(), sProvider);
    }
    
    public Set<OWLClass> getClasses(String mOwl) {
        if(mOwl.trim().length() == 0) {
            return Collections.emptySet();
        }
        OWLClassExpression cExpression = parser.parse(mOwl);
        Set<OWLClass> classes = new HashSet<OWLClass>();
  
        classes.addAll(sClasses(cExpression));
        classes.addAll(eClasses(cExpression));
        classes.addAll(subClasses(cExpression));
        //classes.addAll(instances(cExpression));

        return classes;
    }
    
    // TODO: Get classes etc

    private Set<OWLClass> sClasses(OWLClassExpression cExpression) {
        return oReasoner.getSuperClasses(cExpression, true).getFlattened();
    }
    
    private Set<OWLClass> eClasses(OWLClassExpression cExpression) {
        Node<OWLClass> equivalentClasses = oReasoner.getEquivalentClasses(cExpression);
        Set<OWLClass> result;
        if(cExpression.isAnonymous()) {
            result = equivalentClasses.getEntities();
        } else {
            result = equivalentClasses.getEntitiesMinus(cExpression.asOWLClass());
        }
        return result;
    }
    
    private Set<OWLClass> subClasses(OWLClassExpression cExpression) {
        NodeSet<OWLClass> subClasses = oReasoner.getSubClasses(cExpression, true);
        return subClasses.getFlattened();
    }
    
    // TODO: Not sure how 2
    /*private Set<OWLClass> instances(OWLClassExpression cExpression) {
        NodeSet<OWLNamedIndividual> individuals = oReasoner.getInstances(cExpression, true);
        return individuals.getFlattened();
    }*/
}