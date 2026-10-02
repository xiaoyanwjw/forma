# Query 入参对象化 + PiMessage 命名 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Introduce `BaseQuery` + Session/History `*Query` objects (clamp in Query), rename domain `PiMessageDTO` → `PiMessage`, and tighten `02-be` / coding-style checklists — without changing two-phase SQL, ACL, HTTP paths, or response JSON.

**Architecture:** Controllers build `*Query` via `@SuperBuilder` (inject `userId` from `SecuritySupport`); QueryServices accept one Query arg and call `query.limit()` / `query.turnLimit()` / normalized field accessors; domain replay projection is `PiMessage` (not `*DTO`).

**Tech Stack:** Java 8 / Spring Boot 2.7 / Lombok `@SuperBuilder` / JUnit 5 + Mockito

**Spec:** `docs/superpowers/specs/2026-10-02-query-object-and-domain-projection-naming-design.md`

## Global Constraints

- ≥2 业务参 **或** 分页/过滤 → 必须 `*Query extends BaseQuery`；单参读方法本切片不改（Credit / Scene / Identity）
- `BaseQuery` 仅 `userId`；读路径不强制 `username`
- 禁止 `new` + setter；一律 Builder；Application / QueryService 不读 SecurityContext
- limit/clamp 在 Query 实例方法；删除 `SessionQueryService.clampLimit` / `clampTurnPage`
- `getMessageList` → `pageTurns`；`latestArtifact` → `getLatestArtifact`（合并重载）
- domain `PiMessageDTO` → `PiMessage`；Application 出口 `SessionTurnDTO` / `SessionMessageDTO` **不改名**
- 不改两段式 SQL、ACL、60 天窗、HTTP 路径与响应 JSON
- Java 8：无 `var` / `List.of`；匿名内部类写全类型参数

## File map

| 文件 | 职责 |
|------|------|
| `lippi-ai-ebus-common/.../common/query/BaseQuery.java` | 读侧基类：`userId` + `@SuperBuilder` |
| `.../session/query/SessionListQuery.java` | 列表：`sceneCode` + `limit()` |
| `.../session/query/SessionTurnPageQuery.java` | 回合分页：`sessionId` / `nextToken` / `turnLimit()` |
| `.../session/query/SessionLatestArtifactQuery.java` | 侧栏最新成果：`sessionId` / `artifactType` |
| `.../history/query/HistoryListQuery.java` | 成果列表：`sceneCode` |
| `.../history/query/HistoryArtifactQuery.java` | 成果详情：`artifactId` |
| `SessionQueryService.java` / `HistoryQueryService.java` | 改签名；删 Service 侧 clamp |
| `SessionController.java` / `HistoryController.java` | 组 Query |
| `PiMessageDTO.java` → `PiMessage.java` | domain 投影改名 |
| `sdd/context/02-be.md` · `01-coding-style.md` | 规约勾选 |

---

### Task 1: `BaseQuery`

**Files:**
- Create: `lippi-ai-ebus-common/src/main/java/com/xmut/ebus/common/query/BaseQuery.java`
- Test: `lippi-ai-ebus-common/src/test/java/com/xmut/ebus/common/query/BaseQueryTest.java`

**Interfaces:**
- Produces: `abstract class BaseQuery` with `String getUserId()`；`@Getter` `@SuperBuilder` `@EqualsAndHashCode`
- Consumes: none

- [ ] **Step 1: Write the failing test**

```java
package com.xmut.ebus.common.query;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BaseQueryTest {

    @Getter
    @SuperBuilder
    @EqualsAndHashCode(callSuper = true)
    private static final class SampleQuery extends BaseQuery {
        private final String marker;
    }

    @Test
    void builderCarriesUserIdAndSubclassField() {
        SampleQuery q = SampleQuery.builder().userId("u-1").marker("m").build();
        assertEquals("u-1", q.getUserId());
        assertEquals("m", q.getMarker());
    }

    @Test
    void userIdMayBeNullForPublicPathsIfEverNeeded() {
        SampleQuery q = SampleQuery.builder().marker("m").build();
        assertNull(q.getUserId());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lippi-ai-ebus-common -Dtest=BaseQueryTest test`

Expected: FAIL — `cannot find symbol: class BaseQuery`

- [ ] **Step 3: Implement `BaseQuery`**

```java
package com.xmut.ebus.common.query;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * 读查询基类：携带操作者 userId，供 QueryService 统一取用。
 * <p>
 * Controllers 组查询时用 {@code .userId(SecuritySupport.requireUserId())} 注入；
 * Application / QueryService 勿依赖 SecurityContext。禁止 {@code new} + setter。
 */
@Getter
@SuperBuilder
@EqualsAndHashCode
public abstract class BaseQuery {

    /** 操作者用户 ID（JWT {@code sub}） */
    private final String userId;
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -pl lippi-ai-ebus-common -Dtest=BaseQueryTest test`

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-common/src/main/java/com/xmut/ebus/common/query/BaseQuery.java \
  lippi-ai-ebus-common/src/test/java/com/xmut/ebus/common/query/BaseQueryTest.java
git commit -m "$(cat <<'EOF'
feat(common): add BaseQuery for read-side query objects

Mirror BaseCommand with userId-only SuperBuilder base so Session/History
can stop passing scattered query params.
EOF
)"
```

---

### Task 2: `PiMessageDTO` → `PiMessage`

**Files:**
- Rename/Create: `lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/agent/model/PiMessage.java`（内容同旧类，类名/构造器改名）
- Delete: `.../model/PiMessageDTO.java`
- Modify (type swap only):
  - `lippi-ai-ebus-domain/.../repository/PiSessionQueryRepository.java`
  - `lippi-ai-ebus-infrastructure/.../session/PiSessionQueryRepositoryImpl.java`
  - `lippi-ai-ebus-application/.../session/query/SessionQueryService.java`
  - `lippi-ai-ebus-application/.../session/support/SessionTurnAssembler.java`
  - `lippi-ai-ebus-application/src/test/.../session/query/SessionQueryServiceTest.java`
  - `lippi-ai-ebus-application/src/test/.../session/support/SessionTurnAssemblerTest.java`

**Interfaces:**
- Produces: `PiMessage` with identical constructors/getters as old `PiMessageDTO`
- Consumes: none（行为不变）

- [ ] **Step 1: Write the failing rename smoke in Assembler test**

在 `SessionTurnAssemblerTest` 顶部把 import / 类型 / `new PiMessageDTO` 全部改为 `PiMessage`（先改测试，确认编译失败指向旧类型）。

示例 helper：

```java
private static PiMessage msg(
        String role,
        String content,
        long seq,
        Instant at,
        String runId,
        String toolCallId,
        List<PiToolCallRef> toolCalls) {
    return new PiMessage(
            role,
            content,
            at,
            seq,
            toolCallId,
            toolCalls == null ? Collections.<PiToolCallRef>emptyList() : toolCalls,
            runId);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lippi-ai-ebus-application -am -Dtest=SessionTurnAssemblerTest test`

Expected: FAIL — `cannot find symbol: class PiMessage`

- [ ] **Step 3: Add `PiMessage` and delete `PiMessageDTO`**

把 `PiMessageDTO.java` 复制为 `PiMessage.java`，类名与全部构造器改名；删掉旧文件。Javadoc 强调：只读投影，非 HTTP DTO。

然后全局替换引用（仅 Java，勿改 Application `*DTO`）：

```bash
rg -n 'PiMessageDTO' --glob '*.java'
```

Expected after fix: 无 `PiMessageDTO` 命中（`docs/` 历史计划可留）。

- [ ] **Step 4: Run tests**

Run:

```bash
mvn -pl lippi-ai-ebus-application -am \
  -Dtest=SessionTurnAssemblerTest,SessionQueryServiceTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add -A lippi-ai-ebus-domain lippi-ai-ebus-infrastructure lippi-ai-ebus-application
git commit -m "$(cat <<'EOF'
refactor(domain): rename PiMessageDTO to PiMessage

Align replay projection naming with PiLogicalRunRef/PiSessionMeta and
reserve *DTO for Application/HTTP exits.
EOF
)"
```

---

### Task 3: `SessionListQuery` + `list(SessionListQuery)`

**Files:**
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/session/query/SessionListQuery.java`
- Create: `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/session/query/SessionListQueryTest.java`
- Modify: `.../session/query/SessionQueryService.java`（`list` 签名；删 `clampLimit` / `DEFAULT_LIMIT` / `MAX_LIMIT`）
- Modify: `.../interfaces/.../session/SessionController.java`（`list`）
- Modify: `.../SessionQueryServiceTest.java`（`list*` 用例改组 Query）

**Interfaces:**
- Consumes: `BaseQuery`
- Produces:
  - `SessionListQuery.builder().userId(...).sceneCode(...).limit(...).build()`
  - `int limit()` — null/≤0 → 50；上限 100
  - `String sceneCode()` — 空白 → null
  - `List<SessionSummaryDTO> SessionQueryService.list(SessionListQuery query)`

- [ ] **Step 1: Write failing `SessionListQueryTest`**

```java
package com.xmut.ebus.application.business.session.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionListQueryTest {

    @Test
    void limitDefaultsAndCaps() {
        assertEquals(50, SessionListQuery.builder().userId("u").build().limit());
        assertEquals(50, SessionListQuery.builder().userId("u").limit(0).build().limit());
        assertEquals(100, SessionListQuery.builder().userId("u").limit(500).build().limit());
        assertEquals(30, SessionListQuery.builder().userId("u").limit(30).build().limit());
    }

    @Test
    void sceneCodeBlankBecomesNull() {
        assertNull(SessionListQuery.builder().userId("u").sceneCode("  ").build().sceneCode());
        assertEquals("ecommerce",
                SessionListQuery.builder().userId("u").sceneCode("ecommerce").build().sceneCode());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lippi-ai-ebus-application -am -Dtest=SessionListQueryTest test`

Expected: FAIL — `cannot find symbol: class SessionListQuery`

- [ ] **Step 3: Implement `SessionListQuery`**

```java
package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SessionListQuery extends BaseQuery {

    public static final int DEFAULT_LIMIT = 50;
    public static final int MAX_LIMIT = 100;

    /** 可选场景过滤；空白经 {@link #sceneCode()} 归一为 null */
    private final String sceneCode;
    /** 可选页大小；经 {@link #limit()} clamp */
    private final Integer limit;

    public String sceneCode() {
        return StringUtils.hasText(sceneCode) ? sceneCode.trim() : null;
    }

    public int limit() {
        if (limit == null || limit.intValue() <= 0) {
            return DEFAULT_LIMIT;
        }
        return Math.min(limit.intValue(), MAX_LIMIT);
    }
}
```

- [ ] **Step 4: Wire Service + Controller + update list tests**

`SessionQueryService.list`:

```java
@Transactional(readOnly = true)
public List<SessionSummaryDTO> list(SessionListQuery query) {
    String uid = StringUtils.requireHasText(query.getUserId(), "userId required");
    String sceneCode = query.sceneCode();
    int capped = query.limit();
    Instant since = Instant.now(clock).minus(SESSION_WINDOW_DAYS, ChronoUnit.DAYS);
    // ... unchanged body using uid / sceneCode / capped
}
```

删除 `clampLimit`、以及类上的 `DEFAULT_LIMIT` / `MAX_LIMIT`（常量迁到 Query）。

`SessionController.list`:

```java
return ApiResponse.success(
        sessionQueryService.list(SessionListQuery.builder()
                .userId(SecuritySupport.requireUserId())
                .sceneCode(sceneCode)
                .limit(limit)
                .build()));
```

`SessionQueryServiceTest` 列表用例改为：

```java
List<SessionSummaryDTO> list = service.list(SessionListQuery.builder()
        .userId(USER).sceneCode(null).limit(null).build());
```

`listClampsLimitToMaxOneHundred`：

```java
assertTrue(service.list(SessionListQuery.builder().userId(USER).limit(500).build()).isEmpty());
verify(piSessionQueryRepository).selectByUserSince(eq(USER), any(Instant.class), isNull(), eq(100));
```

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl lippi-ai-ebus-application -am \
  -Dtest=SessionListQueryTest,SessionQueryServiceTest test
```

Expected: PASS（turn / latest 用例仍用旧散参签名，直到 Task 4/5）

- [ ] **Step 6: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/session/query/ \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/session/query/ \
  lippi-ai-ebus-interfaces/src/main/java/com/xmut/ebus/interfaces/web/business/session/SessionController.java
git commit -m "$(cat <<'EOF'
feat(session): accept SessionListQuery for session list

Move list page-size clamp into the query object and stop scattering
userId/scene/limit through the QueryService signature.
EOF
)"
```

---

### Task 4: `SessionTurnPageQuery` + `pageTurns`

**Files:**
- Create: `.../session/query/SessionTurnPageQuery.java`
- Create: `.../session/query/SessionTurnPageQueryTest.java`
- Modify: `SessionQueryService.java`（`getMessageList` → `pageTurns`；删 `clampTurnPage` / `TURN_PAGE_*`）
- Modify: `SessionController.java`（`listMessages`）
- Modify: `SessionQueryServiceTest.java`（全部 `getMessageList*` → `pageTurns` + Query）

**Interfaces:**
- Consumes: `BaseQuery`；现有 `getLogicalRunIds` / `getMessagesByLogicalRunIds` / `SessionTurnAssembler`（行为不变）
- Produces:
  - `int turnLimit()` — null/≤0 → 20；上限 50
  - `String nextToken()` — 空白 → null
  - `Page<SessionTurnDTO> pageTurns(SessionTurnPageQuery query)`

- [ ] **Step 1: Write failing `SessionTurnPageQueryTest`**

```java
package com.xmut.ebus.application.business.session.query;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class SessionTurnPageQueryTest {

    @Test
    void turnLimitDefaultsAndCaps() {
        assertEquals(20, SessionTurnPageQuery.builder().userId("u").sessionId("s").build().turnLimit());
        assertEquals(20, SessionTurnPageQuery.builder().userId("u").sessionId("s").limit(0).build().turnLimit());
        assertEquals(50, SessionTurnPageQuery.builder().userId("u").sessionId("s").limit(500).build().turnLimit());
        assertEquals(10, SessionTurnPageQuery.builder().userId("u").sessionId("s").limit(10).build().turnLimit());
    }

    @Test
    void nextTokenBlankBecomesNull() {
        assertNull(SessionTurnPageQuery.builder().userId("u").sessionId("s").nextToken(" ").build().nextToken());
        assertEquals("10",
                SessionTurnPageQuery.builder().userId("u").sessionId("s").nextToken("10").build().nextToken());
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lippi-ai-ebus-application -am -Dtest=SessionTurnPageQueryTest test`

Expected: FAIL — `cannot find symbol: class SessionTurnPageQuery`

- [ ] **Step 3: Implement `SessionTurnPageQuery`**

```java
package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SessionTurnPageQuery extends BaseQuery {

    public static final int TURN_PAGE_DEFAULT = 20;
    public static final int TURN_PAGE_MAX = 50;

    private final String sessionId;
    private final String nextToken;
    private final Integer limit;

    public String nextToken() {
        return StringUtils.hasText(nextToken) ? nextToken.trim() : null;
    }

    public int turnLimit() {
        if (limit == null || limit.intValue() <= 0) {
            return TURN_PAGE_DEFAULT;
        }
        return Math.min(limit.intValue(), TURN_PAGE_MAX);
    }
}
```

- [ ] **Step 4: Rewrite Service method + Controller + tests**

`SessionQueryService`：

```java
@Transactional(readOnly = true)
public Page<SessionTurnDTO> pageTurns(SessionTurnPageQuery query) {
    String uid = StringUtils.requireHasText(query.getUserId(), "userId required");
    String sid = StringUtils.requireHasText(query.getSessionId(), "sessionId required");

    PiSessionMeta row = piSessionQueryRepository.findBySessionId(sid)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE));
    if (!uid.equals(row.getUserId())) {
        throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
    }

    int turnLimit = query.turnLimit();
    Page<PiLogicalRunRef> runPage =
            piSessionQueryRepository.getLogicalRunIds(sid, query.nextToken(), turnLimit);
    // ... unchanged assemble / order body（PiMessage 已在 Task 2 改名）
}
```

删除 `clampTurnPage`、`TURN_PAGE_DEFAULT`、`TURN_PAGE_MAX`。删除旧 `getMessageList(...)`。

Controller：

```java
return ApiResponse.success(
        sessionQueryService.pageTurns(SessionTurnPageQuery.builder()
                .userId(SecuritySupport.requireUserId())
                .sessionId(sessionId)
                .nextToken(nextToken)
                .limit(limit)
                .build()));
```

测试方法可改名 `pageTurns*`；调用改为：

```java
Page<SessionTurnDTO> page = service.pageTurns(SessionTurnPageQuery.builder()
        .userId(USER).sessionId(SESSION).nextToken("10").limit(500).build());
verify(piSessionQueryRepository).getLogicalRunIds(SESSION, "10", 50);
```

- [ ] **Step 5: Run tests**

Run:

```bash
mvn -pl lippi-ai-ebus-application -am \
  -Dtest=SessionTurnPageQueryTest,SessionQueryServiceTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/session/query/ \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/session/query/ \
  lippi-ai-ebus-interfaces/src/main/java/com/xmut/ebus/interfaces/web/business/session/SessionController.java
git commit -m "$(cat <<'EOF'
feat(session): pageTurns takes SessionTurnPageQuery

Rename getMessageList to pageTurns and move turn-page clamp into the
query object while keeping GET /sessions/{id}/messages shape.
EOF
)"
```

---

### Task 5: `SessionLatestArtifactQuery` + `getLatestArtifact`

**Files:**
- Create: `.../session/query/SessionLatestArtifactQuery.java`
- Modify: `SessionQueryService.java`（合并 `latestArtifact` 重载 → `getLatestArtifact`）
- Modify: `SessionController.java`
- Modify: `SessionQueryServiceTest.java`（`latestArtifact*`）
- Modify: `lippi-ai-ebus-starter/src/test/java/com/xmut/ebus/SessionQueryIntegrationTest.java`

**Interfaces:**
- Consumes: `BaseQuery`；现有 `GenerationRunRepository` + `HistoryQueryService.findById(String,String)`（History Query 改动在 Task 6）
- Produces:
  - `String artifactType()` — 空白 → null
  - `Optional<HistoryArtifactDetailDTO> getLatestArtifact(SessionLatestArtifactQuery query)`

- [ ] **Step 1: Write failing call-site update in unit test**

把 `latestArtifactFetchesSessionWithOnlyXhsArtifact` 改为：

```java
Optional<HistoryArtifactDetailDTO> found = service.getLatestArtifact(
        SessionLatestArtifactQuery.builder().userId(USER).sessionId(SESSION).build());
```

（先改一个测试即可触发编译失败。）

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lippi-ai-ebus-application -am -Dtest=SessionQueryServiceTest#latestArtifactFetchesSessionWithOnlyXhsArtifact test`

Expected: FAIL — `cannot find symbol: getLatestArtifact` / `SessionLatestArtifactQuery`

- [ ] **Step 3: Implement Query + Service + Controller**

```java
package com.xmut.ebus.application.business.session.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class SessionLatestArtifactQuery extends BaseQuery {

    private final String sessionId;
    private final String artifactType;

    public String artifactType() {
        return StringUtils.hasText(artifactType) ? artifactType.trim() : null;
    }
}
```

Service（合并两个重载）：

```java
@Transactional(readOnly = true)
public Optional<HistoryArtifactDetailDTO> getLatestArtifact(SessionLatestArtifactQuery query) {
    String uid = StringUtils.requireHasText(query.getUserId(), "userId required");
    String sid = StringUtils.requireHasText(query.getSessionId(), "sessionId required");
    Instant since = Instant.now(clock).minus(HistoryQueryService.HISTORY_WINDOW_DAYS, ChronoUnit.DAYS);
    Optional<String> artifactId;
    String artifactType = query.artifactType();
    if (artifactType != null) {
        artifactId = generationRunRepository.findLatestSettledArtifactRefBySession(
                uid, sid, since, artifactType);
    } else {
        artifactId = generationRunRepository.findLatestSettledArtifactRefBySession(uid, sid, since);
    }
    if (!artifactId.isPresent()) {
        return Optional.empty();
    }
    try {
        return Optional.of(historyQueryService.findById(uid, artifactId.get()));
    } catch (BusinessException ex) {
        return Optional.empty();
    }
}
```

Controller：

```java
return ApiResponse.success(
        sessionQueryService.getLatestArtifact(SessionLatestArtifactQuery.builder()
                        .userId(SecuritySupport.requireUserId())
                        .sessionId(sessionId)
                        .artifactType(artifactType)
                        .build())
                .orElse(null));
```

更新全部 `latestArtifact*` 单测与 `SessionQueryIntegrationTest` 调用点。

Integration 示例：

```java
HistoryArtifactDetailDTO latest = sessionQueryService.getLatestArtifact(
        SessionLatestArtifactQuery.builder().userId(userId).sessionId(sessionId).build())
        .orElse(null);
```

- [ ] **Step 4: Run tests**

Run:

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=SessionQueryServiceTest test
mvn -pl lippi-ai-ebus-starter -am -Dtest=SessionQueryIntegrationTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application lippi-ai-ebus-interfaces lippi-ai-ebus-starter/src/test/java/com/xmut/ebus/SessionQueryIntegrationTest.java
git commit -m "$(cat <<'EOF'
feat(session): getLatestArtifact takes SessionLatestArtifactQuery

Collapse the latestArtifact overloads into one query-object entrypoint
without changing the HTTP contract.
EOF
)"
```

---

### Task 6: History `*Query` + Service/Controller

**Files:**
- Create: `.../history/query/HistoryListQuery.java`
- Create: `.../history/query/HistoryArtifactQuery.java`
- Modify: `HistoryQueryService.java`
- Modify: `HistoryController.java`
- Modify: `HistoryQueryServiceTest.java`
- Modify: `SessionQueryService.java`（`getLatestArtifact` 内 `findById` 改组 `HistoryArtifactQuery`）
- Modify: `SessionQueryServiceTest.java`（mock `findById` 签名）

**Interfaces:**
- Consumes: `BaseQuery`
- Produces:
  - `List<HistoryArtifactSummaryDTO> list(HistoryListQuery query)`
  - `HistoryArtifactDetailDTO findById(HistoryArtifactQuery query)`
  - `HistoryListQuery.sceneCode()` — 空白 → null

- [ ] **Step 1: Write failing History list test update**

```java
List<HistoryArtifactSummaryDTO> list = service.list(
        HistoryListQuery.builder().userId(USER).sceneCode(null).build());
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -pl lippi-ai-ebus-application -am -Dtest=HistoryQueryServiceTest#listUsesSixtyDayWindowAndHistoryTypes test`

Expected: FAIL — `cannot find symbol: class HistoryListQuery` / 旧签名不匹配

- [ ] **Step 3: Implement History queries + wire**

`HistoryListQuery`:

```java
package com.xmut.ebus.application.business.history.query;

import com.xmut.ebus.common.query.BaseQuery;
import com.xmut.ebus.common.util.StringUtils;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class HistoryListQuery extends BaseQuery {

    private final String sceneCode;

    public String sceneCode() {
        return StringUtils.hasText(sceneCode) ? sceneCode.trim() : null;
    }
}
```

`HistoryArtifactQuery`:

```java
package com.xmut.ebus.application.business.history.query;

import com.xmut.ebus.common.query.BaseQuery;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@EqualsAndHashCode(callSuper = true)
public class HistoryArtifactQuery extends BaseQuery {

    private final String artifactId;
}
```

`HistoryQueryService`:

```java
public List<HistoryArtifactSummaryDTO> list(HistoryListQuery query) {
    String uid = StringUtils.requireHasText(query.getUserId(), "userId required");
    String sceneCode = query.sceneCode();
    // ... unchanged
}

public HistoryArtifactDetailDTO findById(HistoryArtifactQuery query) {
    String uid = StringUtils.requireHasText(query.getUserId(), "userId required");
    String id = StringUtils.requireHasText(query.getArtifactId(), "artifactId required");
    // ... unchanged
}
```

`HistoryController`:

```java
historyQueryService.list(HistoryListQuery.builder()
        .userId(SecuritySupport.requireUserId())
        .sceneCode(sceneCode)
        .build());

historyQueryService.findById(HistoryArtifactQuery.builder()
        .userId(SecuritySupport.requireUserId())
        .artifactId(id)
        .build());
```

`SessionQueryService.getLatestArtifact` 内改为：

```java
return Optional.of(historyQueryService.findById(HistoryArtifactQuery.builder()
        .userId(uid)
        .artifactId(artifactId.get())
        .build()));
```

`SessionQueryServiceTest` mock：

```java
when(historyQueryService.findById(argThat(q ->
        USER.equals(q.getUserId()) && "sku-9".equals(q.getArtifactId()))))
        .thenReturn(expected);
```

或用 `ArgumentMatchers` 自定义；也可用：

```java
when(historyQueryService.findById(any(HistoryArtifactQuery.class))).thenReturn(expected);
verify(historyQueryService).findById(argThat(q ->
        USER.equals(q.getUserId()) && "sku-9".equals(q.getArtifactId())));
```

更新 `HistoryQueryServiceTest` 全部 `list` / `findById` 调用。

- [ ] **Step 4: Run tests**

Run:

```bash
mvn -pl lippi-ai-ebus-application -am \
  -Dtest=HistoryQueryServiceTest,SessionQueryServiceTest test
mvn -pl lippi-ai-ebus-starter -am -Dtest=SessionQueryIntegrationTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application lippi-ai-ebus-interfaces
git commit -m "$(cat <<'EOF'
feat(history): list/findById take History *Query objects

Establish the History query-object pattern and align Session latest-
artifact lookup to the same findById(Query) signature.
EOF
)"
```

---

### Task 7: Docs — `02-be` / `01-coding-style` + spec status

**Files:**
- Modify: `sdd/context/02-be.md`
- Modify: `sdd/context/01-coding-style.md`
- Modify: `docs/superpowers/specs/2026-10-02-query-object-and-domain-projection-naming-design.md`（Status → accepted）

**Interfaces:**
- Produces: 文档约定与实现一致；无运行时代码

- [ ] **Step 1: Update `02-be.md` Command / Query 段**

在 **Command / Query 创建** 后追加 Query 对称段（保留现有 Command 条文）：

```markdown
**Query 创建（读）**

- 一律 `XxxQuery.builder()…build()`（Lombok `@SuperBuilder`，继承 `com.xmut.ebus.common.query.BaseQuery`）。
- **何时必须 `*Query`：** ≥2 个业务参，**或** 带分页 / 过滤；单参可读方法可暂留散参。
- `BaseQuery` 至少 `userId`（JWT sub）；读路径**不强制** `username`。
- Controller 组查询时显式 `.userId(SecuritySupport.requireUserId())…`；禁止 Application / QueryService 读 SecurityContext。
- limit / pagesize 默认值与上限写在对应 Query 的实例方法（如 `limit()` / `turnLimit()`），**不要**堆在 QueryService 静态 `clamp*`。
- QueryService 方法名优先：`list` / `page*` / `find*` / `getLatestArtifact`。
```

在 **Java 勾选** 增加/改写：

```
[ ] Query extends BaseQuery + @SuperBuilder；≥2 参或分页/过滤必须 *Query
[ ] QueryService 操作者从 query.getUserId()；limit clamp 在 Query 实例方法
[ ] domain 只读投影勿用 *DTO 后缀；Application/HTTP 出口用 *DTO
```

把原「Application / QueryService 不直接读 SecurityContext；操作者从 command.getUserId()/getUsername()」改为同时覆盖 query：

```
[ ] Application / QueryService 不直接读 SecurityContext；操作者从 command/query.getUserId()（写路径可加 getUsername()）
```

在 **反模式** 表（若有）补一行：domain 投影叫 `*DTO` → 去后缀（如 `PiMessage`）。

- [ ] **Step 2: Update `01-coding-style.md` 勾选**

把：

```
[ ] 查询类 *PageQuery；勿 Get*Command
```

改为：

```
[ ] 查询类 *PageQuery / *Query extends BaseQuery；≥2 参或分页/过滤必须对象化；勿 Get*Command
[ ] domain 只读投影勿 *DTO；HTTP/Application 出口用 *DTO
```

「优雅写法」第 3 条可改为：`查询 *PageQuery / *Query`。

- [ ] **Step 3: Mark design spec accepted**

`docs/superpowers/specs/2026-10-02-query-object-and-domain-projection-naming-design.md` 顶部：

```markdown
**Status:** accepted
```

- [ ] **Step 4: Final regression**

Run:

```bash
mvn -pl lippi-ai-ebus-starter -am \
  -Dtest=BaseQueryTest,SessionListQueryTest,SessionTurnPageQueryTest,SessionQueryServiceTest,HistoryQueryServiceTest,SessionTurnAssemblerTest,SessionQueryIntegrationTest \
  test
```

Expected: PASS

可选 FE（契约未变）：

```bash
cd lippi-ai-ebus-web && npm test -- --run src/utils/sessionReplay.test.ts
```

- [ ] **Step 5: Commit**

```bash
git add sdd/context/02-be.md sdd/context/01-coding-style.md \
  docs/superpowers/specs/2026-10-02-query-object-and-domain-projection-naming-design.md
git commit -m "$(cat <<'EOF'
docs: codify BaseQuery and domain projection naming rules

Record when *Query is required, where clamp lives, and that domain
projections must not use the *DTO suffix.
EOF
)"
```

---

## Self-review (author)

1. **Spec coverage:** §4 BaseQuery → T1；§7 PiMessage → T2；§5.1–5.3 Session → T3–T5；§5.4–5.5 History → T6；§6 clamp → T3/T4；§8 docs → T7；§5.6 单参不改 → 无任务（刻意）。两段式 SQL / HTTP 外形 → Global Constraints + 无改动任务。
2. **Placeholders:** 无 TBD；每步含具体代码/命令。
3. **Type consistency:** `pageTurns(SessionTurnPageQuery)` / `getLatestArtifact(SessionLatestArtifactQuery)` / `list(HistoryListQuery)` / `findById(HistoryArtifactQuery)` / `PiMessage` 贯穿；T5 仍用旧 `findById(String,String)`，T6 再切 Query。
