package com.xmut.ebus;

import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import com.xmut.ebus.interfaces.ratelimit.AuthRateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
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
        jdbcTemplate.update("DELETE FROM ebus_artifact");
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
}
