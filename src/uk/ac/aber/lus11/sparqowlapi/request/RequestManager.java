/* 
 * Copyright 2014 Luke Slater (lus11@aber.ac.uk).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.ac.aber.lus11.sparqowlapi.request;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.semanticweb.elk.owlapi.ElkReasonerFactory;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAnnotationProperty;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.OWLReasonerFactory;
import org.semanticweb.owlapi.vocab.OWLRDFVocabulary;
import uk.ac.aber.lus11.sparqowlapi.util.NewShortFormProvider;


/**
 * Set up the ontology manager, the ontology, and provide a router for Manchester
 * OWL syntax requests.
 * 
 * @author Luke Slater (lus11@aber.ac.uk)
 */
public class RequestManager {
    private OWLOntologyManager oManager;
    private final List<OWLOntology> ontologies = new ArrayList<>();
    private final List<OWLAnnotationProperty> aProperties = new ArrayList<>();
    private final List<QueryEngine> queryEngines = new ArrayList<>();
    
    public RequestManager(String ontologyDescription, boolean literal) throws OWLOntologyCreationException, IOException {
        List<String> oList = new ArrayList<>();
        
        if(literal) {
            oList.add(ontologyDescription);
        } else {
            Path filePath = new File(ontologyDescription).toPath();
            Charset charset = Charset.defaultCharset();
            
            oList = Files.readAllLines(filePath, charset);
        }
        
        loadOntologies(oList);
        loadAnnotations();
        createReasoner();
    }
    
    /**
     * Create the ontology manager and load it with the given ontology.
     * 
     * @param ontologyLink URI to the OWL ontology to be queried.
     * @throws OWLOntologyCreationException 
     */
    private void loadOntologies(List<String> oList) throws OWLOntologyCreationException, IOException {
        this.oManager = OWLManager.createOWLOntologyManager();
        
        for(String oListString : oList) {
            System.out.println(oListString);
            IRI iri = IRI.create(oListString);
            OWLOntology ontology = this.oManager.loadOntologyFromOntologyDocument(iri);
            
            this.ontologies.add(ontology);
            this.ontologies.addAll(ontology.getImports());
        }   
    }
    
    /**
     * Create and run the reasoning on the loaded OWL ontologies, creating a QueryEngine for each.
     */
    private void createReasoner() {
        List<String> langs = new ArrayList<>();
        Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
        for (OWLAnnotationProperty annotationProperty : this.aProperties) {
            preferredLanguageMap.put(annotationProperty, langs);
        }
        
        OWLReasonerFactory reasonerFactory = new ElkReasonerFactory(); // May be replaced with any reasoner using the standard interface
        for(OWLOntology ontology : this.ontologies) {
            OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology);
            oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
            NewShortFormProvider sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, oManager);
            
            this.queryEngines.add(new QueryEngine(oReasoner, sForm));
        }
        
    }
    
    /**
     * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider.
     */
    private void loadAnnotations() {
        OWLDataFactory factory = oManager.getOWLDataFactory();
        OWLAnnotationProperty rdfsLabel = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()) ;                                                                                           
        aProperties.add(rdfsLabel);
    }
    
    /**
     * Iterate the query engines, collecting results from each and collating them into a single structure.
     * 
     * @param mOwlQuery Class query in Manchester OWL Syntax.
     * @param requestType Type of class match to be performed. Valid values are: subclass, superclass, equivalent or all.
     * @return Set of OWL Classes.
     */
    public Set<OWLClass> runQuery(String mOwlQuery, RequestType requestType) {
        Set<OWLClass> classes = new HashSet<>();
        for(QueryEngine queryEngine : this.queryEngines) {
            classes.addAll(queryEngine.getClasses(mOwlQuery, requestType));
        }
        return classes;
    }
    
    public List<QueryEngine> getQueryEngines() {
        return this.queryEngines;
    }

    /**
     * @return the oManager
     */
    public OWLOntologyManager getoManager() {
        return oManager;
    }

    /**
     * @return the ontologies
     */
    public List<OWLOntology> getOntologies() {
        return ontologies;
    }
}