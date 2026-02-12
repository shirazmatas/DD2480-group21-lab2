import org.json.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BuildTest {

    private static final String TEST_REPO_URL = "https://github.com/OscarHd13/dd2480Test.git";

    private JSONObject createPayload(String branchName) {
        JSONObject repo = new JSONObject();
        repo.put("name", "test-repo");
        repo.put("clone_url", TEST_REPO_URL);

        JSONObject json = new JSONObject();
        json.put("ref", "refs/heads/" + branchName);
        json.put("after", "dummy-sha");
        json.put("repository", repo);

        return json;
    }

    // Expected to succeed. Project compiles and branch name exists
    @Test
    void testRealBuildSuccess() throws IOException {

        JSONObject payload = createPayload("main");
        Build build = new Build(payload);
        build.run();

        assertEquals("success", build.getResult());
    }

    // Expected to throw error because the branch name that it is trying to access does not exist
    @Test
    public void testRealBuildError() throws IOException {

        JSONObject payload = createPayload("wafdawdwad"); //random name

        Build build = new Build(payload);
        build.run();

        assertEquals("error",build.getResult());
    }

    // Expected to fail since it does not contain a pom.xml file, so compilation will fail
    @Test
    public void testRealBuildFailure() throws IOException {
        JSONObject payload = createPayload("fail-branch");

        Build build = new Build(payload);
        build.run();

        assertEquals("failure", build.getResult());
    }
}