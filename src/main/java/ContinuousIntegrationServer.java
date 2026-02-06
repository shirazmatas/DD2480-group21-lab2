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

    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {

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
        } else if (!"issue_comment".equals(request.getHeader("X-GitHub-Event"))) { // TODO change to "push"
            response.setStatus(HttpServletResponse.SC_OK);
            System.out.println("Event is not 'push'");
        } else {
            response.setStatus(HttpServletResponse.SC_OK);
            try {
                JSONObject body = new JSONObject(new JSONTokener(request.getReader()));
                new Thread(() -> runBuildJob(body)).start();
            } catch (Exception e) {
                System.out.println("Failed to parse request body");
                e.printStackTrace();
            }
        }
    }

    private void runBuildJob(JSONObject requestBody) {
        String buildId = UUID.randomUUID().toString();
        String projectName = requestBody.getJSONObject("repository").getString("name");
        String cloneUrl = requestBody.getJSONObject("repository").getString("clone_url");
        String branch = requestBody.getString("ref").replace("refs/heads/", "");

        System.out.println("Build " + buildId + ": job accepted for branch " + branch);

        File buildDirectory = new File(BUILDS_DIRECTORY, "build-" + buildId);
        File projectDirectory = new File(buildDirectory, projectName);

        try {
            // TODO update commit status on GitHub

            if (!buildDirectory.mkdirs())
                throw new RuntimeException("Failed to create build directory");

            if (!runCommand(buildDirectory, "git", "clone", "--branch", branch, cloneUrl, projectDirectory.getAbsolutePath()))
                throw new RuntimeException("Failed to clone repository");

            if (!runCommand(projectDirectory, "mvnw.cmd", "clean", "compile")) {
                System.out.println("Build " + buildId + ": compilation failed");
                // TODO update commit status on GitHub
                return;
            }

            System.out.println("Build " + buildId + ": compilation success");

            // TODO run tests

            // TODO cleanup

            // TODO update commit status on GitHub

        } catch (Exception e) {
            System.out.println("Build " + buildId + ": unexpected error");
            e.printStackTrace();
        }
    }

    private boolean runCommand(File directory, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(directory)
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }
        }

        return process.waitFor() == 0;
    }

    public static void main(String[] args) throws Exception {
        Server server = new Server(8080);
        server.setHandler(new ContinuousIntegrationServer()); 
        server.start();
        server.join();
    }
}
