package com.xmut.ebus;

import com.xmut.ebus.infrastructure.checkpoint.MysqlResumeIdempotencyStore;
import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.graph.checkpoint.InMemoryResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import com.xmut.lims.pi.agent.graph.checkpoint.redis.RedisResumeIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Story 2.8b：MysqlResumeIdempotencyStore 矩阵与 Adam Primary 装配。
 */
@SpringBootTest
@ActiveProfiles("test")
class MysqlResumeIdempotencyStoreIntegrationTest {

    @Autowired
    private ResumeIdempotencyStore resumeIdempotencyStore;

    @Autowired
    private MysqlResumeIdempotencyStore mysqlResumeIdempotencyStore;

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM pi_resume_idempotency");
    }

    @Test
    void primaryBean_isMysql_notRedisOrInMemory() {
        assertThat(resumeIdempotencyStore).isSameAs(mysqlResumeIdempotencyStore);
        assertThat(resumeIdempotencyStore).isInstanceOf(MysqlResumeIdempotencyStore.class);
        Map<String, ResumeIdempotencyStore> beans =
                applicationContext.getBeansOfType(ResumeIdempotencyStore.class);
        assertThat(beans.values()).hasSize(1);
        assertThat(beans.values()).noneMatch(b -> b instanceof RedisResumeIdempotencyStore);
        assertThat(beans.values()).noneMatch(b -> b instanceof InMemoryResumeIdempotencyStore);
        assertThat(applicationContext.getEnvironment()
                .getProperty("lims.pi.checkpoint.redis.enabled", "false"))
                .isEqualTo("false");
    }

    @Test
    void claim_complete_secondClaimReturnsCompleted() {
        ResumeIdempotencyStore.ClaimResult first = mysqlResumeIdempotencyStore.claim("r1", "c1");
        assertThat(first.getStatus()).isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);

        ConversationResult result = ConversationResult.ok("r1", "done", Collections.emptyList());
        mysqlResumeIdempotencyStore.complete("r1", "c1", result);

        ResumeIdempotencyStore.ClaimResult second = mysqlResumeIdempotencyStore.claim("r1", "c1");
        assertThat(second.getStatus()).isEqualTo(ResumeIdempotencyStore.ClaimStatus.COMPLETED);
        assertThat(second.getCompletedResult().getFinalResponse()).isEqualTo("done");

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_resume_idempotency WHERE run_id = ? AND confirm_request_id = ?",
                Integer.class, "r1", "c1");
        assertThat(rows).isEqualTo(1);
        // 与 CP / Session 分表
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_graph_checkpoint", Integer.class)).isNotNull();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_session", Integer.class)).isNotNull();
    }

    @Test
    void inProgress_secondClaim_conflicts() {
        assertThat(mysqlResumeIdempotencyStore.claim("r1", "c2").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
        assertThat(mysqlResumeIdempotencyStore.claim("r1", "c2").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.IN_PROGRESS);
    }

    @Test
    void abandon_allowsReclaim() {
        mysqlResumeIdempotencyStore.claim("r1", "c3");
        mysqlResumeIdempotencyStore.abandon("r1", "c3");
        assertThat(mysqlResumeIdempotencyStore.claim("r1", "c3").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
    }

    @Test
    void expired_treatedAsMissing_allowsReclaim() {
        assertThat(mysqlResumeIdempotencyStore.claim("r-exp", "c-exp").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
        jdbcTemplate.update(
                "UPDATE pi_resume_idempotency SET expires_at = ? WHERE run_id = ? AND confirm_request_id = ?",
                Timestamp.from(Instant.now().minusSeconds(60)),
                "r-exp", "c-exp");
        assertThat(mysqlResumeIdempotencyStore.claim("r-exp", "c-exp").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
    }

    @Test
    void deleteByRun_removesAllKeys() {
        mysqlResumeIdempotencyStore.claim("r-del", "c-a");
        mysqlResumeIdempotencyStore.complete("r-del", "c-a",
                ConversationResult.ok("r-del", "a", Collections.emptyList()));
        mysqlResumeIdempotencyStore.claim("r-del", "c-b");
        mysqlResumeIdempotencyStore.deleteByRun("r-del");
        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM pi_resume_idempotency WHERE run_id = ?",
                Integer.class, "r-del");
        assertThat(rows).isZero();
        assertThat(mysqlResumeIdempotencyStore.claim("r-del", "c-a").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
    }

    @Test
    void claim_corruptCompleted_returnsFailedCompleted() {
        jdbcTemplate.update(
                "INSERT INTO pi_resume_idempotency "
                        + "(run_id, confirm_request_id, phase, result_summary, updated_at, expires_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)",
                "r-bad", "c-bad", "completed",
                "{\"phase\":\"completed\",\"status\":\"NOT_A_REAL_STATUS\"}",
                Timestamp.from(Instant.now()),
                Timestamp.from(Instant.now().plusSeconds(3600)));

        ResumeIdempotencyStore.ClaimResult result = mysqlResumeIdempotencyStore.claim("r-bad", "c-bad");
        assertThat(result.getStatus()).isEqualTo(ResumeIdempotencyStore.ClaimStatus.COMPLETED);
        assertThat(result.getCompletedResult().getStatus())
                .isEqualTo(ConversationResult.Status.FAILED);
        assertThat(result.getCompletedResult().getFinalResponse()).contains("corrupt");
    }

    @Test
    void ttlSeconds_defaultsTo86400() {
        assertThat(mysqlResumeIdempotencyStore.getTtlSeconds()).isEqualTo(86400);
    }

    @Test
    void claim_expiresAt_nearNowPlusTtl() {
        Instant before = Instant.now();
        assertThat(mysqlResumeIdempotencyStore.claim("r-ttl", "c-ttl").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
        Instant after = Instant.now();
        Timestamp expiresAt = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM pi_resume_idempotency WHERE run_id = ? AND confirm_request_id = ?",
                Timestamp.class, "r-ttl", "c-ttl");
        assertThat(expiresAt).isNotNull();
        int ttl = mysqlResumeIdempotencyStore.getTtlSeconds();
        assertThat(expiresAt.toInstant())
                .isAfterOrEqualTo(before.plusSeconds(ttl).minusSeconds(1))
                .isBeforeOrEqualTo(after.plusSeconds(ttl).plusSeconds(1));
        assertThat(expiresAt.toInstant())
                .isCloseTo(before.plusSeconds(ttl), within(5, ChronoUnit.SECONDS));
    }
}
