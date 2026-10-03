package com.xmut.forma;

import com.xmut.forma.domain.business.artifact.model.Artifact;
import com.xmut.forma.domain.business.artifact.model.ArtifactType;
import com.xmut.forma.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.forma.interfaces.ratelimit.AuthRateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class ArtifactRepositoryIntegrationTest {

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM forma_artifact");
    }

    @Test
    void saveAndFindByIdRoundTrip() {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        String id = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String payload = "{\"disclaimer\":\"基于通用知识推断，非实时平台数据\",\"items\":[]}";
        Artifact artifact = Artifact.create(
                id, "user-1", runId, ArtifactType.PICKLIST, "ecommerce",
                "domestic-generic-default", "选品清单", payload, now);
        artifactRepository.save(artifact);

        Optional<Artifact> loaded = artifactRepository.findById(id);
        assertTrue(loaded.isPresent());
        assertEquals(ArtifactType.PICKLIST, loaded.get().getType());
        assertEquals("ecommerce", loaded.get().getSceneCode());
        assertEquals(payload, loaded.get().getPayloadJson());
        assertEquals(id, artifactRepository.findByRunId(runId).get().getId());
    }

    @Test
    void listByUserSinceFiltersWindowTypeAndScene() {
        Instant now = Instant.parse("2026-09-28T12:00:00Z");
        artifactRepository.save(Artifact.create(
                UUID.randomUUID().toString(), "user-1", UUID.randomUUID().toString(),
                ArtifactType.PICKLIST, "ecommerce", null, "近", "{}", now.minus(1, ChronoUnit.DAYS)));
        artifactRepository.save(Artifact.create(
                UUID.randomUUID().toString(), "user-1", UUID.randomUUID().toString(),
                ArtifactType.SKU, "ecommerce", null, "sku", "{}", now.minus(2, ChronoUnit.DAYS)));
        artifactRepository.save(Artifact.create(
                UUID.randomUUID().toString(), "user-1", UUID.randomUUID().toString(),
                ArtifactType.CHAT, "ecommerce", null, "chat", "{}", now.minus(1, ChronoUnit.HOURS)));
        artifactRepository.save(Artifact.create(
                UUID.randomUUID().toString(), "user-1", UUID.randomUUID().toString(),
                ArtifactType.PICKLIST, "ecommerce", null, "旧", "{}", now.minus(70, ChronoUnit.DAYS)));
        artifactRepository.save(Artifact.create(
                UUID.randomUUID().toString(), "user-2", UUID.randomUUID().toString(),
                ArtifactType.PICKLIST, "ecommerce", null, "他人", "{}", now));

        List<Artifact> rows = artifactRepository.listByUserSince(
                "user-1",
                now.minus(60, ChronoUnit.DAYS),
                Arrays.asList(ArtifactType.PICKLIST, ArtifactType.SKU),
                "ecommerce");
        assertEquals(2, rows.size());
        assertEquals("近", rows.get(0).getTitle());
        assertEquals("sku", rows.get(1).getTitle());
    }
}
