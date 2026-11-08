# Dataset generator

The generator lives in Java, next to the benchmark harness:
`backend/src/main/java/dev/beyondllm/dataset/DatasetGenerator.java`, with phrase banks in `PhraseBank.java` and
the label vocabulary in `domain/LabelSchema.java`.

```bash
cd backend && mvn -q package -DskipTests
java -jar target/quarkus-app/quarkus-run.jar generate-dataset --seed=42          # → ../datasets/evaluation/coffee-v1.jsonl
java -jar target/quarkus-app/quarkus-run.jar generate-dataset --seed=7 --out=/tmp/other.jsonl
```

Running it again with the same seed gives byte-identical output (verified by `DatasetGeneratorTest`). The ground
truth comes only from the explicit generation rules described in `docs/methodology.md`. All content is synthetic
and fictional.
