package dev.beyondllm.api;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItems;

/** HTTP contract tests of the REST API. Inference endpoints point at closed ports in the test profile. */
@QuarkusTest
class ApiTest {

    @Test
    void listsAllCandidatesWithCapabilities() {
        given().get("/api/models").then().statusCode(200)
                .body("id", hasItems("kev-0.8b", "kev-0.8b-candsel", "gliner2.5-base", "nuextract3", "qwen3.5-4b", "hybrid-gliner-kev-qwen", "hybrid-gliner-qwen"))
                .body("find { it.id == 'kev-0.8b' }.capabilities.tasks", hasItems("CLASSIFICATION"))
                .body("find { it.id == 'kev-0.8b' }.capabilities.features", hasItems("PROBABILITY_DISTRIBUTION"));
    }

    @Test
    void unavailableRuntimeIsReportedCleanly() {
        given().get("/api/models/qwen3.5-4b/health").then().statusCode(200).body("state", equalTo("DOWN"));
        given().get("/api/models/nope/health").then().statusCode(404);
    }

    @Test
    void inferenceAgainstDownRuntimeReturnsInfraErrorOutcome() {
        given().contentType("application/json")
                .body("{\"modelId\":\"kev-0.8b\",\"task\":\"CLASSIFICATION\",\"text\":\"cold coffee, refund\"}")
                .post("/api/inference").then().statusCode(200).body("outcome", equalTo("INFRA_ERROR"));
        given().contentType("application/json").body("{\"modelId\":\"kev-0.8b\",\"task\":\"EXTRACTION\",\"text\":\"x\"}")
                .post("/api/inference").then().statusCode(200).body("outcome", equalTo("UNSUPPORTED"));
        given().contentType("application/json").body("{\"modelId\":\"kev-0.8b\"}").post("/api/inference").then().statusCode(400);
    }

    @Test
    void datasetsAreListed() {
        given().get("/api/datasets").then().statusCode(200)
                .body("find { it.name == 'coffee-v1' }.records", equalTo(1000));
        given().get("/api/datasets/coffee-v1/records?group=ambiguous&limit=3").then().statusCode(200)
                .body("size()", equalTo(3)).body("[0].gold.intent", equalTo("none"));
    }

    @Test
    void benchmarkLifecycleEndpoints() {
        given().contentType("application/json").body("{\"models\":[]}").post("/api/benchmarks").then().statusCode(400);
        given().get("/api/benchmarks").then().statusCode(200).body("size()", greaterThanOrEqualTo(0));
        given().get("/api/benchmarks/does-not-exist").then().statusCode(404);
    }
}
