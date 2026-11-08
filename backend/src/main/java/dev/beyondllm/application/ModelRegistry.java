package dev.beyondllm.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Collects every {@link BenchmarkModel} CDI bean (single models and hybrid pipelines). */
@ApplicationScoped
public class ModelRegistry {

    private final List<BenchmarkModel> models;

    @Inject
    public ModelRegistry(ModelFactory factory) {
        List<BenchmarkModel> list = new ArrayList<>(factory.create());
        list.sort(Comparator.comparing((BenchmarkModel m) -> m.info().kind()).thenComparing(BenchmarkModel::id));
        this.models = List.copyOf(list);
    }

    public ModelRegistry(List<BenchmarkModel> models) {
        this.models = List.copyOf(models);
    }

    public List<BenchmarkModel> all() {
        return models;
    }

    public Optional<BenchmarkModel> find(String id) {
        return models.stream().filter(m -> m.id().equals(id)).findFirst();
    }

    public BenchmarkModel require(String id) {
        return find(id).orElseThrow(() -> new IllegalArgumentException("Unknown model id '" + id + "'. Known: "
                + models.stream().map(BenchmarkModel::id).toList()));
    }
}
