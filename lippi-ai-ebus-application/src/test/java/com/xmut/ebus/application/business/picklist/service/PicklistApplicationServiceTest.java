package com.xmut.ebus.application.business.picklist.service;

import com.xmut.ebus.application.business.picklist.command.PersistPicklistCommand;
import com.xmut.ebus.application.business.picklist.command.PersistPicklistItemCommand;
import com.xmut.ebus.application.business.picklist.dto.PicklistArtifactDTO;
import com.xmut.ebus.common.exception.BusinessException;
import com.xmut.ebus.domain.business.picklist.model.Picklist;
import com.xmut.ebus.domain.business.picklist.repository.PicklistRepository;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PicklistApplicationServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-26T10:00:00Z");

    @Mock
    private PicklistRepository picklistRepository;

    private PicklistApplicationService service;

    @BeforeEach
    void setUp() {
        service = new PicklistApplicationService(picklistRepository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void persistUsableSavesEightItems() {
        PersistPicklistCommand cmd = PersistPicklistCommand.builder()
                .userId("u1")
                .runId("r1")
                .templateId("domestic-generic-default")
                .disclaimer("基于通用知识推断，非实时平台数据")
                .assumptions("默认")
                .items(items(8))
                .build();

        PicklistArtifactDTO dto = service.persistUsable(cmd);
        assertEquals(8, dto.getItems().size());
        ArgumentCaptor<Picklist> captor = ArgumentCaptor.forClass(Picklist.class);
        verify(picklistRepository).save(captor.capture());
        assertEquals("r1", captor.getValue().getRunId());
        assertEquals(8, captor.getValue().getItems().size());
    }

    @Test
    void persistUsableRejectsSevenItems() {
        PersistPicklistCommand cmd = PersistPicklistCommand.builder()
                .userId("u1")
                .runId("r1")
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
                .reason("理由")
                .differentiation("差异")
                .demand("需求")
                .competition("竞争")
                .margin("利润")
                .risk("风险")
                .build());
        PersistPicklistCommand cmd = PersistPicklistCommand.builder()
                .userId("u1")
                .runId("r1")
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
                    .reason("理由")
                    .differentiation("差异")
                    .demand("需求")
                    .competition("竞争")
                    .margin("利润")
                    .risk("风险")
                    .build());
        }
        return list;
    }
}
