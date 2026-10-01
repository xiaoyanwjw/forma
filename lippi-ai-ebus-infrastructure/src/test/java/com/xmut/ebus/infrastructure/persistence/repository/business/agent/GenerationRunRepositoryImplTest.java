package com.xmut.ebus.infrastructure.persistence.repository.business.agent;

import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.GenerationRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GenerationRunRepositoryImplTest {

    @Mock
    private GenerationRunMapper generationRunMapper;

    private GenerationRunRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new GenerationRunRepositoryImpl(generationRunMapper);
    }

    @Test
    void untypedLatestArtifactSqlIncludesXhsHistoryTypes() throws Exception {
        InputStream in = GenerationRunMapper.class.getResourceAsStream(
                "/mybatis/mapper/GenerationRunMapper.xml");
        assertNotNull(in);
        String xml;
        try (Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
            scanner.useDelimiter("\\A");
            xml = scanner.hasNext() ? scanner.next() : "";
        }
        assertTrue(xml.contains("'picklist', 'sku', 'xhs_topiclist', 'xhs_note', 'xhs_break'"));
    }

    @Test
    void findLatestSettledArtifactRefBySessionDelegatesToMapper() {
        Instant since = Instant.parse("2026-07-30T12:00:00Z");
        when(generationRunMapper.selectLatestUsableArtifactRefBySession("user-1", "sess-1", since, null))
                .thenReturn("art-9");

        Optional<String> found = repository.findLatestSettledArtifactRefBySession("user-1", "sess-1", since);

        assertEquals("art-9", found.get());
        verify(generationRunMapper).selectLatestUsableArtifactRefBySession("user-1", "sess-1", since, null);
    }

    @Test
    void findLatestSettledArtifactRefBySessionEmptyWhenMapperNull() {
        Instant since = Instant.parse("2026-07-30T12:00:00Z");
        when(generationRunMapper.selectLatestUsableArtifactRefBySession("user-1", "sess-1", since, null))
                .thenReturn(null);

        assertFalse(repository.findLatestSettledArtifactRefBySession("user-1", "sess-1", since).isPresent());
    }

    @Test
    void findLatestSettledArtifactRefBySessionWithTypeDelegates() {
        Instant since = Instant.parse("2026-07-30T12:00:00Z");
        when(generationRunMapper.selectLatestUsableArtifactRefBySession(
                "user-1", "sess-1", since, "picklist"))
                .thenReturn("art-pick");

        Optional<String> found = repository.findLatestSettledArtifactRefBySession(
                "user-1", "sess-1", since, "picklist");

        assertEquals("art-pick", found.get());
    }
}
