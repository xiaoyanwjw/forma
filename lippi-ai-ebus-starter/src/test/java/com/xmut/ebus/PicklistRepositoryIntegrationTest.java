package com.xmut.ebus;

import com.xmut.ebus.domain.business.picklist.constant.PicklistDefaults;
import com.xmut.ebus.domain.business.picklist.model.Picklist;
import com.xmut.ebus.domain.business.picklist.model.PicklistItem;
import com.xmut.ebus.domain.business.picklist.repository.PicklistRepository;
import com.xmut.ebus.interfaces.ratelimit.AuthRateLimitInterceptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class PicklistRepositoryIntegrationTest {

    @Autowired
    private PicklistRepository picklistRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AuthRateLimitInterceptor authRateLimitInterceptor;

    @BeforeEach
    void clean() {
        authRateLimitInterceptor.reset();
        jdbcTemplate.update("DELETE FROM ebus_picklist_item");
        jdbcTemplate.update("DELETE FROM ebus_picklist");
    }

    @Test
    void saveAndFindByIdRoundTrip() {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        String picklistId = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        List<PicklistItem> items = new ArrayList<PicklistItem>();
        for (int i = 0; i < 8; i++) {
            items.add(PicklistItem.of(
                    UUID.randomUUID().toString(),
                    picklistId,
                    i,
                    "品" + i,
                    "19-39",
                    "理由" + i,
                    "差异" + i,
                    "需求",
                    "竞争",
                    "利润",
                    "风险",
                    now));
        }
        Picklist picklist = Picklist.create(
                picklistId,
                UUID.randomUUID().toString(),
                runId,
                PicklistDefaults.TEMPLATE_ID,
                "基于通用知识推断，非实时平台数据",
                "默认假设",
                items,
                now);

        picklistRepository.save(picklist);

        Optional<Picklist> loaded = picklistRepository.findById(picklistId);
        assertTrue(loaded.isPresent());
        assertEquals(runId, loaded.get().getRunId());
        assertEquals(PicklistDefaults.TEMPLATE_ID, loaded.get().getTemplateId());
        assertEquals(8, loaded.get().getItems().size());
        assertEquals("品0", loaded.get().getItems().get(0).getTitle());

        Optional<Picklist> byRun = picklistRepository.findByRunId(runId);
        assertTrue(byRun.isPresent());
        assertEquals(picklistId, byRun.get().getId());
    }
}
