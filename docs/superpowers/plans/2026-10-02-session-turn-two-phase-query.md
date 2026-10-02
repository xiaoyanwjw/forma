# Session turn two-phase query Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace message-window replay with true two-phase query: page logical runIds by `tipSeq`, then load messages for those runs, return `Page<SessionTurnDTO>`.

**Architecture:** `SessionQueryService` calls `PiSessionQueryRepository.getLogicalRunIds` then `getMessagesByLogicalRunIds`; `PiSessionQueryRepositoryImpl` (renamed from `PiSessionQuerySupport`) runs MySQL `SUBSTRING_INDEX` SQL behind `PiSessionEntryMapper`. Remove `expandOldestRun` / message-seq paging from the replay path.

**Tech Stack:** Java 8 / Spring Boot 2.7 / MyBatis / existing `SessionTurnAssembler` / JUnit 5 + Mockito

**Spec:** `docs/superpowers/specs/2026-10-02-session-turn-two-phase-query-design.md`  
**Supersedes (replay path):** `docs/superpowers/plans/2026-10-02-session-turn-run-clustering.md`（消息窗 + expand 补丁）

## Global Constraints

- Logical runId = `SUBSTRING_INDEX(run_id, ':', 1)`（run 主体不含 `:`）
- 无 `run_id` 不进回放
- ①② 均 `seq > compact_anchor_seq`；不为压缩另做特殊规则
- 对外 API 外形不变：`GET .../messages` → `Page<SessionTurnDTO>`；`nextToken` = 本页最老 tipSeq
- `limit` 回合默认 20、最大 50；ACL 仅本人 session
- FE 已消费 turns；本计划以后端为主，最后跑 FE 回归

## File map

| 文件 | 职责 |
|------|------|
| `lippi-ai-ebus-domain/.../model/PiLogicalRunRef.java` | ① 的 `{logicalRunId, tipSeq}` |
| `lippi-ai-ebus-domain/.../repository/PiSessionQueryRepository.java` | 端口：换掉 `getMessageList` |
| `.../mybatis/mapper/PiSessionEntryMapper.java` + `.xml` | 两段 SQL |
| `.../session/PiSessionQuerySupport.java` → `PiSessionQueryRepositoryImpl.java` | 仓储实现 |
| `.../session/query/SessionQueryService.java` | ①→②→Assembler；删 expand |
| `SessionQueryServiceTest.java` | 单测改 mock 新端口 |
| `SessionQueryIntegrationTest.java`（若有 messages 断言） | 集成对齐 tipSeq 游标 |

---

### Task 1: Domain — `PiLogicalRunRef` + repository 端口

**Files:**
- Create: `lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/agent/model/PiLogicalRunRef.java`
- Modify: `lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/agent/repository/PiSessionQueryRepository.java`
- Test: compile-only this task（行为测在 Task 3/4）

**Interfaces:**
- Produces:
  - `PiLogicalRunRef(String logicalRunId, long tipSeq)` + getters
  - `Page<PiLogicalRunRef> getLogicalRunIds(String sessionId, String nextToken, int limit)`
  - `List<PiMessageDTO> getMessagesByLogicalRunIds(String sessionId, List<String> logicalRunIds)`
- Removes: `Page<PiMessageDTO> getMessageList(...)` from the port（实现类同步删）

- [ ] **Step 1: Add `PiLogicalRunRef`**

```java
package com.xmut.ebus.domain.business.agent.model;

/** 回放分页：逻辑 runId + 可见区内 MAX(seq)。 */
public final class PiLogicalRunRef {
    private final String logicalRunId;
    private final long tipSeq;

    public PiLogicalRunRef(String logicalRunId, long tipSeq) {
        this.logicalRunId = logicalRunId;
        this.tipSeq = tipSeq;
    }

    public String getLogicalRunId() {
        return logicalRunId;
    }

    public long getTipSeq() {
        return tipSeq;
    }
}
```

- [ ] **Step 2: Replace repository methods**

在 `PiSessionQueryRepository` 中删除 `getMessageList`，改为：

```java
import com.xmut.ebus.domain.business.agent.model.PiLogicalRunRef;

/**
 * 按逻辑 run 分页（tipSeq=MAX(seq) DESC）；nextToken 空=最新；非空= tipSeq &lt; token。
 * 仅 seq &gt; compact_anchor；忽略空 run_id。
 */
Page<PiLogicalRunRef> getLogicalRunIds(String sessionId, String nextToken, int limit);

/**
 * 拉取给定逻辑 runId 的全部可见消息（含 :suspend/:resume），seq 升序。
 * logicalRunIds 空 → 空列表。
 */
List<PiMessageDTO> getMessagesByLogicalRunIds(String sessionId, List<String> logicalRunIds);
```

- [ ] **Step 3: Compile domain**

Run: `mvn -pl lippi-ai-ebus-domain -am -DskipTests compile -q`  
Expected: SUCCESS（下游模块本步可不编；Impl 尚未改会在 Task 3 红）

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/agent/model/PiLogicalRunRef.java \
  lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/agent/repository/PiSessionQueryRepository.java
git commit -m "$(cat <<'EOF'
feat(session): add logical-run query port for two-phase replay

EOF
)"
```

---

### Task 2: Mapper SQL — `selectLogicalRunPage` + `selectByLogicalRunIds`

**Files:**
- Modify: `lippi-ai-ebus-infrastructure/src/main/java/com/xmut/ebus/infrastructure/persistence/mybatis/mapper/PiSessionEntryMapper.java`
- Modify: `lippi-ai-ebus-infrastructure/src/main/resources/mybatis/mapper/PiSessionEntryMapper.xml`
- Create (optional PO): map via `@MapKey` 或简单 resultType — 推荐专用小 PO / 或 `PiLogicalRunRef` 若 MyBatis 能直接映射（优先 `Map`/`resultType` 到简单 PO 再转 domain）

**Interfaces:**
- Consumes: compactAnchorSeq、nextToken(Long nullable)、limit
- Produces: mapper 方法供 Impl 调用

- [ ] **Step 1: Add mapper Java methods**

```java
List<PiLogicalRunRow> selectLogicalRunPage(
        @Param("sessionId") String sessionId,
        @Param("compactAnchorSeq") long compactAnchorSeq,
        @Param("nextToken") Long nextToken,
        @Param("limit") int limit);

List<PiSessionEntryPO> selectByLogicalRunIds(
        @Param("sessionId") String sessionId,
        @Param("compactAnchorSeq") long compactAnchorSeq,
        @Param("logicalRunIds") List<String> logicalRunIds);
```

在同包或 `po` 下增加：

```java
public class PiLogicalRunRow {
    private String logicalRunId;
    private long tipSeq;
    // getters/setters
}
```

- [ ] **Step 2: XML — ① 分页**

```xml
<select id="selectLogicalRunPage" resultType="com.xmut.ebus.infrastructure.persistence.mybatis.po.PiLogicalRunRow">
    SELECT logical_run_id AS logicalRunId, tip_seq AS tipSeq
    FROM (
        SELECT
            SUBSTRING_INDEX(run_id, ':', 1) AS logical_run_id,
            MAX(seq) AS tip_seq
        FROM pi_session_entry
        WHERE session_id = #{sessionId}
          AND seq &gt; #{compactAnchorSeq}
          AND run_id IS NOT NULL
          AND run_id &lt;&gt; ''
        GROUP BY SUBSTRING_INDEX(run_id, ':', 1)
    ) t
    <where>
        <if test="nextToken != null">
            tip_seq &lt; #{nextToken}
        </if>
    </where>
    ORDER BY tip_seq DESC
    LIMIT #{limit}
</select>
```

- [ ] **Step 3: XML — ② 按 run 拉消息**

```xml
<select id="selectByLogicalRunIds" resultMap="PiSessionEntryResultMap">
    SELECT id, biz_id, session_id, seq, entry_type, parent_id, run_id, payload, created_at
    FROM pi_session_entry
    WHERE session_id = #{sessionId}
      AND seq &gt; #{compactAnchorSeq}
      AND run_id IS NOT NULL
      AND run_id &lt;&gt; ''
      AND SUBSTRING_INDEX(run_id, ':', 1) IN
      <foreach collection="logicalRunIds" item="rid" open="(" separator="," close=")">
          #{rid}
      </foreach>
    ORDER BY seq ASC
</select>
```

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-infrastructure/src/main/java/com/xmut/ebus/infrastructure/persistence/mybatis/mapper/PiSessionEntryMapper.java \
  lippi-ai-ebus-infrastructure/src/main/resources/mybatis/mapper/PiSessionEntryMapper.xml \
  lippi-ai-ebus-infrastructure/src/main/java/com/xmut/ebus/infrastructure/persistence/mybatis/po/PiLogicalRunRow.java
git commit -m "$(cat <<'EOF'
feat(session): add two-phase logical-run SQL mappers

EOF
)"
```

---

### Task 3: Rename Support → Impl + implement two-phase methods

**Files:**
- Rename: `.../session/PiSessionQuerySupport.java` → `PiSessionQueryRepositoryImpl.java`
- Delete old `getMessageList` body（含 `selectPageDesc` 回放循环）；`selectPageDesc` 若无其它引用可保留 mapper
- Grep 全仓 `PiSessionQuerySupport` 并改引用（通常仅类名 + Spring 扫描）

**Interfaces:**
- Consumes: Task 1 端口、Task 2 mapper
- Produces: 可运行的 `getLogicalRunIds` / `getMessagesByLogicalRunIds`

- [ ] **Step 1: Rename class + update `@Repository` bean**

```java
@Repository
@RequiredArgsConstructor
public class PiSessionQueryRepositoryImpl implements PiSessionQueryRepository {
    // same fields: sessionMapper, entryMapper, objectMapper
}
```

- [ ] **Step 2: Implement `getLogicalRunIds`**

```java
@Override
public Page<PiLogicalRunRef> getLogicalRunIds(String sessionId, String nextToken, int limit) {
    if (!StringUtils.hasText(sessionId) || limit < 1) {
        return Page.empty();
    }
    String sid = sessionId.trim();
    PiSessionPO session = sessionMapper.selectById(sid);
    if (session == null) {
        return Page.empty();
    }
    Long cursor = parseToken(nextToken);
    if (StringUtils.hasText(nextToken) && cursor == null) {
        return Page.empty();
    }
    int need = limit + 1;
    List<PiLogicalRunRow> rows = entryMapper.selectLogicalRunPage(
            sid, session.getCompactAnchorSeq(), cursor, need);
    if (rows == null || rows.isEmpty()) {
        return Page.empty();
    }
    boolean hasMore = rows.size() > limit;
    if (hasMore) {
        rows = new ArrayList<PiLogicalRunRow>(rows.subList(0, limit));
    }
    List<PiLogicalRunRef> items = new ArrayList<PiLogicalRunRef>(rows.size());
    for (PiLogicalRunRow row : rows) {
        if (row == null || !StringUtils.hasText(row.getLogicalRunId())) {
            continue;
        }
        items.add(new PiLogicalRunRef(row.getLogicalRunId().trim(), row.getTipSeq()));
    }
    String token = null;
    if (hasMore && !items.isEmpty()) {
        token = String.valueOf(items.get(items.size() - 1).getTipSeq());
    }
    return Page.of(items, token);
}
```

注意：① SQL 已 `ORDER BY tip_seq DESC`，页内 items[0]=最新；`nextToken` = **本页最老** = list 最后一条 tipSeq（与 spec 例一致）。

- [ ] **Step 3: Implement `getMessagesByLogicalRunIds`**

```java
@Override
public List<PiMessageDTO> getMessagesByLogicalRunIds(String sessionId, List<String> logicalRunIds) {
    if (!StringUtils.hasText(sessionId) || logicalRunIds == null || logicalRunIds.isEmpty()) {
        return Collections.emptyList();
    }
    String sid = sessionId.trim();
    PiSessionPO session = sessionMapper.selectById(sid);
    if (session == null) {
        return Collections.emptyList();
    }
    List<String> ids = new ArrayList<String>();
    for (String id : logicalRunIds) {
        if (StringUtils.hasText(id)) {
            ids.add(id.trim());
        }
    }
    if (ids.isEmpty()) {
        return Collections.emptyList();
    }
    List<PiSessionEntryPO> rows = entryMapper.selectByLogicalRunIds(
            sid, session.getCompactAnchorSeq(), ids);
    if (rows == null || rows.isEmpty()) {
        return Collections.emptyList();
    }
    List<PiMessageDTO> out = new ArrayList<PiMessageDTO>();
    for (PiSessionEntryPO row : rows) {
        PiMessageDTO dto = toReplayMessage(row);
        if (dto != null) {
            out.add(dto);
        }
    }
    return out;
}
```

保留现有 `toReplayMessage` / `parseToken`。

- [ ] **Step 4: Compile infrastructure + application dependents**

Run: `mvn -pl lippi-ai-ebus-infrastructure,lippi-ai-ebus-application -am -DskipTests compile -q`  
Expected: application 可能仍因 Service 调旧方法失败 → 进入 Task 4；至少 Impl 自身编译过。

- [ ] **Step 5: Commit**

```bash
git add -u lippi-ai-ebus-infrastructure/src/main/java/com/xmut/ebus/infrastructure/session/
git add lippi-ai-ebus-infrastructure/src/main/java/com/xmut/ebus/infrastructure/session/PiSessionQueryRepositoryImpl.java
# 确保旧 Support 已删除
git commit -m "$(cat <<'EOF'
refactor(session): PiSessionQueryRepositoryImpl two-phase run queries

EOF
)"
```

---

### Task 4: `SessionQueryService` 改为 ①→②→Assembler

**Files:**
- Modify: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/session/query/SessionQueryService.java`
- Modify: `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/session/query/SessionQueryServiceTest.java`

**Interfaces:**
- Consumes: `getLogicalRunIds` / `getMessagesByLogicalRunIds` / `SessionTurnAssembler`
- Produces: `Page<SessionTurnDTO>`（页内 tipSeq 升序）

- [ ] **Step 1: Rewrite failing tests first（TDD）**

替换对 `getMessageList` 的 mock：

```java
@Test
void getMessageListReturnsTurnPageWithNextToken() {
    when(piSessionQueryRepository.findBySessionId(SESSION))
            .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
    when(piSessionQueryRepository.getLogicalRunIds(eq(SESSION), isNull(), eq(20)))
            .thenReturn(Page.of(Collections.singletonList(new PiLogicalRunRef("run-a", 6L)), "6"));
    Instant t1 = NOW.minus(3, ChronoUnit.MINUTES);
    String dump = "```json\n{\"view\":{\"version\":1,\"blocks\":[]}}\n```";
    when(piSessionQueryRepository.getMessagesByLogicalRunIds(eq(SESSION), eq(Collections.singletonList("run-a"))))
            .thenReturn(Arrays.asList(
                    new PiMessageDTO("user", "你好", t1, 3L, null, Collections.<PiToolCallRef>emptyList(), "run-a"),
                    new PiMessageDTO("assistant", dump, t1, 6L, null, Collections.<PiToolCallRef>emptyList(), "run-a")));

    Page<SessionTurnDTO> page = service.getMessageList(USER, SESSION, null, null);

    assertEquals("6", page.getNextToken());
    assertEquals(1, page.getItems().size());
    assertEquals("run-a", page.getItems().get(0).getRunId());
    assertEquals("你好", page.getItems().get(0).getUserPrompt());
}

@Test
void getMessageListClustersListingSuspendResumeIntoOneTurn() {
    when(piSessionQueryRepository.findBySessionId(SESSION))
            .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
    when(piSessionQueryRepository.getLogicalRunIds(eq(SESSION), isNull(), eq(20)))
            .thenReturn(Page.of(Collections.singletonList(new PiLogicalRunRef("list-9", 4L)), null));
    Instant t = NOW.minus(1, ChronoUnit.MINUTES);
    when(piSessionQueryRepository.getMessagesByLogicalRunIds(eq(SESSION), eq(Collections.singletonList("list-9"))))
            .thenReturn(Arrays.asList(
                    msg("user", "请生成上架素材", 1L, t, "list-9:suspend", null, null),
                    msg("assistant", "{\"output\":\"plan/final.json\"}", 2L, t, "list-9:suspend", null, null),
                    msg("user", "{\"optionId\":\"confirm_execute\"}", 3L, t, "list-9:resume", null, null),
                    msg("assistant", "{\"output\":\"exec/final.json\"}", 4L, t, "list-9:resume", null, null)));

    Page<SessionTurnDTO> page = service.getMessageList(USER, SESSION, null, null);

    assertNull(page.getNextToken());
    assertEquals(1, page.getItems().size());
    assertEquals("list-9", page.getItems().get(0).getRunId());
    assertEquals(4, page.getItems().get(0).getMessages().size());
}

@Test
void getMessageListPassesNextTokenAndClampsLimit() {
    when(piSessionQueryRepository.findBySessionId(SESSION))
            .thenReturn(Optional.of(meta(SESSION, USER, "ecommerce", NOW)));
    when(piSessionQueryRepository.getLogicalRunIds(eq(SESSION), eq("10"), eq(50)))
            .thenReturn(Page.<PiLogicalRunRef>empty());

    Page<SessionTurnDTO> page = service.getMessageList(USER, SESSION, "10", 500);

    assertTrue(page.getItems().isEmpty());
    verify(piSessionQueryRepository).getLogicalRunIds(SESSION, "10", 50);
    verify(piSessionQueryRepository, never()).getMessagesByLogicalRunIds(anyString(), anyList());
}
```

`FORBIDDEN` 用例改为 `verify(..., never()).getLogicalRunIds(...)`。

- [ ] **Step 2: Run tests — expect fail / compile error**

Run: `mvn -pl lippi-ai-ebus-application -am -DfailIfNoTests=false -Dtest=SessionQueryServiceTest test -q`  
Expected: FAIL（Service 仍调旧 API 或未实现）

- [ ] **Step 3: Rewrite `getMessageList`**

删除 `ExpandResult` / `expandOldestRun` / `sliceNewestTurns` / `MESSAGE_PAGE_*`（若不再使用）。保留 `TURN_PAGE_*`、`clampTurnPage`、`keepReplay`（② 已过滤时可对 messages 再 `keepReplay` 防御）。

```java
@Transactional(readOnly = true)
public Page<SessionTurnDTO> getMessageList(String userId, String sessionId, String nextToken, Integer limit) {
    String uid = StringUtils.requireHasText(userId, "userId required");
    String sid = StringUtils.requireHasText(sessionId, "sessionId required");

    PiSessionMeta row = piSessionQueryRepository.findBySessionId(sid)
            .orElseThrow(() -> new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE));
    if (!uid.equals(row.getUserId())) {
        throw new BusinessException(ErrorCode.FORBIDDEN, MSG_UNAVAILABLE);
    }

    int turnLimit = clampTurnPage(limit);
    Page<PiLogicalRunRef> runPage = piSessionQueryRepository.getLogicalRunIds(sid, nextToken, turnLimit);
    if (runPage == null || runPage.getItems() == null || runPage.getItems().isEmpty()) {
        return Page.empty();
    }
    List<PiLogicalRunRef> newestFirst = runPage.getItems();
    List<String> runIds = new ArrayList<String>(newestFirst.size());
    for (PiLogicalRunRef ref : newestFirst) {
        runIds.add(ref.getLogicalRunId());
    }
    List<PiMessageDTO> messages = piSessionQueryRepository.getMessagesByLogicalRunIds(sid, runIds);
    List<SessionTurnDTO> assembled = SessionTurnAssembler.assemble(messages);
    // 页内升序：与 tipSeq 升序一致（newestFirst 为 DESC，反转顺序对齐）
    List<SessionTurnDTO> ascending = orderTurnsByRunTipOrder(assembled, newestFirst);
    return Page.of(ascending, runPage.getNextToken());
}

/** 按 ① 返回的 tipSeq 升序排放 turns（聊天新在后）。 */
private static List<SessionTurnDTO> orderTurnsByRunTipOrder(
        List<SessionTurnDTO> assembled, List<PiLogicalRunRef> newestFirst) {
    Map<String, SessionTurnDTO> byRun = new HashMap<String, SessionTurnDTO>();
    for (SessionTurnDTO turn : assembled) {
        if (turn != null && StringUtils.hasText(turn.getRunId())) {
            byRun.put(turn.getRunId(), turn);
        }
    }
    List<SessionTurnDTO> out = new ArrayList<SessionTurnDTO>(newestFirst.size());
    for (int i = newestFirst.size() - 1; i >= 0; i--) {
        SessionTurnDTO turn = byRun.get(newestFirst.get(i).getLogicalRunId());
        if (turn != null) {
            out.add(turn);
        }
    }
    return out;
}
```

- [ ] **Step 4: Run unit tests**

Run: `mvn -pl lippi-ai-ebus-application -am -DfailIfNoTests=false -Dtest=SessionQueryServiceTest,SessionTurnAssemblerTest test -q`  
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/session/query/SessionQueryService.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/session/query/SessionQueryServiceTest.java
git commit -m "$(cat <<'EOF'
feat(session): wire SessionQueryService to two-phase run paging

EOF
)"
```

---

### Task 5: Integration + FE 回归

**Files:**
- Modify if needed: `lippi-ai-ebus-starter/src/test/java/com/xmut/ebus/SessionQueryIntegrationTest.java`（messages 断言 / nextToken）
- No FE code change expected

- [x] **Step 1: Grep 残留**

Run: `rg "getMessageList\\(|PiSessionQuerySupport|expandOldestRun|MESSAGE_PAGE_" --glob '*.{java,xml,ts,vue}'`  
Expected: Service 方法名 `getMessageList`（对外用例名）可保留；端口旧签名与 Support 类名应消失。

- [x] **Step 2: 后端集成 / starter 测试**

Run: `mvn -pl lippi-ai-ebus-starter -am -DfailIfNoTests=false -Dtest=SessionQueryIntegrationTest,SessionQueryServiceTest,SessionTurnAssemblerTest test -q`  
Expected: PASS（按需改集成夹具：消息必须带 `run_id`，否则回放为空）

- [x] **Step 3: FE 回归**

Run: `cd lippi-ai-ebus-web && npx vitest run src/utils/sessionReplay.test.ts src/views/business/history/HistoryPlaceholder.test.ts src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts src/views/business/scene/XiaohongshuWorkspace.test.ts`  
Expected: PASS

- [x] **Step 4: Mark design accepted + supersede old plan**

在 spec 顶部：`Status: accepted`。  
在旧 plan 顶部加一行：`> Superseded by docs/superpowers/plans/2026-10-02-session-turn-two-phase-query.md`

- [x] **Step 5: Commit**

```bash
git add docs/superpowers/specs/2026-10-02-session-turn-two-phase-query-design.md \
  docs/superpowers/plans/2026-10-02-session-turn-run-clustering.md \
  docs/superpowers/plans/2026-10-02-session-turn-two-phase-query.md
git commit -m "$(cat <<'EOF'
docs(session): accept two-phase run query spec and plan

EOF
)"
```

---

## Spec coverage checklist

| Spec 项 | Task |
|---------|------|
| `getLogicalRunIds` + tipSeq 分页 | 1, 2, 3 |
| `getMessagesByLogicalRunIds` | 1, 2, 3 |
| `SUBSTRING_INDEX` | 2 |
| `seq > compact_anchor` | 2, 3 |
| 无 run_id 忽略 | 2 SQL |
| Service ①→②→Assembler，删 expand | 4 |
| Rename Support → Impl | 3 |
| API 外形 / FE turns | 4–5（FE 已就绪，回归） |
| 测试：suspend/resume、分页、FORBIDDEN | 4, 5 |
| 页内 tipSeq 升序 | 4 `orderTurnsByRunTipOrder` |
