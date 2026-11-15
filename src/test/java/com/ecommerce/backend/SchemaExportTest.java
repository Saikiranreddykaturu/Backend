package com.ecommerce.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.Container.ExecResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One-off generation test (T-03): boots the real app context against an external
 * throwaway PostgreSQL (started manually; no volume so no existing data is touched)
 * with ddl-auto=create so Hibernate creates the current schema, then captures it
 * with pg_dump --schema-only. The captured SQL becomes V1__initial_schema.sql,
 * which by construction matches what Hibernate 'validate' expects.
 * DELETED after V1 is generated.
 *
 * Requires a running postgres on localhost:5433, e.g.:
 *   docker run -d --name pg-gen -p 5433:5432 -e POSTGRES_DB=ecommerce_db \
 *     -e POSTGRES_USER=postgres -e POSTGRES_PASSWORD=postgres postgres:15
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class SchemaExportTest {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> "jdbc:postgresql://localhost:5433/ecommerce_db");
        r.add("spring.datasource.username", () -> "postgres");
        r.add("spring.datasource.password", () -> "postgres");
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create");
        r.add("spring.jpa.properties.hibernate.dialect", () -> "org.hibernate.dialect.PostgreSQLDialect");
        r.add("spring.flyway.enabled", () -> "false");
        r.add("spring.cache.type", () -> "none");
        r.add("spring.data.redis.host", () -> "localhost");
        r.add("app.jwt.secret", () -> "testsecretkeytestsecretkeytestsecretkey");
    }

    @Autowired
    ApplicationContext applicationContext;

    @Test
    void dumpSchema() throws Exception {
        // Schema was created by Hibernate during context boot.
        ProcessBuilder pb = new ProcessBuilder("docker", "exec", "pg-gen", "pg_dump", "-U",
                "postgres", "-d", "ecommerce_db", "-s", "--no-owner", "--no-privileges", "--no-comments");
        pb.redirectErrorStream(false);
        Process p = pb.start();
        String stdout = new String(p.getInputStream().readAllBytes());
        String stderr = new String(p.getErrorStream().readAllBytes());
        int code = p.waitFor();
        if (code != 0) {
            throw new IllegalStateException("pg_dump failed (" + code + "): " + stderr);
        }
        Path target = Paths.get("src/main/resources/db/migration/V1__initial_schema.sql");
        Files.createDirectories(target.getParent());
        Files.writeString(target, stdout);
    }
}
