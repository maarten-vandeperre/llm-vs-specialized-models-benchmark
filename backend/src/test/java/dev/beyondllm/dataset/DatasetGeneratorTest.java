package dev.beyondllm.dataset;

import dev.beyondllm.domain.DecisionPolicy;
import dev.beyondllm.domain.LabelSchema;
import dev.beyondllm.domain.TaskType;
import dev.beyondllm.infrastructure.http.JsonHttp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatasetGeneratorTest {

    private final List<DatasetRecord> recs = new DatasetGenerator(42).generate();

    @Test
    void sameSeedIsByteIdenticalAndDifferentSeedDiffers() throws Exception {
        String a = JsonHttp.MAPPER.writeValueAsString(recs);
        String b = JsonHttp.MAPPER.writeValueAsString(new DatasetGenerator(42).generate());
        assertEquals(a, b);
        assertNotEquals(a, JsonHttp.MAPPER.writeValueAsString(new DatasetGenerator(7).generate()));
    }

    @Test
    void hasTargetSizeGroupsLanguagesAndSplits() {
        assertEquals(1000, recs.size());
        Map<String, Long> groups = recs.stream().collect(Collectors.groupingBy(DatasetRecord::group, Collectors.counting()));
        assertEquals(Map.of("classification", 300L, "extraction", 250L, "combined", 150L, "ambiguous", 96L, "multilingual", 108L, "long", 96L), groups);
        assertTrue(recs.stream().map(DatasetRecord::language).collect(Collectors.toSet()).containsAll(List.of("en", "nl", "fr")));
        long calib = recs.stream().filter(r -> r.split().equals("calibration")).count();
        assertTrue(calib > 200 && calib < 400, "≈30% calibration split, was " + calib);
        assertEquals(1000, recs.stream().map(DatasetRecord::id).distinct().count(), "unique ids");
    }

    @Test
    void goldFollowsExplicitRules() {
        for (DatasetRecord r : recs) {
            DatasetRecord.Gold g = r.gold();
            assertEquals(LabelSchema.categoryFor(g.complaint()), g.category(), r.id());
            if (!g.complaint().equals(LabelSchema.NONE)) {
                assertEquals("negative", g.sentiment(), r.id());
            }
            assertEquals(DecisionPolicy.decide(g.intent(), g.complaint(), g.orderId() != null, g.total()), g.decision(), r.id());
            for (String f : LabelSchema.FIELDS) {
                assertTrue(LabelSchema.isKnown(f, g.label(f)), r.id() + " " + f);
            }
            if (g.orderId() != null) {
                assertNotNull(g.orderIdSpan(), r.id());
                assertEquals(g.orderId().toLowerCase(), r.text().substring(g.orderIdSpan().start(), g.orderIdSpan().end()).toLowerCase(), r.id());
            }
            if (g.total() != null) {
                assertEquals(DatasetGenerator.total(g.items()), g.total(), "total = sum(price × qty) " + r.id());
            }
        }
        assertTrue(recs.stream().filter(r -> r.group().equals("ambiguous")).allMatch(r -> r.gold().intent().equals("none")));
    }

    @Test
    void multilingualTriplesAreEquivalent() {
        Map<Object, List<DatasetRecord>> triples = recs.stream().filter(r -> r.group().equals("multilingual"))
                .collect(Collectors.groupingBy(r -> r.meta().get("tripleId")));
        assertEquals(36, triples.size());
        for (List<DatasetRecord> t : triples.values()) {
            assertEquals(3, t.size());
            assertEquals(1, t.stream().map(r -> r.gold().intent() + r.gold().complaint() + r.gold().orderId() + r.gold().items() + r.gold().total()).distinct().count());
            assertEquals(1, t.stream().map(DatasetRecord::split).distinct().count(), "triple stays in one split");
        }
    }

    @Test
    void longDocumentsCoverLengthsAndPositions() {
        List<DatasetRecord> l = recs.stream().filter(r -> r.group().equals("long")).toList();
        assertTrue(l.stream().allMatch(r -> r.task() == TaskType.EXTRACTION));
        for (int t : DatasetGenerator.LONG_TARGET_TOKENS) {
            List<DatasetRecord> at = l.stream().filter(r -> r.meta().get("targetTokens").equals(t)).toList();
            assertEquals(24, at.size());
            double mean = at.stream().mapToInt(DatasetRecord::approxTokens).average().orElse(0);
            assertTrue(Math.abs(mean - t) / t < 0.25, "approx tokens near target " + t + " was " + mean);
        }
    }

    @Test
    void coversRequiredPhenomena() {
        var tags = recs.stream().flatMap(r -> r.tags().stream()).collect(Collectors.toSet());
        assertTrue(tags.containsAll(List.of("negation", "contradictory", "spelling_error", "irrelevant_text", "implicit",
                "missing_order_id", "missing_total", "multiple_products", "numeric_distractor", "product_distractor")), tags.toString());
    }

    @Test
    void writeReadRoundTripAndEdgeCasesLoad(@TempDir Path dir) throws Exception {
        Path f = dir.resolve("evaluation/coffee-v1.jsonl");
        DatasetRepository.write(recs, f);
        assertEquals(recs, DatasetRepository.read(f));
        Files.createDirectories(dir.resolve("fixtures"));
        Files.writeString(dir.resolve("fixtures/edge-cases-v1.jsonl"), "// comment\n"
                + "{\"id\":\"e1\",\"group\":\"edge_cases\",\"language\":\"en\",\"task\":\"COMBINED\",\"text\":\"Order BE-1 cold, refund. €3.50\","
                + "\"gold\":{\"intent\":\"refund\",\"sentiment\":\"negative\",\"category\":\"product_quality\",\"complaint\":\"temperature\","
                + "\"orderId\":\"BE-1\",\"items\":[],\"total\":3.50,\"currency\":\"EUR\"}}\n");
        DatasetRepository repo = new DatasetRepository(dir.toString());
        DatasetRecord e = repo.load("edge-cases-v1").getFirst();
        assertEquals("evaluation", e.split());
        assertEquals(6, e.gold().orderIdSpan().start());
        assertEquals("auto_refund", e.gold().decision(), "decision derived at load time");
        assertEquals(30, repo.select("coffee-v1", new DatasetRepository.Filter(null, "evaluation", null, 30)).size());
    }

    @Test
    void stratifiedLimitCoversAllGroups() {
        List<DatasetRecord> s = DatasetRepository.stratifiedLimit(recs, 12);
        assertEquals(6, s.stream().map(DatasetRecord::group).distinct().count());
    }
}
