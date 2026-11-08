package dev.beyondllm.api;

import dev.beyondllm.dataset.DatasetRecord;
import dev.beyondllm.dataset.DatasetRepository;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import java.util.List;

@Path("/api/datasets")
@Produces(MediaType.APPLICATION_JSON)
public class DatasetResource {

    private final DatasetRepository repo;

    public DatasetResource(DatasetRepository repo) {
        this.repo = repo;
    }

    @GET
    public List<DatasetRepository.DatasetDescriptor> list() {
        return repo.describeAll();
    }

    @GET
    @Path("/{name}/records")
    public List<DatasetRecord> records(@PathParam("name") String name, @QueryParam("group") String group, @QueryParam("split") String split,
                                       @QueryParam("limit") @DefaultValue("50") int limit) {
        if (!repo.names().contains(name)) {
            throw new NotFoundException("Unknown dataset " + name);
        }
        List<DatasetRecord> all = repo.select(name, new DatasetRepository.Filter(group == null ? null : List.of(group), split, null, null));
        return all.subList(0, Math.min(Math.max(limit, 0), all.size()));
    }
}
