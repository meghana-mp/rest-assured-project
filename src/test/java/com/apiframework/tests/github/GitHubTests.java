package com.apiframework.tests.github;

import com.apiframework.constants.ApiConstants;
import com.apiframework.dataproviders.DataProviders;
import com.apiframework.models.github.CreateRepoRequest;
import com.apiframework.models.github.GitHubRepo;
import com.apiframework.models.github.GitHubUser;
import com.apiframework.specs.GitHubSpecs;
import com.apiframework.utils.TestUtils;
import io.qameta.allure.Epic;
import io.qameta.allure.Feature;
import io.qameta.allure.Severity;
import io.qameta.allure.SeverityLevel;
import io.restassured.response.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static io.restassured.module.jsv.JsonSchemaValidator.matchesJsonSchemaInClasspath;
import static org.hamcrest.Matchers.*;
import static org.testng.Assert.*;

/**
 * GitHub API Tests — OAuth 2.0 Bearer Token Authentication
 *
 * TC_GH_001  Authenticate & Fetch Profile
 * TC_GH_002  Authentication Failure — DATA-DRIVEN (3 invalid token scenarios)
 * TC_GH_003  List Repositories (with query params + deserialization to List<GitHubRepo>)
 * TC_GH_004  Create Repository (serialization of CreateRepoRequest + deserialization to GitHubRepo)
 * TC_GH_005  Delete Repository (DELETE → 204, confirmed with follow-up GET → 404)
 *
 * Prerequisites:
 *   - Set 'github.token' in config.properties (or env var GITHUB_TOKEN)
 *   - Token requires scopes: repo, delete_repo
 */
@Epic("REST Assured Framework")
@Feature("GitHub API — OAuth 2.0 Bearer Token")
public class GitHubTests {

    private static final Logger log = LoggerFactory.getLogger(GitHubTests.class);
    private static final String TEST_REPO_NAME = TestUtils.uniqueName("ra-test-repo");

    private String githubLogin;
    private String createdRepoName;

    // ── Setup ───────────────────────────────────────────────────────────────

    @BeforeClass
    public void setUp() {
        String token = com.apiframework.config.ConfigManager.getInstance()
                .getProperty("github.token");

        if (token == null || token.isBlank() || token.startsWith("REPLACE_WITH")) {
            throw new SkipException(
                    "GitHub token not configured — set 'github.token' in config.properties "
                            + "or the GITHUB_TOKEN environment variable. Skipping GitHub tests.");
        }

        // Resolve the authenticated user's login once and cache it for TC_GH_005 path param
        githubLogin = given()
                .spec(GitHubSpecs.authorizedSpec())
                .when()
                .get(ApiConstants.GITHUB_USER)
                .then()
                .statusCode(200)
                .extract().jsonPath().getString("login");

        log.info("GitHub test session started — authenticated as: {}", githubLogin);
    }

    // ── TC_GH_001 ───────────────────────────────────────────────────────────

    /*
     * TC_GH_001 — Authenticate & Fetch Profile
     *
     * What it does:
     *   Sends a GET request to /user with a valid OAuth 2.0 Bearer token and verifies
     *   that the API returns the authenticated user's full profile object.
     *
     * Steps:
     *   1. Send GET /user using authorizedSpec() which attaches:
     *        Authorization: Bearer <token>
     *        Accept: application/vnd.github+json
     *        X-GitHub-Api-Version: 2022-11-28
     *   2. Assert HTTP 200, JSON content-type, and response time < 5 s via successSpec().
     *   3. Assert individual fields: login, id, avatar_url, type = "User".
     *   4. Deserialize the full response into a GitHubUser POJO and run TestNG null checks.
     *
     * Serialization:   NONE — GET request carries no body.
     * Deserialization: response.as(GitHubUser.class) — Jackson reads the JSON response and
     *                  maps each field to the GitHubUser POJO. Snake_case JSON fields
     *                  (avatar_url, html_url, public_repos, created_at) are mapped using
     *                  @JsonProperty annotations. @JsonIgnoreProperties(ignoreUnknown = true)
     *                  silently drops fields not declared in the POJO.
     *
     * Expected result:
     *   HTTP 200 — non-null login, id, and avatar_url; type equals "User".
     */
    @Severity(SeverityLevel.CRITICAL)
    @Test(priority = 1,
          groups = {"smoke", "regression", "full"},
          description = "TC_GH_001 — Verify GET /user with valid Bearer token returns profile data")
    public void TC_GH_001_authenticateAndFetchProfile() {
        Response response = given()
                .spec(GitHubSpecs.authorizedSpec())
                .when()
                .get(ApiConstants.GITHUB_USER)
                .then()
                .spec(GitHubSpecs.successSpec())
                .body(matchesJsonSchemaInClasspath(ApiConstants.SCHEMA_GITHUB_USER))
                .body("login",      not(emptyOrNullString()))
                .body("id",         notNullValue())
                .body("avatar_url", not(emptyOrNullString()))
                .body("type",       equalTo("User"))
                .extract().response();

        // ── Deserialization: JSON → GitHubUser POJO ──────────────────────
        GitHubUser user = response.as(GitHubUser.class);
        long responseTime = response.getTime();

        assertNotNull(user.getLogin(),     "login must not be null");
        assertNotNull(user.getId(),        "id must not be null");
        assertNotNull(user.getAvatarUrl(), "avatar_url must not be null");

        TestUtils.logResult("TC_GH_001", true, responseTime,
                "login=" + user.getLogin() + ", id=" + user.getId()
                        + ", public_repos=" + user.getPublicRepos());
    }

    // ── TC_GH_002 ───────────────────────────────────────────────────────────

    /*
     * TC_GH_002 — Authentication Failure  [DATA-DRIVEN — runs 3 times]
     *
     * What it does:
     *   Sends a GET request to /user with an invalid/expired Bearer token and verifies
     *   that the API rejects the request with 401 Unauthorized. This test is parameterised
     *   with three different invalid token formats to maximise negative-test coverage.
     *
     * Data-Driven:
     *   Powered by @DataProvider("invalidGitHubTokens") in DataProviders.java.
     *   TestNG injects one row per iteration:
     *     Row 1 — plain invalid string
     *     Row 2 — fake PAT format (wrong value)
     *     Row 3 — malformed JWT-style token
     *   The test method signature accepts (String invalidToken, String scenario).
     *
     * Steps:
     *   1. Receive invalidToken and scenario from the DataProvider.
     *   2. Build the request using GitHubSpecs.withTokenSpec(invalidToken) which injects
     *      "Authorization: Bearer <invalidToken>" dynamically per iteration.
     *   3. GET /user and assert HTTP 401 via unauthorizedSpec().
     *   4. Assert the response body contains a non-empty "message" field.
     *   5. Log the scenario name and 401 message for traceability.
     *
     * Serialization:   NONE — GET request carries no body.
     * Deserialization: NONE — only the "message" field is extracted via jsonPath(); the
     *                  full response is not deserialized into a POJO.
     *
     * Expected result:
     *   HTTP 401 Unauthorized for all 3 token variants — response body contains "message".
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 2,
          groups = {"regression", "full"},
          dataProvider = "invalidGitHubTokens",
          dataProviderClass = DataProviders.class,
          description = "TC_GH_002 — Verify GET /user with various invalid tokens returns 401 Unauthorized")
    public void TC_GH_002_authenticationFailure(String invalidToken, String scenario) {
        log.info("TC_GH_002 scenario: {}", scenario);

        // ── withTokenSpec injects the DataProvider-supplied token per iteration ──
        Response response = given()
                .spec(GitHubSpecs.withTokenSpec(invalidToken))
                .when()
                .get(ApiConstants.GITHUB_USER)
                .then()
                .spec(GitHubSpecs.unauthorizedSpec())
                .body("message", not(emptyOrNullString()))
                .extract().response();

        long responseTime = response.getTime();

        TestUtils.logResult("TC_GH_002", true, responseTime,
                "scenario=[" + scenario + "] status=401, message="
                        + response.jsonPath().getString("message"));
    }

    // ── TC_GH_003 ───────────────────────────────────────────────────────────

    /*
     * TC_GH_003 — List Repositories
     *
     * What it does:
     *   Sends a GET request to /user/repos with query parameters to retrieve the
     *   authenticated user's repositories sorted by most recently updated, capped at 10.
     *   Verifies the response is a JSON array and each item has a non-null name field.
     *
     * Steps:
     *   1. GET /user/repos with query params: per_page=10, sort=updated, direction=desc.
     *   2. Assert HTTP 200 and that the root element is a JSON array (List.class check).
     *   3. Deserialize the JSON array into List<GitHubRepo> via jsonPath().getList().
     *   4. Assert list size ≤ 10 and that each GitHubRepo item has a non-null name.
     *
     * Serialization:   NONE — GET request with only query parameters, no body.
     * Deserialization: response.jsonPath().getList(".", GitHubRepo.class) — Jackson maps
     *                  each JSON object in the array to a GitHubRepo POJO. The root path "."
     *                  refers to the top-level JSON array. Fields like full_name, clone_url,
     *                  default_branch are mapped via @JsonProperty annotations on GitHubRepo.
     *
     * Expected result:
     *   HTTP 200 — JSON array of ≤ 10 GitHubRepo objects, each with a non-null name.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 3,
          groups = {"regression", "full"},
          description = "TC_GH_003 — Verify GET /user/repos lists accessible repositories")
    public void TC_GH_003_listRepositories() {
        Response response = given()
                .spec(GitHubSpecs.authorizedSpec())
                .queryParam("per_page", 10)
                .queryParam("sort", "updated")
                .queryParam("direction", "desc")
                .when()
                .get(ApiConstants.GITHUB_USER_REPOS)
                .then()
                .spec(GitHubSpecs.successSpec())
                .body(matchesJsonSchemaInClasspath(ApiConstants.SCHEMA_GITHUB_REPOS_LIST))
                .body("$", instanceOf(List.class))
                .extract().response();

        // ── Deserialization: JSON array → List<GitHubRepo> ───────────────
        List<GitHubRepo> repos = response.jsonPath().getList(".", GitHubRepo.class);
        long responseTime = response.getTime();

        assertNotNull(repos, "Repositories list must not be null");
        assertTrue(repos.size() <= 10, "per_page=10 should cap the result at 10 items");
        repos.forEach(r -> assertNotNull(r.getName(), "Each repo must have a name"));

        TestUtils.logResult("TC_GH_003", true, responseTime,
                "count=" + repos.size() + " repositories returned");
    }

    // ── TC_GH_004 ───────────────────────────────────────────────────────────

    /*
     * TC_GH_004 — Create Repository
     *
     * What it does:
     *   Sends a POST request to /user/repos with a CreateRepoRequest body to create a new
     *   public repository under the authenticated user's account. The repository name is
     *   unique per run (timestamp-suffixed) to avoid conflicts. The created repo name is
     *   stored for deletion in TC_GH_005.
     *
     * Steps:
     *   1. Build a CreateRepoRequest POJO using Lombok @Builder with name, description,
     *      privateRepo=false, autoInit=true.
     *   2. POST /user/repos — RestAssured serializes the POJO to JSON automatically.
     *   3. Assert HTTP 201, JSON content-type, and key response fields (name, private, id).
     *   4. Deserialize the response body into a GitHubRepo POJO.
     *   5. Run TestNG assertions on created.id, name, htmlUrl, privateRepo.
     *   6. Store createdRepoName in the instance variable for TC_GH_005.
     *
     * Serialization:   .body(body) — RestAssured detects that body is a POJO (not a String)
     *                  and delegates to Jackson ObjectMapper to serialize CreateRepoRequest
     *                  into JSON:  { "name": "...", "description": "...",
     *                               "private": false, "auto_init": true }
     *                  Note: @JsonProperty("private") maps privateRepo field → "private" key,
     *                  and @JsonProperty("auto_init") maps autoInit → "auto_init".
     *
     * Deserialization: response.as(GitHubRepo.class) — Jackson maps the 201 response JSON
     *                  (id, name, full_name, private, html_url, clone_url, etc.) to the
     *                  GitHubRepo POJO. Snake_case keys are handled by @JsonProperty.
     *
     * Expected result:
     *   HTTP 201 Created — response contains the newly created repo with a server-assigned
     *   id, matching name, public visibility, and html_url.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 4,
          groups = {"regression", "full"},
          description = "TC_GH_004 — Verify POST /user/repos creates a new repository")
    public void TC_GH_004_createRepository() {
        // ── Serialization: CreateRepoRequest POJO → JSON request body ────
        CreateRepoRequest body = CreateRepoRequest.builder()
                .name(TEST_REPO_NAME)
                .description("Automated test repository — created by REST Assured framework")
                .privateRepo(false)
                .autoInit(true)
                .build();

        Response response = given()
                .spec(GitHubSpecs.authorizedSpec())
                .body(body)
                .when()
                .post(ApiConstants.GITHUB_USER_REPOS)
                .then()
                .spec(GitHubSpecs.createdSpec())
                .header("Content-Type", containsString("application/json"))
                .body(matchesJsonSchemaInClasspath(ApiConstants.SCHEMA_GITHUB_REPO))
                .body("name",    equalTo(TEST_REPO_NAME))
                .body("private", equalTo(false))
                .body("id",      notNullValue())
                .extract().response();

        // ── Deserialization: JSON response → GitHubRepo POJO ─────────────
        GitHubRepo created = response.as(GitHubRepo.class);
        long responseTime = response.getTime();

        assertNotNull(created.getId(),        "Created repo must have an id");
        assertEquals(created.getName(),       TEST_REPO_NAME, "Repo name must match request");
        assertNotNull(created.getHtmlUrl(),   "Created repo must have an html_url");
        assertFalse(created.getPrivateRepo(), "Repo should be public");

        createdRepoName = created.getName();

        TestUtils.logResult("TC_GH_004", true, responseTime,
                "repo=" + created.getFullName() + ", id=" + created.getId());
    }

    // ── TC_GH_005 ───────────────────────────────────────────────────────────

    /*
     * TC_GH_005 — Delete Repository
     *
     * What it does:
     *   Sends a DELETE request to /repos/{owner}/{repo} to permanently remove the repository
     *   created in TC_GH_004. After the delete, a follow-up GET confirms the repository no
     *   longer exists (404), proving the deletion was successful and not just silently ignored.
     *
     * Steps:
     *   1. Use githubLogin (from @BeforeClass) and createdRepoName (from TC_GH_004)
     *      as path parameters in the DELETE /repos/{owner}/{repo} request.
     *   2. Assert HTTP 204 No Content via noContentSpec() — the body is empty on success.
     *   3. Follow-up GET /repos/{owner}/{repo} — assert HTTP 404 to confirm deletion.
     *
     * Serialization:   NONE — DELETE request carries no body.
     * Deserialization: NONE — 204 has no response body; the follow-up GET response is
     *                  not deserialized since only the status code (404) is checked.
     *
     * Dependency: dependsOnMethods = "TC_GH_004_createRepository" — this test only runs
     *             if TC_GH_004 succeeded and createdRepoName is populated.
     *
     * Expected result:
     *   HTTP 204 No Content on DELETE — follow-up GET returns HTTP 404 Not Found.
     */
    @Severity(SeverityLevel.NORMAL)
    @Test(priority = 5,
          groups = {"regression", "full"},
          dependsOnMethods = "TC_GH_004_createRepository",
          description = "TC_GH_005 — Verify DELETE /repos/{owner}/{repo} returns 204 No Content")
    public void TC_GH_005_deleteRepository() {
        Response response = given()
                .spec(GitHubSpecs.authorizedSpec())
                .pathParam("owner", githubLogin)
                .pathParam("repo",  createdRepoName)
                .when()
                .delete(ApiConstants.GITHUB_REPO_BY_OWNER)
                .then()
                .spec(GitHubSpecs.noContentSpec())
                .extract().response();

        long responseTime = response.getTime();

        // Verify the repo is actually gone — a subsequent GET must return 404
        given()
                .spec(GitHubSpecs.authorizedSpec())
                .pathParam("owner", githubLogin)
                .pathParam("repo",  createdRepoName)
                .when()
                .get(ApiConstants.GITHUB_REPO_BY_OWNER)
                .then()
                .statusCode(404);

        TestUtils.logResult("TC_GH_005", true, responseTime,
                "Deleted " + githubLogin + "/" + createdRepoName + " — confirmed 404 on re-fetch");
    }
}
