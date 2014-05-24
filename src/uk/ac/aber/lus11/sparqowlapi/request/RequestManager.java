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

import java.util.*;
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
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.io.*;
import uk.ac.aber.lus11.sparqowlapi.util.NewShortFormProvider;


/**
 * Set up the ontology manager, the ontology, and provide a router for Manchester
 * OWL syntax requests.
 * 
 * @author Luke Slater (lus11@aber.ac.uk)
 */
public class RequestManager {
    private OWLOntologyManager oManager;
    //    private final List<OWLOntology> ontologies = new ArrayList<>();
    private final Map<String, OWLOntology> ontologies = new TreeMap<>();
    private final List<OWLAnnotationProperty> aProperties = new ArrayList<>();
    private final Map<String, QueryEngine> queryEngines = new TreeMap<>();
    private final Map<String, OWLOntologyManager> ontologyManagers = new TreeMap<>();
    private final OWLDataFactory df = OWLManager.getOWLDataFactory() ;
    
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
        
        for(String oListString : oList) {
            System.out.println(oListString);
            IRI iri = IRI.create(oListString);
	    try {
		this.oManager = OWLManager.createOWLOntologyManager();
		OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration() ;
		config.setFollowRedirects(true) ;
		config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT) ;
		OWLOntology ontology = this.oManager.loadOntologyFromOntologyDocument(new IRIDocumentSource(iri), config);
		this.ontologies.put(oListString, ontology);
		this.ontologyManagers.put(oListString, this.oManager) ;
	    } catch (OWLOntologyAlreadyExistsException E) {
		// do nothing
	    } catch (Exception E) {
		E.printStackTrace() ;
	    }
	    
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
	Iterator<String> it = this.ontologies.keySet().iterator();
	while (it.hasNext()) {
	    String oListString = it.next() ;
	    OWLOntology ontology = ontologies.get(oListString) ;
	    OWLOntologyManager manager = ontologyManagers.get(oListString) ;
            OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology);
            oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
            NewShortFormProvider sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);
            this.queryEngines.put(oListString, new QueryEngine(oReasoner, sForm));
        }
    }
    
    /**
     * Create list of RDFS_LABEL annotations to be used by the ShortFormProvider.
     */
    private void loadAnnotations() {
        OWLDataFactory factory = oManager.getOWLDataFactory();
        OWLAnnotationProperty rdfsLabel = factory.getOWLAnnotationProperty(OWLRDFVocabulary.RDFS_LABEL.getIRI()) ;                                                       
        aProperties.add(rdfsLabel);
	aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasNarrowSynonym"))) ;
	aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasBroadSynonym"))) ;
	aProperties.add(factory.getOWLAnnotationProperty(IRI.create("http://www.geneontology.org/formats/oboInOwl#hasExactSynonym"))) ;
    }
    
    private Set<MyOWLClassInformation> classes2info(Set<OWLClass> classes, OWLOntology o, String uri) {
        Set<MyOWLClassInformation> result = new HashSet<>();
	for (OWLClass c : classes) {
	    MyOWLClassInformation info = new MyOWLClassInformation() ;
	    info.owlClass = c ;
	    info.ontologyURI = uri ;
	    for (OWLAnnotation annotation : c.getAnnotations(o, df.getRDFSLabel())) {
		if (annotation.getValue() instanceof OWLLiteral) {
		    OWLLiteral val = (OWLLiteral) annotation.getValue();
		    info.label = val.getLiteral() ;
		}
	    }
	    /* definition */
	    for (OWLAnnotation annotation : c.getAnnotations(o, df.getOWLAnnotationProperty(IRI.create("http://purl.obolibrary.org/obo/IAO_0000115")))) {
		if (annotation.getValue() instanceof OWLLiteral) {
		    OWLLiteral val = (OWLLiteral) annotation.getValue();
		    info.definition = val.getLiteral() ;
		}
	    }
	    result.add(info);
	}
	return result ;
    }

    /**
     * Iterate the query engines, collecting results from each and collating them into a single structure.
     * 
     * @param mOwlQuery Class query in Manchester OWL Syntax.
     * @param requestType Type of class match to be performed. Valid values are: subclass, superclass, equivalent or all.
     * @return Set of OWL Classes.
     */
    public Set<MyOWLClassInformation> runQuery(String mOwlQuery, RequestType requestType, String ontUri) {
        Set<MyOWLClassInformation> classes = new HashSet<>();
	if (ontUri == null) { // query all the ontologies in the repo
	    Iterator<String> it = queryEngines.keySet().iterator() ;
	    while (it.hasNext()) {
		String oListString = it.next() ;
		QueryEngine queryEngine = queryEngines.get(oListString) ;
		OWLOntology ontology = ontologies.get(oListString) ;
		try {
		    Set<OWLClass> resultSet = queryEngine.getClasses(mOwlQuery, requestType) ;
		    classes.addAll(classes2info(resultSet, ontology, oListString)) ;
		} catch (org.semanticweb.owlapi.expression.ParserException E) { }
	    }
	} else if (queryEngines.get(ontUri) == null) { // download the ontology and query
	    Map<OWLAnnotationProperty, List<String>> preferredLanguageMap = new HashMap<>();
	    for (OWLAnnotationProperty annotationProperty : this.aProperties) {
		preferredLanguageMap.put(annotationProperty, new ArrayList<String>());
	    }
	    try {
		OWLOntologyManager manager = OWLManager.createOWLOntologyManager() ;
		OWLOntologyLoaderConfiguration config = new OWLOntologyLoaderConfiguration() ;                                                                            
		config.setFollowRedirects(true) ;                                                                                                                         
		config.setMissingImportHandlingStrategy(MissingImportHandlingStrategy.SILENT) ;                                                                           
		OWLOntology ontology = manager.loadOntologyFromOntologyDocument(new IRIDocumentSource(IRI.create(ontUri)), config);
		OWLReasonerFactory reasonerFactory = new ElkReasonerFactory(); 
		OWLReasoner oReasoner = reasonerFactory.createReasoner(ontology);
		oReasoner.precomputeInferences(InferenceType.CLASS_HIERARCHY);
		NewShortFormProvider sForm = new NewShortFormProvider(aProperties, preferredLanguageMap, manager);
		Set<OWLClass> resultSet = new QueryEngine(oReasoner, sForm).getClasses(mOwlQuery, requestType) ;
		classes.addAll(classes2info(resultSet, ontology, ontUri)) ;
	    } catch (OWLOntologyCreationException E) {
		E.printStackTrace() ;
	    }
	} else { // query one single ontology
	    QueryEngine queryEngine = queryEngines.get(ontUri) ;
	    OWLOntology ontology = ontologies.get(ontUri) ;
	    try {
		Set<OWLClass> resultSet = queryEngine.getClasses(mOwlQuery, requestType) ;
		classes.addAll(classes2info(resultSet, ontology, ontUri)) ;
	    } catch (org.semanticweb.owlapi.expression.ParserException E) { 
		E.printStackTrace() ; 
	    }
	}
	return classes;
    }
    
    public Map<String, QueryEngine> getQueryEngines() {
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
    public Map<String, OWLOntology> getOntologies() {
        return ontologies;
    }
}
