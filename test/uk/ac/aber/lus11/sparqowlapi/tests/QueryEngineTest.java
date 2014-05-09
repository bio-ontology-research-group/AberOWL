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
package uk.ac.aber.lus11.sparqowlapi.tests;

import uk.ac.aber.lus11.sparqowlapi.request.RequestType;
import uk.ac.aber.lus11.sparqowlapi.request.RequestManager;
import java.io.IOException;
import java.util.Set;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;


/**
 * Test getting various classes from a given description in an OWL ontology using
 * the QueryEngine, loaded by the RequestManager.
 * 
 * @author Luke Slater
 */
public class QueryEngineTest {
    private static final String PIZZA = "http://130.88.198.11/2008/07/sssw/pizza.owl";
    private static final String query = "Pizza and hasTopping some FishTopping";
    private RequestManager manager;
    
    public QueryEngineTest() {
        
    }
    
    @BeforeClass
    public static void setUpClass() throws OWLOntologyCreationException {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() throws OWLOntologyCreationException, IOException {
        this.manager = new RequestManager(PIZZA, true);
    }
    
    @After
    public void tearDown() {
    }

    /**
     * Test of getClasses method, of class QueryEngine.
     */
    @Test
    public void testGetAllClasses() {
        System.out.println("getAllClasses");
        
        RequestType requestType = RequestType.ALL;
        Set<OWLClass> results = this.manager.runQuery(query, requestType);

        assertEquals(results.size(), 8);
    }
    
    /**
     * Test of getClasses method, of class QueryEngine.
     */
    @Test
    public void testGetSubClasses() {
        System.out.println("getSubClasses");
        
        RequestType requestType = RequestType.SUBCLASS;
        Set<OWLClass> results = this.manager.runQuery(query, requestType);
        
        assertEquals(results.size(), 7);
    }

    /**
     * Test of getClasses method, of class QueryEngine.
     */
    @Test
    public void testGetSuperClasses() {
        System.out.println("getSuperClasses");
        
        RequestType requestType = RequestType.SUPERCLASS;
        Set<OWLClass> results = this.manager.runQuery(query, requestType);

        assertEquals(results.size(), 1);
    }
    
    
    /**
     * Test of getClasses method, of class QueryEngine.
     */
    @Test
    public void testGetEquivalentClasses() {
        System.out.println("getEquivalentClasses");
        
        RequestType requestType = RequestType.EQUIVALENT;
        Set<OWLClass> results = this.manager.runQuery(query, requestType);
        
        assertTrue(results.isEmpty());
    }
    
}
