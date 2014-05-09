package sparqowlapi;

import org.eclipse.jetty.server.Server;

/**
 *
 * @author reality
 */
public class SparqOwlServer {
    RequestManager oManager;
    RequestHandler rHandler;
    
    SparqOwlServer(RequestManager oManager) throws InterruptedException, Exception {
        this.oManager = oManager;
        startServer();
        this.rHandler = new RequestHandler(oManager);
    }
    
    void startServer() throws InterruptedException, Exception {
        Server server = new Server(8080);
        server.start();
        server.join();
    }
}
