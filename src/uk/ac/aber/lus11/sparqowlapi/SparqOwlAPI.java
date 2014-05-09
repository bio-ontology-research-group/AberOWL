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
package uk.ac.aber.lus11.sparqowlapi;

import uk.ac.aber.lus11.sparqowlapi.request.RequestManager;
import uk.ac.aber.lus11.sparqowlapi.server.SparqOwlServer;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;

/**
 * Main class to initialise the server.
 * 
 * @author Luke Slater (lus11@aber.ac.uk)
 */
public class SparqOwlAPI {
    /**
     * @param args the command line arguments
     * @throws org.semanticweb.owlapi.model.OWLOntologyCreationException
     */
    public static void main(String[] args) throws OWLOntologyCreationException, Exception {
        System.out.println("Starting.");
        
        RequestManager oManager = new RequestManager("owls.txt", false);
        System.out.println("Loaded Manager.");
        
        SparqOwlServer sServer = new SparqOwlServer(oManager);
        sServer.startServer();;
        System.out.println("Started Server.");
    }
    
}