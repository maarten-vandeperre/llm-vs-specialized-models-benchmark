package dev.beyondllm.api;

import dev.beyondllm.benchmark.BenchmarkConfig;
import dev.beyondllm.benchmark.BenchmarkRunner;
import dev.beyondllm.benchmark.PredictionRecord;
import dev.beyondllm.benchmark.RunStatus;
import dev.beyondllm.infrastructure.persistence.ResultStore;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Map;

@Path("/api/benchmarks")
@Produces(MediaType.APPLICATION_JSON)
public class BenchmarkResource {

    private final BenchmarkRunner runner;
    private final ResultStore store;

    public BenchmarkResource(BenchmarkRunner runner, ResultStore store) {
        this.runner = runner;
        this.store = store;
    }

    public record Page(long total, List<PredictionRecord> items) {
    }

    @POST
    public Response start(BenchmarkConfig config) {
        if (config == null || config.models().isEmpty()) {
            throw new BadRequestException("At least one model id is required");
        }
        try {
            return Response.accepted(runner.submit(config)).build();
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @GET
    public List<RunStatus> list() {
        return runner.list();
    }

    @GET
    @Path("/{runId}")
    public RunStatus status(@PathParam("runId") String runId) {
        return runner.status(runId).orElseThrow(() -> new NotFoundException("Unknown run " + runId));
    }

    @GET
    @Path("/{runId}/results")
    public Page results(@PathParam("runId") String runId, @QueryParam("modelId") String modelId, @QueryParam("group") String group,
                        @QueryParam("outcome") String outcome, @QueryParam("phase") String phase, @QueryParam("incorrect") boolean incorrect,
                        @QueryParam("offset") @DefaultValue("0") int offset, @QueryParam("limit") @DefaultValue("50") int limit) {
        status(runId);
        List<PredictionRecord> all = store.predictions(runId).stream()
                .filter(p -> modelId == null || modelId.isBlank() || modelId.equals(p.modelId()))
                .filter(p -> group == null || group.isBlank() || group.equals(p.group()))
                .filter(p -> outcome == null || outcome.isBlank() || outcome.equals(String.valueOf(p.outcome())))
                .filter(p -> phase == null || phase.isBlank() || phase.equals(p.phase()))
                .filter(p -> !incorrect || p.evaluation() == null || !p.evaluation().correct())
                .toList();
        int from = Math.min(Math.max(offset, 0), all.size());
        int to = Math.min(from + Math.max(limit, 0), all.size());
        return new Page(all.size(), all.subList(from, to));
    }

    @GET
    @Path("/{runId}/results.csv")
    @Produces("text/csv")
    public Response csv(@PathParam("runId") String runId) {
        status(runId);
        String csv = store.readText(runId, "predictions.csv").orElseGet(() -> store.toCsv(store.predictions(runId)));
        return Response.ok(csv).header("Content-Disposition", "attachment; filename=\"" + runId + "-predictions.csv\"").build();
    }

    @GET
    @Path("/{runId}/report")
    @Produces({MediaType.APPLICATION_JSON, "text/markdown"})
    public Response report(@PathParam("runId") String runId, @QueryParam("format") @DefaultValue("json") String format) {
        RunStatus s = status(runId);
        if (s.status() != RunStatus.State.COMPLETED) {
            return Response.status(409).type(MediaType.APPLICATION_JSON).entity(Map.of("error", "Run is " + s.status() + "; report available after completion")).build();
        }
        if ("md".equalsIgnoreCase(format)) {
            return store.readText(runId, "report.md").map(t -> Response.ok(t, "text/markdown").build())
                    .orElseThrow(() -> new NotFoundException("No report.md"));
        }
        return store.readJson(runId, "report.json", Map.class).map(r -> Response.ok(r, MediaType.APPLICATION_JSON).build())
                .orElseThrow(() -> new NotFoundException("No report.json"));
    }
}
