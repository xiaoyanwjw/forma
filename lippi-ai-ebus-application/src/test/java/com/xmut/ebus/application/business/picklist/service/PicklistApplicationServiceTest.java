package com.xmut.ebus.application.business.picklist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistItemCommand;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.common.exception.BusinessException;
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
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PicklistApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Mock
    private ArtifactRepository artifactRepository;

    private PicklistApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PicklistApplicationService(
                artifactRepository, new ObjectMapper(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void persistUsableSavesPicklistArtifact() throws Exception {
        PersistPicklistCommand cmd = PersistPicklistCommand.builder()
                .userId("u1").runId("r1").sceneCode("ecommerce")
                .templateId("domestic-generic-default")
                .disclaimer("基于通用知识推断，非实时平台数据")
                .items(items(8)).build();
        PicklistArtifactDTO dto = service.persistUsable(cmd);
        assertEquals(8, dto.getItems().size());
        assertEquals("台面积水", dto.getItems().get(0).getPainPoint());
        ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
        verify(artifactRepository).save(captor.capture());
        Artifact saved = captor.getValue();
        assertEquals(ArtifactType.PICKLIST, saved.getType());
        assertEquals("ecommerce", saved.getSceneCode());
        assertEquals("选品清单", saved.getTitle());
        assertTrue(saved.getPayloadJson().contains("\"painPoint\""));
        assertTrue(saved.getPayloadJson().contains("\"sourceUrl\""));
        assertEquals("https://item.example/0", dto.getItems().get(0).getSourceUrl());
        assertEquals(saved.getId(), dto.getPicklistId());
    }

    @Test
    void persistUsableRejectsSevenItems() {
        PersistPicklistCommand cmd = PersistPicklistCommand.builder()
                .userId("u1")
                .runId("r1")
                .sceneCode("ecommerce")
                .disclaimer("基于通用知识推断，非实时平台数据")
                .items(items(7))
                .build();
        assertThrows(BusinessException.class, () -> service.persistUsable(cmd));
    }

    @Test
    void persistUsableRejectsOverlongTitle() {
        List<PersistPicklistItemCommand> list = items(8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 257; i++) {
            sb.append('x');
        }
        list.set(0, PersistPicklistItemCommand.builder()
                .title(sb.toString())
                .priceBand("19-39")
                .painPoint("痛点")
                .angle("切入")
                .diff("差异")
                .niche("细分")
                .demand("需求")
                .competition("竞争")
                .margin("利润")
                .risk("风险")
                .sourceUrl("https://item.example/0")
                .build());
        PersistPicklistCommand cmd = PersistPicklistCommand.builder()
                .userId("u1")
                .runId("r1")
                .sceneCode("ecommerce")
                .disclaimer("基于通用知识推断，非实时平台数据")
                .items(list)
                .build();
        assertThrows(BusinessException.class, () -> service.persistUsable(cmd));
    }

    private static List<PersistPicklistItemCommand> items(int n) {
        List<PersistPicklistItemCommand> list = new ArrayList<PersistPicklistItemCommand>();
        for (int i = 0; i < n; i++) {
            list.add(PersistPicklistItemCommand.builder()
                    .title("品" + i)
                    .priceBand("19-39")
                    .painPoint("台面积水")
                    .angle("租房刚需")
                    .diff("多色套装")
                    .niche("细分" + (i % 3))
                    .demand("需求")
                    .competition("竞争")
                    .margin("利润")
                    .risk("风险")
                    .sourceUrl("https://item.example/" + i)
                    .build());
        }
        return list;
    }
}
