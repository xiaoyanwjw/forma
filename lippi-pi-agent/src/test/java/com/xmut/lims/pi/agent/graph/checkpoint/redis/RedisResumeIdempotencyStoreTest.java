package com.xmut.lims.pi.agent.graph.checkpoint.redis;

import com.xmut.lims.pi.agent.ConversationResult;
import com.xmut.lims.pi.agent.graph.checkpoint.ResumeIdempotencyStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class RedisResumeIdempotencyStoreTest {

    @Mock
    private PiRedisCommands redis;

    private final ConcurrentHashMap<String, String> kv = new ConcurrentHashMap<>();
    private RedisResumeIdempotencyStore store;

    @BeforeEach
    void setUp() {
        kv.clear();
        store = new RedisResumeIdempotencyStore(redis, 100);
        lenient().when(redis.setIfAbsent(anyString(), anyString(), anyLong())).thenAnswer(inv -> {
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            return kv.putIfAbsent(key, value) == null;
        });
        lenient().when(redis.get(anyString())).thenAnswer(inv -> kv.get(inv.getArgument(0)));
        lenient().doAnswer(inv -> {
            kv.put(inv.getArgument(0), inv.getArgument(2));
            return null;
        }).when(redis).setex(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString());
        lenient().doAnswer(inv -> {
            kv.remove((String) inv.getArgument(0));
            return null;
        }).when(redis).del(anyString());
    }

    @Test
    void claim_complete_secondClaimReturnsCompleted() {
        ResumeIdempotencyStore.ClaimResult first = store.claim("r1", "c1");
        assertThat(first.getStatus()).isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);

        ConversationResult result = ConversationResult.ok("r1", "done", java.util.Collections.emptyList());
        store.complete("r1", "c1", result);

        ResumeIdempotencyStore.ClaimResult second = store.claim("r1", "c1");
        assertThat(second.getStatus()).isEqualTo(ResumeIdempotencyStore.ClaimStatus.COMPLETED);
        assertThat(second.getCompletedResult().getFinalResponse()).isEqualTo("done");
        assertThat(kv.keySet()).allMatch(k -> k.startsWith("pi:resume-idem:"));
        assertThat(kv.keySet()).noneMatch(k -> k.contains(":t1:"));
        assertThat(RedisResumeIdempotencyStore.entryKey("r1", "c1"))
                .isEqualTo("pi:resume-idem:r1:c1");
    }

    @Test
    void inProgress_secondClaim_conflicts() {
        assertThat(store.claim("r1", "c2").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
        assertThat(store.claim("r1", "c2").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.IN_PROGRESS);
    }

    @Test
    void abandon_allowsReclaim() {
        store.claim("r1", "c3");
        store.abandon("r1", "c3");
        assertThat(store.claim("r1", "c3").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
    }

    @Test
    void claim_afterSetNxMissAndKeyExpired_retriesAndClaims() {
        java.util.concurrent.atomic.AtomicInteger setCalls = new java.util.concurrent.atomic.AtomicInteger();
        lenient().when(redis.setIfAbsent(anyString(), anyString(), anyLong())).thenAnswer(inv -> {
            int n = setCalls.incrementAndGet();
            String key = inv.getArgument(0);
            String value = inv.getArgument(1);
            if (n == 1) {
                return false;
            }
            return kv.putIfAbsent(key, value) == null;
        });
        lenient().when(redis.get(anyString())).thenAnswer(inv -> kv.get(inv.getArgument(0)));

        assertThat(store.claim("r1", "c-expire").getStatus())
                .isEqualTo(ResumeIdempotencyStore.ClaimStatus.CLAIMED);
        assertThat(setCalls.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void claim_corruptCompleted_returnsFailedCompleted() {
        String key = RedisResumeIdempotencyStore.entryKey("r1", "c-bad");
        kv.put(key, "{\"phase\":\"completed\",\"status\":\"NOT_A_REAL_STATUS\"}");
        lenient().when(redis.setIfAbsent(anyString(), anyString(), anyLong())).thenReturn(false);

        ResumeIdempotencyStore.ClaimResult result = store.claim("r1", "c-bad");
        assertThat(result.getStatus()).isEqualTo(ResumeIdempotencyStore.ClaimStatus.COMPLETED);
        assertThat(result.getCompletedResult().getStatus())
                .isEqualTo(ConversationResult.Status.FAILED);
        assertThat(result.getCompletedResult().getFinalResponse()).contains("corrupt");
    }
}
