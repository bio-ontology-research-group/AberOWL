/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package sparqowlapi;

import org.coode.owlapi.manchesterowlsyntax.ManchesterOWLSyntaxEditorParser;
import org.semanticweb.owlapi.expression.OWLEntityChecker;
import org.semanticweb.owlapi.expression.ShortFormEntityChecker;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.util.BidirectionalShortFormProvider;
import org.semanticweb.owlapi.util.BidirectionalShortFormProviderAdapter;
import org.semanticweb.owlapi.util.ShortFormProvider;

/**
 *
 * @author reality
 */
public class QueryParser {
    BidirectionalShortFormProvider biSFormProvider;
    OWLOntology ontology;
    
    QueryParser(OWLOntology ontology, ShortFormProvider sProvider) {
        this.ontology = ontology;
        biSFormProvider = new BidirectionalShortFormProviderAdapter(
            ontology.getOWLOntologyManager(),
            ontology.getImportsClosure(),
            sProvider
        );
    }
    
    OWLClassExpression parse(String mOwl) {
        OWLDataFactory dFactory = this.ontology.getOWLOntologyManager().getOWLDataFactory();
        ManchesterOWLSyntaxEditorParser parser = new ManchesterOWLSyntaxEditorParser(dFactory, mOwl);
        parser.setDefaultOntology(ontology);
        OWLEntityChecker eChecker = new ShortFormEntityChecker(biSFormProvider);
        parser.setOWLEntityChecker(eChecker);
        
        return parser.parseClassExpression();
    }
}