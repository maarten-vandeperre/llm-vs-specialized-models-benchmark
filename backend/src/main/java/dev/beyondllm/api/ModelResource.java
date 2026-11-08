package dev.beyondllm.api;

import dev.beyondllm.application.BenchmarkModel;
import dev.beyondllm.application.ModelRegistry;
import dev.beyondllm.domain.BenchmarkRequest;
import dev.beyondllm.domain.BenchmarkResponse;
import dev.beyondllm.domain.HealthStatus;
import dev.beyondllm.domain.ModelCapabilities;
import dev.beyondllm.domain.TaskType;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api")
@Produces(MediaType.APPLICATION_JSON)
public class ModelResource {

    private final ModelRegistry registry;

    public ModelResource(ModelRegistry registry) {
        this.registry = registry;
    }

    public record ModelView(String id, String displayName, String kind, String runtime, String modelRef, String family,
                            String quantization, String notes, ModelCapabilities capabilities) {
        static ModelView of(BenchmarkModel m) {
            var i = m.info();
            return new ModelView(m.id(), i.displayName(), i.kind(), i.runtime(), i.modelRef(), i.family(), i.quantization(), i.notes(), m.capabilities());
        }
    }

    public record InferenceRequest(String modelId, TaskType task, String text, String language) {
    }

    @GET
    @Path("/models")
    public List<ModelView> models() {
        return registry.all().stream().map(ModelView::of).toList();
    }

    @GET
    @Path("/models/{id}/health")
    public HealthStatus health(@PathParam("id") String id) {
        return registry.find(id).orElseThrow(() -> new NotFoundException("Unknown model " + id)).health();
    }

    @POST
    @Path("/inference")
    public BenchmarkResponse inference(InferenceRequest req) {
        if (req == null || req.modelId() == null || req.task() == null || req.text() == null || req.text().isBlank()) {
            throw new BadRequestException("modelId, task and non-empty text are required");
        }
        BenchmarkModel m = registry.find(req.modelId()).orElseThrow(() -> new NotFoundException("Unknown model " + req.modelId()));
        return m.execute(new BenchmarkRequest(null, req.task(), req.text(), req.language()));
    }
}
