package com.xmut.forma.infrastructure.persistence.repository.business.agent;

import com.xmut.forma.infrastructure.persistence.mybatis.mapper.PiSessionMapper;
import com.xmut.forma.infrastructure.persistence.mybatis.po.PiSessionPO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PiSessionSceneRepositoryImplTest {

    private static final String SESSION = "sess-1";
    private static final String USER = "user-1";
    private static final String SCENE_ID = "a1000001-0001-4000-8000-000000000001";
    private static final String SCENE_CODE = "ecommerce";

    @Mock
    private PiSessionMapper sessionMapper;

    private PiSessionSceneRepositoryImpl repository;

    @BeforeEach
    void setUp() {
        repository = new PiSessionSceneRepositoryImpl(sessionMapper);
    }

    @Test
    void ensureBoundSkipsUpdateSceneWhenAlreadyMatchedAndOnlyFillsUserId() {
        PiSessionPO existing = row(SESSION, null, SCENE_ID, SCENE_CODE);
        when(sessionMapper.selectById(SESSION)).thenReturn(existing);

        repository.ensureBound(SESSION, SCENE_ID, SCENE_CODE, USER);

        verify(sessionMapper, never()).updateScene(anyString(), anyString(), anyString(), any(Instant.class));
        verify(sessionMapper).updateUserIdIfNull(SESSION, USER);
    }

    @Test
    void ensureBoundWritesSceneWhenUnboundThenFillsUserId() {
        PiSessionPO existing = row(SESSION, null, null, null);
        when(sessionMapper.selectById(SESSION)).thenReturn(existing);

        repository.ensureBound(SESSION, SCENE_ID, SCENE_CODE, USER);

        verify(sessionMapper).updateScene(eq(SESSION), eq(SCENE_ID), eq(SCENE_CODE), any(Instant.class));
        verify(sessionMapper).updateUserIdIfNull(SESSION, USER);
    }

    @Test
    void ensureBoundRejectsDifferentSceneWithoutRewrite() {
        PiSessionPO existing = row(SESSION, USER, "other-scene-id", "short_video");
        when(sessionMapper.selectById(SESSION)).thenReturn(existing);

        assertThrows(IllegalStateException.class,
                () -> repository.ensureBound(SESSION, SCENE_ID, SCENE_CODE, USER));
        verify(sessionMapper, never()).updateScene(anyString(), anyString(), anyString(), any(Instant.class));
        verify(sessionMapper, never()).updateUserIdIfNull(anyString(), anyString());
    }

    private static PiSessionPO row(String sessionId, String userId, String sceneId, String sceneCode) {
        PiSessionPO po = new PiSessionPO();
        po.setSessionId(sessionId);
        po.setUserId(userId);
        po.setSceneId(sceneId);
        po.setSceneCode(sceneCode);
        return po;
    }
}
