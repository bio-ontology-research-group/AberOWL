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

import uk.ac.aber.lus11.sparqowlapi.request.RequestManager;
import uk.ac.aber.lus11.sparqowlapi.request.QueryParser;
import java.io.IOException;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import uk.ac.aber.lus11.sparqowlapi.util.NewShortFormProvider;

/**
 * Test the MOS query parser.
 * 
 * @author Luke Slater
 */
public class QueryParserTest {
    private static final String PIZZA = "http://130.88.198.11/2008/07/sssw/pizza.owl";
    private static final String QUERY = "Pizza and hasTopping some FishTopping";
    private static final String CLASSEXPRESSION = "ObjectIntersectionOf(<http://owl.cs.manchester.ac.uk/2008/07/sssw/pizza#Pizza> ObjectSomeValuesFrom(<http://owl.cs.manchester.ac.uk/2008/07/sssw/pizza#hasTopping> <http://owl.cs.manchester.ac.uk/2008/07/sssw/pizza#FishTopping>))";
    private RequestManager manager;
    
    public QueryParserTest() {
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
     * Test of parse method, of class QueryParser.
     */
    @Test
    public void testParse() {
        System.out.println("parse");
        
        OWLOntology ontology = this.manager.getOntologies().get(0);
        NewShortFormProvider sProvider = this.manager.getQueryEngines().get(0).getsProvider();
        
        QueryParser instance = new QueryParser(ontology, sProvider);
        
        OWLClassExpression exp = instance.parse(QUERY);
        
        assertEquals(exp.toString(), CLASSEXPRESSION);
    }
    
}
