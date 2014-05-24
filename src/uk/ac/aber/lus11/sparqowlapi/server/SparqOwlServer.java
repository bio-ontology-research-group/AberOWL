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
package uk.ac.aber.lus11.sparqowlapi.server;

import org.eclipse.jetty.server.Server;
import uk.ac.aber.lus11.sparqowlapi.request.RequestManager;

/**
 * Creates the HTTP server to handle Manchester OWL Syntax queries.
 * 
 * @author Luke Slater
 */
public class SparqOwlServer {
    private final RequestManager rManager;
    private Server server;

    public SparqOwlServer(RequestManager rManager) throws InterruptedException, Exception {
        this.rManager = rManager;
    }
    
    /**
     * Start the OWL API server.
     * 
     * @throws InterruptedException
     * @throws Exception 
     */
    public void startServer() throws InterruptedException, Exception {
        this.server = new Server(9091);
        this.server.setHandler(new RequestHandler(rManager));
        this.server.start();
        this.server.setStopAtShutdown(true);
    }
}
