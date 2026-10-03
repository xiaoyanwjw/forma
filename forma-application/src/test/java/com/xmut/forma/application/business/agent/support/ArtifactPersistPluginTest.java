package com.xmut.forma.application.business.agent.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.forma.domain.business.artifact.model.Artifact;
import com.xmut.forma.domain.business.artifact.model.ArtifactType;
import com.xmut.forma.domain.business.artifact.repository.ArtifactRepository;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.xmut.forma.common.exception.BusinessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
        org.mockito.Mockito.lenient().when(artifactRepository.findByRunId(any()))
                .thenReturn(Optional.<Artifact>empty());
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

    @Test
    void persist_skuType_requiresUsablePayload() {
        plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_SKU,
                listViewMap(), usableSkuPayload());
        ArgumentCaptor<Artifact> cap = ArgumentCaptor.forClass(Artifact.class);
        verify(artifactRepository).save(cap.capture());
        assertEquals(ArtifactType.SKU, cap.getValue().getType());
    }

    @Test
    void persist_skuType_acceptsEmptyMediaObjectIds() {
        Map<String, Object> payload = usableSkuPayload();
        payload.put("mediaObjectIds", Collections.emptyList());
        plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_SKU, listViewMap(), payload);
        verify(artifactRepository).save(any(Artifact.class));
    }

    @Test
    void persist_skuType_rejectsBadTemplateId() {
        Map<String, Object> bad = usableSkuPayload();
        bad.put("templateId", "other-template");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_SKU, listViewMap(), bad));
        assertEquals(ArtifactPersistPlugin.MSG_SKU_UNUSABLE, ex.getMessage());
        verify(artifactRepository, never()).save(any(Artifact.class));
    }

    @Test
    void persist_skuType_rejectsMissingDetailBody() {
        Map<String, Object> bad = usableSkuPayload();
        bad.put("detailBody", "  ");
        assertThrows(BusinessException.class, () ->
                plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_SKU, listViewMap(), bad));
        verify(artifactRepository, never()).save(any(Artifact.class));
    }

    @Test
    void persist_skuType_rejectsMissingDisplayNotes() {
        Map<String, Object> bad = usableSkuPayload();
        bad.remove("displayNotes");
        assertThrows(BusinessException.class, () ->
                plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_SKU, listViewMap(), bad));
        verify(artifactRepository, never()).save(any(Artifact.class));
    }

    @Test
    void persist_skuType_acceptsAssumptionsWhenUserInfoSparse() {
        Map<String, Object> payload = usableSkuPayload();
        payload.put("assumptions", "用户提到优先淘宝；仍输出跨平台公共底稿");
        plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_SKU, listViewMap(), payload);
        verify(artifactRepository).save(any(Artifact.class));
    }

    @Test
    void persist_listingPlan_rejectsFewerThanThreeFrames() {
        Map<String, Object> p = new LinkedHashMap<String, Object>();
        p.put("templateId", "domestic-generic-default");
        p.put("driver", "痛点");
        p.put("frames", Collections.singletonList("只有一张"));
        p.put("modules", Arrays.asList("a", "b", "c"));
        p.put("titleDraft", "标题");
        assertThrows(BusinessException.class, () ->
                plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_LISTING_PLAN, planView(), p));
        verify(artifactRepository, never()).save(any(Artifact.class));
    }

    @Test
    void persist_xhsTopiclist_mapsType_andRequiresNonEmptyViewAndArtifact() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("title", "选题清单");
        PersistedGenerationArtifact out = plugin.persist(
                "u1", "r1", "xiaohongshu", SkillRunProfile.PERSIST_XHS_TOPICLIST,
                listViewMap(), artifact);
        assertNotNull(out.getArtifactRef());
        ArgumentCaptor<Artifact> cap = ArgumentCaptor.forClass(Artifact.class);
        verify(artifactRepository).save(cap.capture());
        assertEquals(ArtifactType.XHS_TOPICLIST, cap.getValue().getType());
    }

    @Test
    void persist_xhsNote_rejectsEmptyArtifact() {
        BusinessException ex = assertThrows(BusinessException.class, () ->
                plugin.persist("u1", "r1", "xiaohongshu", SkillRunProfile.PERSIST_XHS_NOTE,
                        listViewMap(), Collections.<String, Object>emptyMap()));
        assertEquals(ArtifactPersistPlugin.MSG_XHS_UNUSABLE, ex.getMessage());
        verify(artifactRepository, never()).save(any(Artifact.class));
    }

    @Test
    void persist_xhsBreak_rejectsEmptyView() {
        Map<String, Object> artifact = new LinkedHashMap<String, Object>();
        artifact.put("title", "爆文拆解");
        BusinessException ex = assertThrows(BusinessException.class, () ->
                plugin.persist("u1", "r1", "xiaohongshu", SkillRunProfile.PERSIST_XHS_BREAK,
                        Collections.<String, Object>emptyMap(), artifact));
        assertEquals(ArtifactPersistPlugin.MSG_XHS_UNUSABLE, ex.getMessage());
        verify(artifactRepository, never()).save(any(Artifact.class));
    }

    @Test
    void persist_listingPlan_acceptsMinimalPlan() {
        PersistedGenerationArtifact out = plugin.persist(
                "u1", "r1", "ecommerce", SkillRunProfile.PERSIST_LISTING_PLAN, planView(), usablePlanPayload());
        assertNotNull(out.getArtifactRef());
        ArgumentCaptor<Artifact> cap = ArgumentCaptor.forClass(Artifact.class);
        verify(artifactRepository).save(cap.capture());
        assertEquals(ArtifactType.LISTING_PLAN, cap.getValue().getType());
    }

    @Test
    void persist_sameRun_listingPlanThenSku_updatesExistingRow() {
        Artifact existing = Artifact.create(
                "art-plan-1", "u1", "r1", ArtifactType.LISTING_PLAN, "ecommerce",
                null, "策划分镜", "{\"view\":{},\"data\":{}}", NOW);
        when(artifactRepository.findByRunId("r1")).thenReturn(Optional.of(existing));

        PersistedGenerationArtifact out = plugin.persist(
                "u1", "r1", "ecommerce", SkillRunProfile.PERSIST_SKU, listViewMap(), usableSkuPayload());

        assertEquals("art-plan-1", out.getArtifactRef());
        verify(artifactRepository, never()).save(any(Artifact.class));
        ArgumentCaptor<Artifact> cap = ArgumentCaptor.forClass(Artifact.class);
        verify(artifactRepository).update(cap.capture());
        assertEquals(ArtifactType.SKU, cap.getValue().getType());
        assertEquals("art-plan-1", cap.getValue().getId());
    }

    private static Map<String, Object> usablePlanPayload() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("templateId", "domestic-generic-default");
        payload.put("driver", "Mac Mini 扩展痛点");
        payload.put("frames", Arrays.asList("主图：白底俯拍", "场景：水槽旁", "细节：导流槽"));
        payload.put("modules", Arrays.asList("材质说明", "尺寸规格", "使用场景"));
        payload.put("titleDraft", "Mac Mini 拓展坞 桌面不乱");
        return payload;
    }

    private static Map<String, Object> planView() {
        Map<String, Object> view = new LinkedHashMap<String, Object>();
        view.put("version", 1);
        view.put("title", "策划分镜");
        return view;
    }

    private static Map<String, Object> usableSkuPayload() {
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put("title", "Mac Mini 拓展坞 · 上架素材");
        payload.put("templateId", "domestic-generic-default");
        payload.put("heroPlan", "白底俯拍");
        payload.put("detailTitle", "Mac Mini 拓展坞");
        payload.put("detailBody", "易清洗");
        payload.put("displayNotes", "主图突出颜色");
        payload.put("mediaObjectIds", Collections.singletonList("media-1"));
        return payload;
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
