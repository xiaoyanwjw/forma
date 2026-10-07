package com.xmut.forma;

import com.xmut.forma.application.business.credit.service.CreditApplicationService;
import com.xmut.forma.domain.business.credit.constant.CreditHoldStatus;
import com.xmut.forma.support.MysqlContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * True MySQL gate: reserveOne writes an ACTIVE hold row.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@ActiveProfiles({"test", "mysql-container"})
class CreditReserveMysqlContainerIT {

    @Container
    static final MySQLContainer<?> MYSQL = MysqlContainerSupport.MYSQL;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        MysqlContainerSupport.registerDatasource(registry);
    }

    @Autowired
    private CreditApplicationService creditApplicationService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void reserveOnePersistsActiveHold() {
        String userId = UUID.randomUUID().toString();

        String holdId = creditApplicationService.reserveOne(userId);

        Integer holds = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM forma_credit_hold WHERE biz_id = ? AND user_id = ? AND status = ?",
                Integer.class, holdId, userId, CreditHoldStatus.ACTIVE.name());
        assertThat(holdId).isNotBlank();
        assertThat(holds).isEqualTo(1);
        Integer reserved = jdbcTemplate.queryForObject(
                "SELECT reserved FROM forma_credit_account WHERE user_id = ?",
                Integer.class, userId);
        assertThat(reserved).isEqualTo(1);
    }
}
