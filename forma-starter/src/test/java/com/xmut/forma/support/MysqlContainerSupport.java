package com.xmut.forma.support;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Shared mysql:8.0.36 container + JDBC overrides for true-DDL ITs.
 * Start only via {@code @Container} so {@code disabledWithoutDocker} can skip.
 */
public final class MysqlContainerSupport {

    public static final String IMAGE = "mysql:8.0.36";

    public static final MySQLContainer<?> MYSQL = create();

    public static MySQLContainer<?> create() {
        return new MySQLContainer<>(DockerImageName.parse(IMAGE))
                .withDatabaseName("forma")
                .withUsername("forma")
                .withPassword("forma123")
                .withCopyFileToContainer(
                        MountableFile.forHostPath(resolveBootstrapSchema().toString()),
                        "/docker-entrypoint-initdb.d/001_schema.sql");
    }

    private MysqlContainerSupport() {
    }

    public static void registerDatasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.sql.init.mode", () -> "never");
    }

    static Path resolveBootstrapSchema() {
        Path dir = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int i = 0; i < 8 && dir != null; i++) {
            Path candidate = dir.resolve("APP-META/bootstrap/sql/001_schema.sql");
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        throw new IllegalStateException(
                "APP-META/bootstrap/sql/001_schema.sql not found from user.dir="
                        + System.getProperty("user.dir"));
    }
}
