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
import com.google.gson.Gson;
import java.io.IOException;
import java.util.Set;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

/**
 * Test the querying capabilities of the RequestManager.
 * 
 * @author Luke Slater
 */
public class RequestManagerTest { 
    private static final String PIZZA = "http://130.88.198.11/2008/07/sssw/pizza.owl";
    private static final String QUERY = "FishTopping";
    private static final String FISHTOPPING = "[{\"iri\":{\"remainder\":\"FishTopping\",\"prefix\":\"http://owl.cs.manchester.ac.uk/2008/07/sssw/pizza#\",\"hashCode\":1658128913},\"isThing\":false,\"isNothing\":false,\"hashCode\":1658134722}]";
    private RequestManager manager;
    
    public RequestManagerTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
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
     * Test of runQuery method, of class RequestManager.
     */
    @Test
    public void testRunQuery() {
        System.out.println("runQuery");
        Set<OWLClass> result = this.manager.runQuery(QUERY, RequestType.EQUIVALENT);
        
        Gson gson = new Gson();
        assertEquals(gson.toJson(result), FISHTOPPING);
    }
    
}
