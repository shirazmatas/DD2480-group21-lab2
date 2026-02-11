import org.json.JSONObject;
import java.io.*;
import java.time.Instant;
import java.util.UUID;

public class Build {

    private static final String BUILDS_DIRECTORY = "builds/";

    private final String buildId;
    private final String repository;
    private final String branch;
    private final String commit;
    private final String url;

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
            updateStatus("in_progress");

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

    private void updateStatus(String status) {
        metadata.put("status", status);
        metadata.put("endTime", Instant.now().toString());

        try (FileWriter writer = new FileWriter(metadataFile)) {
            writer.write(metadata.toString(4));
        } catch (Exception e) {
            logError("Unexpected error during metadata update", e);
        }

        // TODO update commit status on GitHub
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