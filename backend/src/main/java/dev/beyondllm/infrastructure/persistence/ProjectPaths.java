package dev.beyondllm.infrastructure.persistence;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Resolves the configured project directories ({@code ../results}, {@code ../datasets}) so the CLI works both from
 * {@code backend/} (the default) and from the repository root.
 */
public final class ProjectPaths {

    private ProjectPaths() {
    }

    public static Path resolve(String configured) {
        Path p = Path.of(configured);
        if (p.isAbsolute() || Files.isDirectory(p) || !configured.startsWith("../")) {
            return p;
        }
        Path fromRoot = Path.of(configured.substring(3));
        return Files.isDirectory(fromRoot) && Files.isDirectory(Path.of("backend")) ? fromRoot : p;
    }
}
