package com.xmut.lims.pi.agent.session;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * SessionStore ≠ CheckpointStore：前缀不得共享（Story 51-12 AC5 / 51-16）。
 *
 * <p>生产 Session = SQLite（51-17），不做 Redis；{@link SessionStore#REDIS_KEY_PREFIX}
 * 仅作文档边界常量（已 @Deprecated）。
 */
class SessionStorePrefixIsolationTest {

    @Test
    @SuppressWarnings("deprecation")
    void session_prefix_differs_from_checkpoint_and_resume_idem() {
        assertThat(SessionStore.REDIS_KEY_PREFIX).isEqualTo("pi:session:");
        assertThat(SessionStore.REDIS_KEY_PREFIX).isNotEqualTo("pi:checkpoint:");
        assertThat(SessionStore.REDIS_KEY_PREFIX).isNotEqualTo("pi:resume-idem:");
        assertThat(SessionStore.REDIS_KEY_PREFIX).doesNotStartWith("pi:checkpoint:");
        assertThat(SessionStore.REDIS_KEY_PREFIX).doesNotStartWith("pi:resume-idem:");
    }
}
