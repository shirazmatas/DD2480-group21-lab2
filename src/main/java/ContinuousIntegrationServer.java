import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.UUID;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class ContinuousIntegrationServer extends AbstractHandler {

    private static final String WEBHOOK_ENDPOINT = "/";

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {

        System.out.println("Incoming request");
        System.out.println("    " + request.getMethod() + " " + target);
        request.getHeaderNames().asIterator()
                .forEachRemaining(h -> System.out.println("    " + h + ": " + request.getHeader(h)));

        baseRequest.setHandled(true);

        if (!target.equals(WEBHOOK_ENDPOINT)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            System.out.println("Endpoint not found");
        } else if (!request.getMethod().equals("POST")) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            System.out.println("Method not allowed");
        } else if (!request.getHeader("X-GitHub-Event").equals("issue_comment")) { // TODO change to "push"
            response.setStatus(HttpServletResponse.SC_OK);
            System.out.println("Event is not 'push'");
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            new Thread(() -> runBuildJob(request)).start();
        }
    }

    private void runBuildJob(HttpServletRequest request) {
        String buildId = UUID.randomUUID().toString();
        System.out.println("Build " + buildId + ": job accepted");
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        server.setHandler(new ContinuousIntegrationServer()); 
        server.start();
        server.join();
    }
}
