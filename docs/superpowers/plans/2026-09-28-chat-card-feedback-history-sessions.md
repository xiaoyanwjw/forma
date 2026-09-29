# 卡片反馈 + 历史抽屉 Tab + 侧栏会话切换 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 成功 STATUS 卡下提供重试/赞/踩（踩开抽屉、Feedback upsert）；历史页抽屉用 Tab 切换对话(R1)与成果；工作台侧栏可列出并整页切回本人近 60 天会话。

**Architecture:** Feedback 扩展「质量好」+ `(user,artifact)` upsert；新建 SessionQuery（显式 userId ACL）读 `pi_session`/`pi_session_entry`；History 详情补 `sessionId`；前端移除 Computer 结果条，卡片下操作 + 历史抽屉 Tab + 侧栏切会话（R1 气泡，不回放 STATUS 过程卡）。

**Tech Stack:** Java 8 / Spring Boot 2.7（`lippi-ai-ebus-*`）、Vue3 / Vitest（`lippi-ai-ebus-web`）、MySQL（`ebus_feedback` / `pi_session*` / `ebus_generation_run` / `ebus_artifact`）

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-28-chat-card-feedback-actions-design.md`（accepted）
- 回放 = **R1**：user/assistant 文本气泡；**不做** STATUS 过程卡笔录
- Feedback 标签仅 `质量好` | `质量差`；零 CreditLedger；可改口 **upsert**
- Session 列表/消息：**application 显式传 userId**；`user_id IS NULL` 的会话 **不进入** 本 API 列表（防漏数）
- 历史抽屉默认 Tab：**对话**；点 agent 气泡切 **成果**；Tab 名：**对话** | **成果**
- 消息过滤：**保留** `user`/`assistant` 且 content 非空；**丢弃** `system`、tool、空 content
- 重试语义不变：新 GenerationRun + 新预占；可复用 sessionId
- 不改 3.5/3.7；不做历史页反馈入口；不做分页/物理清理

## Spec open → decisions

| Open | Decision |
|------|----------|
| `pi_session.user_id` 可空历史行 | 列表/详情 **仅** `user_id = 当前用户`；null 不展示 |
| 会话标题 | 取首条非空 user content，截断 40 字；否则 `电商会话` |
| 会话最近成果 | `ebus_generation_run` 按 `session_id`+本人，`artifact_ref` 非空，按 `created_at` 降序取第一条，且 artifact 为 picklist/sku |
| 创建会话时 | 确保 `prepareGenerationRun` / session 行写入 `user_id`（若已有则保持） |

## File map

| Path | Responsibility |
|------|----------------|
| `APP-META/bootstrap/sql/014_ebus_feedback_upsert.sql` + H2 | UNIQUE(user,artifact) + `updated_at` |
| `Feedback` / `FeedbackRepository` / Mapper | `findByUserAndArtifact`；save upsert |
| `FeedbackApplicationService` | 双标签 + upsert + `findByArtifact` |
| `FeedbackController` | GET by artifactId |
| `SessionQueryService` + Controller | 列表 / 消息 |
| `PiSessionMapper` | `selectRecentByUserSince` |
| `GenerationRunRepository` | `findLatestWithArtifactBySession` |
| `HistoryArtifactDetailDTO` + Query | 补 `sessionId` |
| `EcommerceWorkspacePlaceholder.vue` | 卡下操作、踩抽屉、侧栏、切会话；删 Computer result-actions |
| `HistoryPlaceholder.vue` | 抽屉 + Tab 对话/成果 |
| FE `api`/`types` sessions + feedback | HTTP 客户端 |

---

### Task 1: Feedback upsert +「质量好」+ GET by artifact

**Files:**
- Create: `APP-META/bootstrap/sql/014_ebus_feedback_upsert.sql`
- Modify: `lippi-ai-ebus-starter/src/test/resources/schema-h2.sql`
- Modify: `lippi-ai-ebus-domain/.../feedback/model/Feedback.java`（`updatedAt`；可选 `touch`）
- Modify: `.../feedback/repository/FeedbackRepository.java`
- Modify: `FeedbackPO` / `FeedbackMapper.java` / `FeedbackMapper.xml` / `FeedbackRepositoryImpl`
- Modify: `FeedbackApplicationService.java` + `FeedbackApplicationServiceTest.java`
- Modify: `FeedbackController.java` + `FeedbackHistoryIntegrationTest.java`
- Modify: `lippi-ai-ebus-web/src/types/business/feedback.ts`（`FEEDBACK_TAG_GOOD_QUALITY = '质量好'`）

**Interfaces:**
- Produces: `FeedbackRepository.findByUserAndArtifact(userId, artifactId): Optional<Feedback>`
- Produces: `FeedbackApplicationService.TAG_GOOD_QUALITY = "质量好"`；`submit` upsert；`findByArtifact(userId, artifactId): Optional<FeedbackDTO>`
- Produces: `GET /api/v1/feedbacks?artifactId=` → `Result` data 可为 null

- [ ] **Step 1: 写失败测（upsert / 质量好）**

```java
@Test
void submitGoodQualityUpsertsSameArtifact() {
    when(artifactRepository.findById(ARTIFACT_ID)).thenReturn(Optional.of(ownedPicklist()));
    when(feedbackRepository.findByUserAndArtifact(USER, ARTIFACT_ID)).thenReturn(Optional.empty());
    service.submit(cmd(USER, ARTIFACT_ID, "质量好", null));
    verify(feedbackRepository).save(argThat(f -> "质量好".equals(f.getTag())));

    Feedback existing = Feedback.create("fb1", USER, ARTIFACT_ID, "质量好", null, Instant.parse("2026-09-01T00:00:00Z"));
    when(feedbackRepository.findByUserAndArtifact(USER, ARTIFACT_ID)).thenReturn(Optional.of(existing));
    service.submit(cmd(USER, ARTIFACT_ID, "质量差", "偏水"));
    verify(feedbackRepository, atLeastOnce()).save(argThat(f ->
            "fb1".equals(f.getId()) && "质量差".equals(f.getTag()) && "偏水".equals(f.getCommentText())));
}
```

- [ ] **Step 2: Run test — expect FAIL**（尚无 upsert / 质量好）

Run: `mvn -pl lippi-ai-ebus-starter -am -DfailIfNoTests=false -Dtest=FeedbackApplicationServiceTest#submitGoodQualityUpsertsSameArtifact test`

- [ ] **Step 3: SQL + domain/repo**

`014_ebus_feedback_upsert.sql`:

```sql
ALTER TABLE ebus_feedback
    ADD COLUMN updated_at DATETIME(3) NULL COMMENT '更新时间' AFTER created_at;
UPDATE ebus_feedback SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE ebus_feedback
    MODIFY updated_at DATETIME(3) NOT NULL COMMENT '更新时间';
ALTER TABLE ebus_feedback
    ADD UNIQUE KEY uk_ebus_feedback_user_artifact (user_id, artifact_id);
```

H2：表定义加 `updated_at` + `UNIQUE (user_id, artifact_id)`。  
Mapper：`selectByUserAndArtifact`；`insert`/`updateByBizId`（或 save 分支）。

- [ ] **Step 4: Service — 允许两标签；有则改 tag/comment/updatedAt 再 save**

```java
public static final String TAG_GOOD_QUALITY = "质量好";
// allow TAG_GOOD_QUALITY || TAG_POOR_QUALITY only
Optional<Feedback> existing = feedbackRepository.findByUserAndArtifact(userId, artifact.getId());
if (existing.isPresent()) {
    Feedback f = existing.get();
    f.setTag(tag.trim());
    f.setCommentText(comment); // 赞时可清短文
    f.setUpdatedAt(now);
    feedbackRepository.save(f);
    return toDto(f);
}
```

- [ ] **Step 5: Controller GET + 集成测**（同 artifact 先赞后踩只一行；GET 可读）

- [ ] **Step 6: Commit**

```bash
git add APP-META/bootstrap/sql/014_ebus_feedback_upsert.sql \
  lippi-ai-ebus-starter/src/test/resources/schema-h2.sql \
  lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/feedback \
  lippi-ai-ebus-infrastructure/src/main/java/com/xmut/ebus/infrastructure/persistence/**/feedback* \
  lippi-ai-ebus-infrastructure/src/main/resources/mybatis/mapper/FeedbackMapper.xml \
  lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/feedback \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/feedback \
  lippi-ai-ebus-interfaces/src/main/java/com/xmut/ebus/interfaces/**/feedback \
  lippi-ai-ebus-starter/src/test/java/com/xmut/ebus/FeedbackHistoryIntegrationTest.java \
  lippi-ai-ebus-web/src/types/business/feedback.ts
git commit -m "feat(feedback): upsert good/poor quality by artifact"
```

---

### Task 2: FE — 卡下重试/赞/踩 + 踩抽屉；移除 Computer 条

**Files:**
- Modify: `lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue`
- Modify: `EcommerceWorkspacePlaceholder.test.ts`
- Modify: `lippi-ai-ebus-web/src/api/business/feedback/feedback.ts`（`getFeedbackByArtifact`）

**Interfaces:**
- Consumes: `submitFeedback`；`FEEDBACK_TAG_*`；现有 `oneClickRetry`
- Produces: 成功 STATUS 卡下 `data-testid="card-result-actions"`；踩抽屉 `data-testid="dislike-drawer"`

- [ ] **Step 1: 写失败测**

```ts
it('shows retry/like/dislike under success STATUS card, not on Computer bar', async () => {
  // …跑通 picklist 成功…
  expect(root.querySelector('[data-testid="result-actions"]')).toBeNull()
  expect(root.querySelector('[data-testid="card-result-actions"]')).toBeTruthy()
})

it('like posts 质量好 without drawer; dislike opens drawer then posts 质量差', async () => {
  // like → feedback body tag 质量好；无 drawer
  // dislike → drawer visible → confirm → tag 质量差
})
```

- [ ] **Step 2: Run vitest — expect FAIL**

Run: `cd lippi-ai-ebus-web && npx vitest run src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts -t "card-result-actions|like posts|dislike opens"`

- [ ] **Step 3: 实现 UI**

- 删除 Computer 内 `result-actions` 整块。  
- 在成功 STATUS 消息（`m.role==='status'` 或现有成功终态卡）模板下方渲染操作条。  
- 赞：直接 `submitFeedback({ artifactId, tag: 质量好 })`；高亮；可选 `getFeedbackByArtifact` 恢复。  
- 踩：打开底部抽屉（textarea + 取消/提交）；提交 `质量差`。  
- 重试：调用现有 `oneClickRetry`。  

- [ ] **Step 4: 测绿 + Commit**

```bash
git add lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue \
  lippi-ai-ebus-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts \
  lippi-ai-ebus-web/src/api/business/feedback/feedback.ts \
  lippi-ai-ebus-web/src/types/business/feedback.ts
git commit -m "feat(web): move retry/like/dislike under success chat card"
```

---

### Task 3: SessionQuery — 列表 + 消息（userId ACL）

**Files:**
- Modify: `PiSessionMapper.java` / XML — `selectByUserSince(userId, since, sceneCode, limit)`
- Modify: `MysqlSessionStore` **或** 新建 `PiSessionQuerySupport`（推荐独立，避免污染 SessionStore 写端口）读 PO
- Create: `.../application/business/session/query/SessionQueryService.java`
- Create: DTOs `SessionSummaryDTO`, `SessionMessageDTO`
- Create: `.../interfaces/web/business/session/SessionController.java`
- Create: `SessionQueryServiceTest.java` + 可选 `SessionQueryIntegrationTest.java`
- Ensure: session 创建路径写 `user_id`（查 `PiSessionSceneRepository` / `ensureSessionRow`；补 `userId` 参数）

**Interfaces:**
- Produces: `GET /api/v1/sessions?sceneCode=&limit=` → `List<SessionSummaryDTO>`  
  fields: `sessionId`, `title`, `sceneCode`, `updatedAt`
- Produces: `GET /api/v1/sessions/{sessionId}/messages` → `List<SessionMessageDTO>`  
  fields: `role` (`user`|`assistant`), `content`, `createdAt`（可选）
- Produces: `SessionQueryService.list(userId, sceneCodeOrNull, limit)`；`listMessages(userId, sessionId)`

**消息映射（钉死）：**

```java
// after SessionStore.load(sessionId) or entry decode:
// keep if role is user|assistant (ignore case) AND StringUtils.hasText(content)
// skip system / tool / empty
```

- [ ] **Step 1: 失败测 — ACL 与过滤**

```java
@Test
void listOnlyReturnsCurrentUsersSessions() { /* mock mapper */ }

@Test
void listMessagesSkipsSystemAndEmpty() { /* … */ }

@Test
void listMessagesForbiddenForOtherUser() { /* … */ }
```

- [ ] **Step 2: Run — expect FAIL**

- [ ] **Step 3: Mapper + Service + Controller**

- `selectByUserSince`: `WHERE user_id = #{userId} AND updated_at >= #{since}` 可选 scene；`ORDER BY updated_at DESC LIMIT #{limit}`（默认 50，max 100）。  
- `listMessages`：先读 session 行校验 `user_id`；再 `sessionStore.load`；映射过滤。  
- 标题：扫描 messages 首条 user content，`substring(0, 40)`。  

- [ ] **Step 4: 测绿 + Commit**

```bash
git commit -m "feat(session): SessionQuery list and messages with user ACL"
```

---

### Task 4: History 详情补 sessionId + 最近成果 by session

**Files:**
- Modify: `HistoryArtifactDetailDTO` — 加 `sessionId`（可 null）
- Modify: `HistoryQueryService.findById` — `artifact.getRunId()` → `generationRunRepository.findById` → `getSessionId()`
- Modify: `GenerationRunRepository` + Mapper — `findLatestSettledArtifactRefBySession(userId, sessionId)`
- Modify: `HistoryQueryServiceTest` / `FeedbackHistoryIntegrationTest`
- Optional: `GET /api/v1/sessions/{id}/latest-artifact` **或** 侧栏用 History/Artifact 查询；本任务在 SessionQueryService 增加 `findLatestUsableArtifact(userId, sessionId): Optional<HistoryArtifactDetailDTO>` 复用 resign

**Interfaces:**
- Produces: detail JSON 含 `sessionId`
- Produces: `SessionQueryService.latestArtifact(userId, sessionId)` → 详情 DTO 或 null

- [ ] **Step 1–4: TDD 补字段与 latest artifact；Commit**

```bash
git commit -m "feat(history): attach sessionId and latest artifact by session"
```

---

### Task 5: FE — 历史抽屉 Tab「对话｜成果」

**Files:**
- Modify: `HistoryPlaceholder.vue` + `HistoryPlaceholder.test.ts`
- Create/Modify: `lippi-ai-ebus-web/src/api/business/session/session.ts` + types
- Reuse: `ComputerRenderer` 于「成果」Tab

**Interfaces:**
- Consumes: `getHistoryArtifact`；`getSessionMessages(sessionId)`

- [ ] **Step 1: 失败测**

```ts
it('opens drawer with tabs 成果 default and 对话 messages', async () => {
  // click item → drawer
  // default tab 成果 shows Computer / title
  // click 对话 → fetch messages URL；empty state if no sessionId
})
```

- [ ] **Step 2: 实现抽屉**

- 列表点击：打开抽屉（右侧 slide-over 即可），不关列表。  
- Tabs：`成果`（默认）｜`对话`。  
- 成果：现有 detail view。  
- 对话：若无 `sessionId` 显示「暂无会话记录」；否则拉 messages 渲染气泡。  

- [ ] **Step 3: 测绿 + Commit**

```bash
git commit -m "feat(web): history drawer tabs for chat replay and artifact"
```

---

### Task 6: FE — 工作台侧栏会话列表与整页切换

**Files:**
- Modify: `EcommerceWorkspacePlaceholder.vue` + tests
- Modify: session API client

**Interfaces:**
- Consumes: `listSessions`；`getSessionMessages`；`latestArtifact`（或 history detail）
- Produces: 侧栏 `data-testid="session-list"`；项 `data-testid="session-item"`

- [ ] **Step 1: 失败测**

```ts
it('lists sessions in sidebar and switches workspace on click', async () => {
  // mock GET /api/v1/sessions → two items
  // click second → messages loaded；sessionId bound；Computer shows artifact if any
})
```

- [ ] **Step 2: 实现**

- `onMounted` / 登录后 `loadSessions()`。  
- 点选：abort 当前 SSE UI 态；`sessionId` 写入 picklist+listing composable；`messages` 换成 R1 气泡（可映射为 `{role,text}`，无 processEvents）；加载 latest artifact → Computer。  
- 「新任务」：清空 messages/computer，新 session（现有 `newTask`），刷新侧栏。  
- 当前项 `.on` 高亮。  

- [ ] **Step 3: 测绿 + Commit**

```bash
git commit -m "feat(web): workspace sidebar session list and switch"
```

---

### Task 7: 端到端回归与文档

**Files:**
- Modify: `docs/superpowers/specs/2026-09-28-chat-card-feedback-actions-design.md` Implementation 注记（可选）
- Append deferred：R2 UI 笔录（若尚未记）

- [ ] **Step 1: 跑验证**

```bash
mvn -pl lippi-ai-ebus-starter -am test
cd lippi-ai-ebus-web && npm run lint && npm run build
npx vitest run src/views/business/history/HistoryPlaceholder.test.ts \
  src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts
```

Expected: BUILD SUCCESS；相关 vitest 绿（已知 Listing 演示旧断言若仍红，注明非本计划引入）

- [ ] **Step 2: 手工冒烟清单**

1. 成功卡下赞/踩/重试  
2. 历史抽屉 Tab  
3. 侧栏切会话后继续发送  

- [ ] **Step 3: Commit chore/docs if needed**

---

## Self-review (plan author)

| Spec requirement | Task |
|------------------|------|
| 卡下重试/赞/踩 + 踩抽屉 | T2 |
| Feedback 质量好/差 upsert | T1 |
| Computer 条删除 | T2 |
| 历史抽屉 Tab 对话/成果 | T5 |
| R1 消息 | T3 + T5/T6 |
| 侧栏列表 + 整页切换 | T6 |
| sessionId on history | T4 |
| userId ACL / null user 不展示 | T3 |
| 零积分 | T1（无 Credit 调用） |

Placeholder scan: none intentional. Types: `SessionSummaryDTO` / `SessionMessageDTO` / Feedback tags consistent across tasks.
