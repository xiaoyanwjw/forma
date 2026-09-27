package com.xmut.ebus.application.business.agent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.domain.business.artifact.model.Artifact;
import com.xmut.ebus.domain.business.artifact.model.ArtifactType;
import com.xmut.ebus.domain.business.artifact.repository.ArtifactRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ArtifactPersistPluginTest {

    private static final Instant NOW = Instant.parse("2026-09-27T08:00:00Z");

    @Mock
    private ArtifactRepository artifactRepository;

    private ObjectMapper objectMapper;
    private ArtifactPersistPlugin plugin;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        plugin = new ArtifactPersistPlugin(
                artifactRepository,
                objectMapper,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void persist_noneMapsToChat_andStoresViewPlusData() throws Exception {
        PersistedGenerationArtifact out = plugin.persist(
                "u1", "r1", "ecommerce", SkillRunProfile.PERSIST_NONE,
                markdownViewMap(), Collections.singletonMap("text", "草稿"));
        assertNotNull(out.getArtifactRef());
        ArgumentCaptor<Artifact> cap = ArgumentCaptor.forClass(Artifact.class);
        verify(artifactRepository).save(cap.capture());
        assertEquals(ArtifactType.CHAT, cap.getValue().getType());
        Map<?, ?> payload = objectMapper.readValue(cap.getValue().getPayloadJson(), Map.class);
        assertTrue(payload.containsKey("view"));
        assertEquals("草稿", ((Map<?, ?>) payload.get("data")).get("text"));
    }

    @Test
    void persist_picklistType_noItemCountGate() {
        plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_PICKLIST,
                listViewMap(), singletonArtifactWithOneItem());
        verify(artifactRepository).save(any(Artifact.class));
    }

    private static Map<String, Object> markdownViewMap() {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("version", 1);
        view.put("title", "draft");
        List<Map<String, Object>> blocks = new ArrayList<Map<String, Object>>();
        Map<String, Object> md = new LinkedHashMap<String, Object>();
        md.put("type", "markdown");
        md.put("text", "some text");
        blocks.add(md);
        view.put("blocks", blocks);
        return view;
    }

    private static Map<String, Object> listViewMap() {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("version", 1);
        view.put("title", "report");
        return view;
    }

    private static Map<String, Object> singletonArtifactWithOneItem() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        Map<String, Object> item = new LinkedHashMap<String, Object>();
        item.put("title", "one");
        items.add(item);
        payload.put("items", items);
        return payload;
    }
}
