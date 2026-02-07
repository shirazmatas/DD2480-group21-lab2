import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.*;
import org.json.*;
import java.util.UUID;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

public class ContinuousIntegrationServer extends AbstractHandler {

    private static final String WEBHOOK_ENDPOINT = "/"; // TODO change endpoint on GitHub
    private static final String BUILDS_DIRECTORY = "builds/";

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {

        logInfo("Server","Incoming request");
        logInfo("Server", "    " + request.getMethod() + " " + target);
        request.getHeaderNames().asIterator()
                .forEachRemaining(h -> logInfo("Server","    " + h + ": " + request.getHeader(h)));

        baseRequest.setHandled(true);

        if (!target.equals(WEBHOOK_ENDPOINT)) {
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            logInfo("Server", "Endpoint not found");
        } else if (!request.getMethod().equals("POST")) {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            logInfo("Server", "Method not allowed");
        } else if (!"issue_comment".equals(request.getHeader("X-GitHub-Event"))) { // TODO change to "push"
            response.setStatus(HttpServletResponse.SC_OK);
            logInfo("Server", "Event is not 'push'");
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            try {
                JSONObject body = new JSONObject(new JSONTokener(request.getReader()));
                new Thread(() -> runBuildJob(body)).start();
            } catch (Exception e) {
                logError("Server", "Failed to parse request body");
                e.printStackTrace();
            }
        }
    }

    private void runBuildJob(JSONObject requestBody) {
        String buildId = UUID.randomUUID().toString();
        String projectName = requestBody.getJSONObject("repository").getString("name");
        String cloneUrl = requestBody.getJSONObject("repository").getString("clone_url");
        String branch = requestBody.getString("ref").replace("refs/heads/", "");

        logInfo("Build " + buildId, "Job accepted for branch " + branch);

        File buildDirectory = new File(BUILDS_DIRECTORY, "build-" + buildId);
        File projectDirectory = new File(buildDirectory, projectName);

        try {
            // TODO update commit status on GitHub

            if (!buildDirectory.mkdirs())
                throw new RuntimeException("Failed to create build directory");

            if (!runCommand(buildId, buildDirectory, "git", "clone", "--quiet", "--branch", branch, cloneUrl, projectDirectory.getAbsolutePath()))
                throw new RuntimeException("Failed to clone repository");

            if (!runCommand(buildId, projectDirectory, "mvnw.cmd", "clean", "compile")) {
                logInfo("Build " + buildId, "Compilation failed");
                // TODO update commit status on GitHub
                return;
            }

            logInfo("Build " + buildId, "Compilation success");

            // TODO run tests

            // TODO cleanup

            // TODO update commit status on GitHub

        } catch (Exception e) {
            logError("Build " + buildId, "Unexpected error");
            e.printStackTrace();
        }
    }

    private boolean runCommand(String buildId, File directory, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(directory)
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                logInfo("Build " + buildId, line);
            }
        }

        return process.waitFor() == 0;
    }

    private void logInfo(String scope, String log) {
        System.out.println("[INFO] [" + scope + "] " + log);
    }

    private void logError(String scope, String log) {
        System.err.println("[ERROR] [" + scope + "] " + log);
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        server.setHandler(new ContinuousIntegrationServer()); 
        server.start();
        server.join();
    }
}
