import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;

import org.json.*;

public class ContinuousIntegrationServer extends AbstractHandler {

    private static final String WEBHOOK_ENDPOINT = "/"; // TODO change endpoint on GitHub

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
        } else if (!"issue_comment".equals(request.getHeader("X-GitHub-Event"))) { // TODO change to "push"
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

    private void logInfo(String message) {
        System.out.println("[INFO] [Server] " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] [Server] " + message);
        e.printStackTrace(System.err);
    }

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