package com.sendw.mpos;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Regression guards for the boundaries introduced by the modular migration. */
class ArchitectureTest {
    private final Path root = Path.of("src/main/java/com/sendw/mpos");

    private List<Path> sources() throws Exception {
        try (var paths = Files.walk(root)) { return paths.filter(p -> p.toString().endsWith(".java")).toList(); }
    }

    @Test void domainIsIndependentOfFrameworksAndAdapters() throws Exception {
        for (var path : sources()) {
            if (!path.toString().contains("/domain/")) continue;
            var source = Files.readString(path);
            for (String dependency : List.of("org.springframework", "jakarta.servlet", ".application.", ".infrastructure.", ".api.")) {
                assertFalse(source.contains(dependency), path + " must not depend on " + dependency);
            }
        }
    }

    @Test void controllersDoNotAccessSqlOrOwnTransactions() throws Exception {
        for (var path : sources()) {
            if (!path.getFileName().toString().endsWith("Controller.java")) continue;
            var source = Files.readString(path);
            for (String forbidden : List.of("JdbcTemplate", "java.sql", "@Transactional", "SELECT ", "INSERT INTO ", "UPDATE ", "DELETE FROM ")) {
                assertFalse(source.contains(forbidden), path + " contains " + forbidden);
            }
        }
    }

    @Test void allApplicationModulesUsePersistencePorts() throws Exception {
        for (var path : sources()) {
            String name = path.toString();
            if (!name.contains("/application/")) continue;
            var source = Files.readString(path);
            for(String forbidden : List.of("org.springframework.jdbc", "JdbcTemplate", "SELECT ", "INSERT INTO ", "UPDATE ", "DELETE FROM ")) {
                assertFalse(source.contains(forbidden), path + " must use a persistence port: " + forbidden);
            }
            assertFalse(source.contains(".infrastructure."), path + " must not depend on adapter implementations");
        }
    }

    @Test void applicationServicesDoNotReceiveHttpRequests() throws Exception {
        for (var path : sources()) {
            if (!path.toString().contains("/application/")) continue;
            var source = Files.readString(path);
            for (String forbidden : List.of("HttpServletRequest", "ResponseEntity", "MultipartFile", "web.bind.annotation")) {
                assertFalse(source.contains(forbidden), path + " contains transport concern " + forbidden);
            }
        }
    }
}
