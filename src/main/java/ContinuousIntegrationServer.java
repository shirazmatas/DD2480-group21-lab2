import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;

import org.json.*;

/**
 * Description of ContinousIntegrationServer
 * Extends AbstractHandler. Handles incoming webhook pushes from github and manages the commits status as well as
 * initiates tests.
 * Displays the logs and metadata in html form. Define port within main to be used.
 * Uses github token stored in environment.
 * creates the builds in the /builds folder.
 * @author Lucas Lund
 * @author Barnabas Tanczos
 * @author Shengye (Óscar) Huang Wu
 * @version 1.0
 */
public class ContinuousIntegrationServer extends AbstractHandler {

    private static final String WEBHOOK_ENDPOINT = "/webhook";

    /**
     * Handles incoming HTTP requests to the server. Accepting only webhook_endpoint target and checking fields are correct.
     * This method processes the request appropriately, sets the response status, and handles webhook events.
     * Unsupported endpoints, methods, or events are flagged and logged.
     *
     * @param target the target of the request, representing the part of the URI path that the handler is responsible for
     * @param baseRequest the original unwrapped Jetty request object
     * @param request the servlet request object providing detailed request information, including headers and body content
     * @param response the servlet response object used to send data back to the client
     */
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {

        logInfo("Incoming request");
        logInfo("    " + request.getMethod() + " " + target);
        request.getHeaderNames().asIterator()
                .forEachRemaining(h -> logInfo("    " + h + ": " + request.getHeader(h)));

        baseRequest.setHandled(true);

        if (!target.equals(WEBHOOK_ENDPOINT)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            logInfo("Endpoint not found");
        } else if (!request.getMethod().equals("POST")) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            logInfo("Method not allowed");
        } else if (!"push".equals(request.getHeader("X-GitHub-Event"))) {
            response.setStatus(HttpServletResponse.SC_OK);
            logInfo("Event is not 'push'");
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            try {
                JSONObject body = new JSONObject(new JSONTokener(request.getReader()));
                Build build = new Build(body);
                new Thread(build::run).start();
            } catch (Exception e) {
                logError("Unexpected error during setup", e);
            }
        }
    }

    /**
     * Helper method to log messages to the console.
     * @param message the message to be logged
     */
    private void logInfo(String message) {
        System.out.println("[INFO] [Server] " + message);
    }

    /**
     * Helper method to log errors to the console.
     * @param message the message to be logged
     * @param e the exception that caused the error
     */
    private void logError(String message, Exception e) {
        System.err.println("[ERROR] [Server] " + message);
        e.printStackTrace(System.err);
    }

    /**
     * Main function that initiates the server, starts it and handles the files.
     * @param args the arguments fed into main function
     * @throws Exception the exception that caused an error.
     */
    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);

        ContinuousIntegrationServer ciHandler = new ContinuousIntegrationServer();

        ResourceHandler fileHandler = new ResourceHandler();
        fileHandler.setResourceBase("builds");
        fileHandler.setDirectoriesListed(true);

        HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[]{fileHandler, ciHandler});
        server.setHandler(handlers);

        server.start();
        server.join();
    }
}