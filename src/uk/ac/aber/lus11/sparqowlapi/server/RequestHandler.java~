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

import com.google.gson.Gson;
import java.io.IOException;
import java.util.Set;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.semanticweb.owlapi.model.OWLClass;
import uk.ac.aber.lus11.sparqowlapi.request.RequestManager;
import uk.ac.aber.lus11.sparqowlapi.request.*;

/**
 * Handle HTTP requests to the server.
 * 
 * @author Luke Slater (lus11@aber.ac.uk)
 */
public class RequestHandler extends AbstractHandler {
    private final RequestManager oManager;
    
    RequestHandler(RequestManager oManager) {
        this.oManager = oManager;
    }
    
    /**
     * Handle a Manchester OWL Syntax query to the HTTP server.
     * 
     * @param target The URI the request was made to.
     * @param baseRequest Basic request object for communicating with the Jetty handler.
     * @param request The request which was sent to the server by the client.
     * @param response The response object we use to handle returning the data to the client.
     * @throws IOException In the case of a data error.
     * @throws ServletException In the case of a server error.
     */
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        // Set the response type and inform that the request is being handled.
        response.setContentType("application/json;charset=utf-8");
        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
        
        // Get parameters from the request
        String query = request.getParameter("query");
        String type = request.getParameter("type");
        String ontology = request.getParameter("ontology"); // the IRI of the ontology
	
	// for the autocomplete request
        String term = request.getParameter("term");

	if (type != null && type.equals("listontologies")) {
	    Set results = oManager.listOntologies() ;
	    Gson gson = new Gson();
            response.getWriter().println(gson.toJson(results));                               
	} else if (term != null) { // autocomplete query
	    Set results = oManager.queryNames(term, ontology) ;
	    Gson gson = new Gson();
            response.getWriter().println(gson.toJson(results));                               
	} else { // ontology query
	    // Convert request type string to typed enum.
	    RequestType requestType;
	    if(type == null) type = "all";
	    type = type.toLowerCase();
	    switch(type) {
            case "superclass": requestType = RequestType.SUPERCLASS; break;
            case "subclass": requestType = RequestType.SUBCLASS; break;
            case "equivalent": requestType = RequestType.EQUIVALENT; break;
            default: requestType = RequestType.SUBCLASS; break;
	    }
	    // Run the query, convert the results to JSON and write them back to the client.
	    Set results = oManager.runQuery(query, requestType, ontology);
	    Gson gson = new Gson();
	    response.getWriter().println(gson.toJson(results));
	}
    }
}
