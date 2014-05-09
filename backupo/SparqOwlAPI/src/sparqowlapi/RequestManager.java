/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package sparqowlapi;

import com.google.gson.Gson;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.io.StringDocumentSource;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import sparqowlapi.util.NewShortFormProvider;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.reasoner.OWLReasoner;

import org.semanticweb.elk.owlapi.ElkReasonerFactory;


/**
 *
 * @author reality
 */
public class RequestManager {
    OWLOntologyManager oManager = OWLManager.createOWLOntologyManager();
    OWLOntology ontology;
    OWLReasoner oReasoner;
    QueryParser parser;
    NewShortFormProvider sForm;
    
    RequestManager(String ontologyFile) throws OWLOntologyCreationException {
        try {            
            // Create reasoner
            
            OWLDataFactory factory = oManager.getOWLDataFactory();
            
            List<OWLAnnotationProperty> aProperties = new ArrayList<OWLAnnotationProperty>();
            
            // Load ontology from file
            File file = new File(ontologyFile);
            OWLOntology localOntology = oManager.loadOntologyFromOntologyDocument(file);
            IRI ontology = oManager.getOntologyDocumentIRI(localOntology);
            
            System.out.print(localOntology);
            
            OWLAnnotationProperty rdfsLabel = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()) ;                                                                                           
            aProperties.add(rdfsLabel);
            
            OWLReasonerFactory reasonerFactory = new ElkReasonerFactory();
            OWLReasoner reasoner = reasonerFactory.createReasoner(localOntology);

            oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
            
            List<String> langs = getDefaultLanguages();
            Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<OWLAnnotationProperty, List<String>>();
            for (OWLAnnotationProperty annotationProperty : aProperties) {
                preferredLanguageMap.put(annotationProperty, langs);
            }
            
            this.sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, oManager);
        } catch(OWLOntologyCreationException e) {
            System.out.println("Failed to load ontology: " + e.getMessage());
        }
    }
    
    // stolen from http://smi-protege.stanford.edu/repos/protege/protege4/libraries/org.protege.owl.diff/trunk/src/main/java/org/protege/owl/diff/service/RenderingService.java
    public static List<String> getDefaultLanguages() {
	List<String> langs = new ArrayList<>();
	Locale locale = Locale.getDefault();
	if (locale != null && locale.getLanguage() != null && !locale.getLanguage().equals("")) {
            langs.add(locale.getLanguage());
            if (locale.getCountry() != null && !locale.getCountry().equals("")) {
		langs.add(locale.getLanguage() + "-" + locale.getCountry());
            }
	}
	langs.add(null);
	String en = Locale.ENGLISH.getLanguage();
	if (!langs.contains(en)) {
            langs.add(en);
	}
	return langs;
    }
    
    String runQuery(String mOwlQuery) {
       QueryEngine q = new QueryEngine(oReasoner, sForm);
       Set<OWLClass> classes = q.getClasses(mOwlQuery);
       Gson gson = new Gson();
       return gson.toJson(classes);
    }
    
    OWLReasoner createReasoner(OWLOntology ontology) {
        OWLReasonerFactory reasonerFactory = new StructuralReasonerFactory();
        return reasonerFactory.createReasoner(ontology);
    }
}
