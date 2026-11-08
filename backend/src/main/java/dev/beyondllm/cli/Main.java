package dev.beyondllm.cli;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.annotations.QuarkusMain;

/**
 * Entry point. Without arguments (or with {@code serve}) the REST API + dashboard backend runs.
 * With a command, the benchmark runs headless and the process exits:
 * <pre>
 *   java -jar target/quarkus-app/quarkus-run.jar benchmark --profile=smoke --models=kev-0.8b,gliner2.5-base
 *   java -jar target/quarkus-app/quarkus-run.jar generate-dataset --seed=42
 *   java -jar target/quarkus-app/quarkus-run.jar calibrate
 *   java -jar target/quarkus-app/quarkus-run.jar health | env | report &lt;runId&gt;
 * </pre>
 */
@QuarkusMain
public class Main {

    public static void main(String... args) {
        if (args.length > 0 && !"serve".equals(args[0])) {
            // headless command: do not clash with a running API server
            System.setProperty("quarkus.http.port", System.getProperty("quarkus.http.port", "0"));
            System.setProperty("quarkus.log.category.\"io.quarkus\".level", "WARN");
            Quarkus.run(CliApp.class, args);
        } else {
            Quarkus.run(args);
        }
    }
}
