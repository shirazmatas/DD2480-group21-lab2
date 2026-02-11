import org.json.JSONObject;
import java.io.*;
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

    public Build(JSONObject request) throws IOException {
        buildId = UUID.randomUUID().toString();

        repository = request.getJSONObject("repository").getString("name");
        branch = request.getString("ref").replace("refs/heads/", "");
        commit = request.getString("after");
        url = request.getJSONObject("repository").getString("clone_url");

        buildDirectory = new File(BUILDS_DIRECTORY, "build-" + buildId);
        if (!buildDirectory.mkdirs())
            throw new IOException("Failed to create build directory");

        projectDirectory = new File(buildDirectory, repository);
    }

    public void run() {
        try {
            logInfo("Running build for commit " + commit + " on branch " + branch);
            updateStatus("in_progress");

            if (!runCommand(buildDirectory, "git", "clone", "--quiet", "--branch", branch, url, projectDirectory.getAbsolutePath()))
                throw new RuntimeException("Failed to clone repository");

            if (!runCommand(projectDirectory, "mvnw.cmd", "clean", "compile")) {
                logInfo("Compilation failed");
                updateStatus("failure");
                return;
            }

            logInfo("Compilation success");

            // TODO run tests

            // TODO cleanup

            updateStatus("success");

        } catch (Exception e) {
            logError("Unexpected error", e);
            updateStatus("failure");
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
        // TODO update commit status on GitHub
    }

    private void logInfo(String message) {
        System.out.println("[INFO] [Build " + buildId + "] " + message);
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] [Build " + buildId + "] " + message);
        e.printStackTrace(System.err);
    }

}
