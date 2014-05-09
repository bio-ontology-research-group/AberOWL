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

import uk.ac.aber.lus11.sparqowlapi.server.SparqOwlServer;
import uk.ac.aber.lus11.sparqowlapi.request.RequestManager;
import java.io.IOException;
import java.net.URLEncoder;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.junit.After;
import org.junit.AfterClass;
import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

/**
 * Test the OWL data endpoint HTTP server.
 * 
 * @author Luke Slater
 */
public class SparqOwlServerTest {
    private static final String PIZZA = "http://130.88.198.11/2008/07/sssw/pizza.owl";
    private static final String QUERY = "FishTopping";
    private RequestManager manager;
    private SparqOwlServer instance;
    
    public SparqOwlServerTest() {
    }
    
    @BeforeClass
    public static void setUpClass() {
    }
    
    @AfterClass
    public static void tearDownClass() {
    }
    
    @Before
    public void setUp() throws OWLOntologyCreationException, IOException, Exception {
        this.manager = new RequestManager(PIZZA, true);
        this.instance = new SparqOwlServer(this.manager);
        this.instance.startServer();
    }
    
    @After
    public void tearDown() throws Exception {
    }

    /**
     * Test of startServer method, of class SparqOwlServer.
     */
    @Test
    public void testQueryServer() throws Exception {
        System.out.println("queryServer");
        
        CloseableHttpClient httpclient = HttpClients.createDefault();
        HttpGet httpget = new HttpGet("http://127.0.0.1:9090?query=" + URLEncoder.encode(QUERY));
        
        try (CloseableHttpResponse response = httpclient.execute(httpget)) {
            assertEquals(response.getStatusLine().toString(), "HTTP/1.1 200 OK");
            String result = EntityUtils.toString(response.getEntity());
            assertTrue(result.contains("PizzaTopping"));
        }
    }
}
