package com.xmut.ebus.infrastructure.persistence.repository.business.agent;

import com.xmut.ebus.infrastructure.persistence.mybatis.mapper.GenerationRunMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void findLatestSettledArtifactRefBySessionDelegatesToMapper() {
        when(generationRunMapper.selectLatestUsableArtifactRefBySession("user-1", "sess-1"))
                .thenReturn("art-9");

        Optional<String> found = repository.findLatestSettledArtifactRefBySession("user-1", "sess-1");

        assertEquals("art-9", found.get());
        verify(generationRunMapper).selectLatestUsableArtifactRefBySession("user-1", "sess-1");
    }

    @Test
    void findLatestSettledArtifactRefBySessionEmptyWhenMapperNull() {
        when(generationRunMapper.selectLatestUsableArtifactRefBySession("user-1", "sess-1"))
                .thenReturn(null);

        assertFalse(repository.findLatestSettledArtifactRefBySession("user-1", "sess-1").isPresent());
    }
}
