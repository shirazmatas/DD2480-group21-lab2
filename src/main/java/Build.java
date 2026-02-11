import org.json.JSONObject;
import java.io.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

public class Build {

    private static final String BUILDS_DIRECTORY = "builds/";

    private final String buildId;
    private final String repository;
    private final String branch;
    private final String commit;
    private final String url;
    private final String owner;
    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

    private final File buildDirectory;
    private final File projectDirectory;
    private final File metadataFile;

    private final JSONObject metadata;
    private final PrintWriter logWriter;

    private String result = "pending";


    public Build(JSONObject request) throws IOException {
        buildId = UUID.randomUUID().toString();

        repository = request.getJSONObject("repository").getString("name");
        branch = request.getString("ref").replace("refs/heads/", "");
        commit = request.getString("after");
        url = request.getJSONObject("repository").getString("clone_url");
        owner = null;

        buildDirectory = new File(BUILDS_DIRECTORY, "build-" + buildId);
        if (!buildDirectory.mkdirs()){
            this.result = "failure";
            throw new IOException("Failed to create build directory");
        }

        projectDirectory = new File(buildDirectory, repository);
        metadataFile = new File(buildDirectory, "metadata.json");
        logWriter = new PrintWriter(new FileWriter(new File(buildDirectory, "build.log")));

        metadata = new JSONObject();
        metadata.put("buildId", buildId);
        metadata.put("repository", repository);
        metadata.put("branch", branch);
        metadata.put("commit", commit);
        metadata.put("startTime", Instant.now().toString());
    }

    public void run() {
        try {
            logInfo("Running build for commit " + commit + " on branch " + branch);
            updateStatus("pending");

            if (!runCommand(buildDirectory, "git", "clone", "--quiet", "--branch", branch, url, projectDirectory.getAbsolutePath())) {
                this.result = "failure";
                throw new RuntimeException("Failed to clone repository");
            }

            if (!runCommand(projectDirectory, "mvnw.cmd", "clean", "compile")) {
                logInfo("Compilation failed");
                this.result = "failure";
                updateStatus("failure");
                return;
            }

            logInfo("Compilation success");

            if (!runCommand(projectDirectory, "mvnw.cmd", "test")){
                logInfo("Test failed");
                this.result = "failure";
                updateStatus("failure");
                return;
            }
            logInfo("Test passed");

            // TODO cleanup

            updateStatus("success");

        } catch (Exception e) {
            logError("Unexpected error during CI job", e);
            updateStatus("failure");
        } finally {
            logWriter.close();
        }
    }

    private boolean runCommand(File directory, String... command) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(command)
                .directory(directory)
                .redirectErrorStream(true)
                .start();

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null)
                logInfo(line);
        }

        return process.waitFor() == 0;
    }

    /**
     * Update the state of the build on GitHub with a post-request.
     * @param state the state to update for the build; expected values include "pending", "success",
     *              "failure", or "error"
     */
    private void updateStatus(String state) {
        metadata.put("state", state);
        if (!"pending".equals(state)){
            metadata.put("endTime", Instant.now().toString());
        }
        try (FileWriter writer = new FileWriter(metadataFile)) {
            writer.write(metadata.toString(4));
        } catch (Exception e) {
            logError("Unexpected error during metadata update", e);
        }

        try {
            String token = System.getenv("GITHUB_TOKEN"); // TODO: Should be read from a config file?
            if (token == null || token.isBlank()) {
                logInfo("No GitHub token found, skipping status update");
                return;
            }
            JSONObject payload = getJsonObject(state);

            // URL should be predefined
            String apiURL = "https://api.github.com/repos/" + owner + "/"+ repository + "/statuses/" + commit;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(java.net.URI.create(apiURL))
                    .header("Authorization", "token " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/vnd.github.v3+json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                    .build();
            HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() / 100 != 2) {
                logInfo("Failed to update status: " + response.body() + "Reason: "+ response.statusCode());
            }
            else {
                logInfo("State updated successfully for commit " + commit + " on branch " + branch + " (" + state + ")" + " on repo"+ repository);
            }
        }
        catch (Exception e) {
            logError("Unexpected error during state update", e);
        }
    }

    private JSONObject getJsonObject(String state) {
        String description = switch (state) {
            case "success" -> "Build " + buildId + " succeeded";
            case "failure" -> "Build " + buildId + " failed";
            case "pending" -> "Build " + buildId + " is pending";
            case "error" -> "Build " + buildId + " encountered an error";
            default -> "Build " + buildId + state;
            // change default behaviour?
        };
        JSONObject payload = new JSONObject();
        payload.put("state", state);
        payload.put("description", description);
        payload.put("context", "DD2480-continuous-integration-server");
        return payload;
    }

    private void logInfo(String message) {
        System.out.println("[INFO] [Build " + buildId + "] " + message);
        logWriter.println(message);
        logWriter.flush();
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] [Build " + buildId + "] " + message);
        this.result = "failure";
        e.printStackTrace(System.err);
        logWriter.println(message);
        e.printStackTrace(logWriter);
        logWriter.flush();
    }

}