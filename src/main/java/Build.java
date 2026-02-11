import org.json.JSONObject;
import java.io.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.UUID;
import java.util.stream.Stream;

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
        if (!buildDirectory.mkdirs())
            throw new IOException("Failed to create build directory");

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
            logInfo("Running build for " + repository + " commit " + commit + " on branch " + branch);
            updateStatus("pending");

            if (!runCommand(buildDirectory, "git", "clone", "--quiet", "--branch", branch, url, projectDirectory.getAbsolutePath()))
                throw new RuntimeException("Failed to clone repository");

            if (!runCommand(projectDirectory, "mvnw.cmd", "clean", "compile")) {
                logInfo("Compilation failed");
                updateStatus("failure");
                return;
            }

            logInfo("Compilation success");

            if (!runCommand(projectDirectory, "mvnw.cmd", "test")){
                logInfo("Test failed");
                updateStatus("failure");
                return;
            }

            logInfo("Tests passed");

            logInfo("Finished build");
            updateStatus("success");

        } catch (Exception e) {
            logError("Unexpected error during CI job", e);
            updateStatus("error");
        } finally {
            deleteRecursively(projectDirectory);
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
        result = status;
        updateMetadata(status);
        updateGitHubStatus(status);
    }

    private void updateMetadata(String status) {
        metadata.put("status", status);
        if (!status.equals("pending"))
            metadata.put("endTime", Instant.now().toString());

        try (FileWriter writer = new FileWriter(metadataFile)) {
            writer.write(metadata.toString(4));
        } catch (Exception e) {
            logError("Unexpected error during metadata update", e);
        }
    }

    private void updateGitHubStatus(String status) {
        // TODO update commit status on GitHub
    }

    private void deleteRecursively(File directory) {
        try (Stream<Path> paths = Files.walk(directory.toPath())) {
            paths.sorted(java.util.Comparator.reverseOrder())
                    .map(java.nio.file.Path::toFile)
                    .forEach(file -> {
                        if (!file.delete())
                            logInfo("Failed to delete: " + file.getAbsolutePath());
                    });
        } catch (Exception e) {
            logError("Unexpected error while deleting directory: " + directory, e);
        }
    }

    private void logInfo(String message) {
        System.out.println("[INFO] [Build " + buildId + "] " + message);
        logWriter.println(message);
        logWriter.flush();
    }

    private void logError(String message, Exception e) {
        System.err.println("[ERROR] [Build " + buildId + "] " + message);
        e.printStackTrace(System.err);
        logWriter.println(message);
        e.printStackTrace(logWriter);
        logWriter.flush();
    }

    public String getResult() {
        return result;
    }

}