package com.apiframework.dataproviders;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.testng.annotations.DataProvider;

import java.io.IOException;
import java.io.InputStream;

/**
 * Centralised TestNG DataProvider class.
 *
 * All @DataProvider methods are declared static so any test class can reference
 * them via:  dataProvider = "name", dataProviderClass = DataProviders.class
 *
 * Each provider returns Object[][] where each inner array is one test-data row.
 * The values map positionally to the test method's parameter list.
 *
 * All data is externalised to JSON files under src/test/resources/testdata/
 * so test values can be changed without touching Java source.
 */
public class DataProviders {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DataProviders() {}

    /**
     * Loads a JSON file from the test classpath and returns its root JsonNode.
     */
    private static JsonNode loadJson(String classpathPath) throws IOException {
        InputStream is = DataProviders.class.getClassLoader().getResourceAsStream(classpathPath);
        if (is == null) {
            throw new IllegalStateException(
                    "Test data file not found on classpath: " + classpathPath);
        }
        return MAPPER.readTree(is);
    }

    // ── GitHub ──────────────────────────────────────────────────────────────

    /**
     * Three invalid/expired GitHub Bearer token strings for TC_GH_002.
     * Data source: src/test/resources/testdata/github-invalid-tokens.json
     *
     * Columns: { invalidToken (String), scenario description (String) }
     */
    @DataProvider(name = "invalidGitHubTokens")
    public static Object[][] invalidGitHubTokens() throws IOException {
        JsonNode root = loadJson("testdata/github-invalid-tokens.json");
        Object[][] data = new Object[root.size()][2];
        for (int i = 0; i < root.size(); i++) {
            JsonNode row = root.get(i);
            data[i][0] = row.get("token").isNull() ? null : row.get("token").asText();
            data[i][1] = row.get("scenario").asText();
        }
        return data;
    }

    // ── Restful-Booker ───────────────────────────────────────────────────────

    /**
     * Reads guest booking rows from:
     *   src/test/resources/testdata/booking-guest-data.json
     *
     * Each JSON object maps to one test iteration for TC_RB_006.
     * Columns: { firstname, lastname, totalprice, depositpaid, additionalneeds }
     *
     * To add or change test data, edit the JSON file only — no Java changes needed.
     */
    @DataProvider(name = "bookingGuestData")
    public static Object[][] bookingGuestData() throws IOException {
        JsonNode root = loadJson("testdata/booking-guest-data.json");
        Object[][] data = new Object[root.size()][5];
        for (int i = 0; i < root.size(); i++) {
            JsonNode row = root.get(i);
            data[i][0] = row.get("firstname").asText();
            data[i][1] = row.get("lastname").asText();
            data[i][2] = row.get("totalprice").asInt();
            data[i][3] = row.get("depositpaid").asBoolean();
            data[i][4] = row.get("additionalneeds").asText();
        }
        return data;
    }

    /**
     * Single booking creation payload for TC_RB_002.
     * Data source: src/test/resources/testdata/booker-create-booking-data.json
     *
     * Columns: { firstname, lastname, totalprice, depositpaid, additionalneeds }
     */
    @DataProvider(name = "bookingCreateData")
    public static Object[][] bookingCreateData() throws IOException {
        JsonNode row = loadJson("testdata/booker-create-booking-data.json");
        return new Object[][] {{
            row.get("firstname").asText(),
            row.get("lastname").asText(),
            row.get("totalprice").asInt(),
            row.get("depositpaid").asBoolean(),
            row.get("additionalneeds").asText()
        }};
    }

    /**
     * Single booking update payload for TC_RB_004 (PUT full-replace).
     * Data source: src/test/resources/testdata/booker-update-booking-data.json
     *
     * Columns: { firstname, lastname, totalprice, depositpaid, additionalneeds }
     */
    @DataProvider(name = "bookingUpdateData")
    public static Object[][] bookingUpdateData() throws IOException {
        JsonNode row = loadJson("testdata/booker-update-booking-data.json");
        return new Object[][] {{
            row.get("firstname").asText(),
            row.get("lastname").asText(),
            row.get("totalprice").asInt(),
            row.get("depositpaid").asBoolean(),
            row.get("additionalneeds").asText()
        }};
    }

    /**
     * Single dummy payload for the negative PUT test TC_RB_005.
     * Data source: src/test/resources/testdata/booker-negative-booking-data.json
     *
     * Columns: { firstname, lastname, totalprice, depositpaid }
     * Note: these values are intentionally arbitrary — TC_RB_005 only asserts 403.
     */
    @DataProvider(name = "bookingNegativeData")
    public static Object[][] bookingNegativeData() throws IOException {
        JsonNode row = loadJson("testdata/booker-negative-booking-data.json");
        return new Object[][] {{
            row.get("firstname").asText(),
            row.get("lastname").asText(),
            row.get("totalprice").asInt(),
            row.get("depositpaid").asBoolean()
        }};
    }

    // ── Platzi Fake Store ────────────────────────────────────────────────────

    /**
     * Three invalid / missing auth scenarios for TC_PF_003.
     * Data source: src/test/resources/testdata/platzi-invalid-tokens.json
     *
     * null token → test uses baseSpec (no Authorization header).
     * Non-null token → test uses authorizedSpec with the invalid string.
     * All scenarios must return 401 Unauthorized.
     *
     * Columns: { invalidToken (String or null), scenario description (String) }
     */
    @DataProvider(name = "invalidPlatziTokens")
    public static Object[][] invalidPlatziTokens() throws IOException {
        JsonNode root = loadJson("testdata/platzi-invalid-tokens.json");
        Object[][] data = new Object[root.size()][2];
        for (int i = 0; i < root.size(); i++) {
            JsonNode row = root.get(i);
            data[i][0] = row.get("token").isNull() ? null : row.get("token").asText();
            data[i][1] = row.get("scenario").asText();
        }
        return data;
    }

    /**
     * Single product creation payload for TC_PF_004.
     * Data source: src/test/resources/testdata/platzi-product-data.json
     *
     * Columns: { price (int), categoryId (int), imageUrl (String), description (String) }
     */
    @DataProvider(name = "productCreateData")
    public static Object[][] productCreateData() throws IOException {
        JsonNode row = loadJson("testdata/platzi-product-data.json");
        return new Object[][] {{
            row.get("price").asInt(),
            row.get("categoryId").asInt(),
            row.get("imageUrl").asText(),
            row.get("description").asText()
        }};
    }
}
