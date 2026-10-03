# Generation 成果落库简化 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把计费/草稿生成改成「可投影 view → 宽进写 `ebus_artifact` → settle」；删除选品硬校验落库栈与 Legacy 投影；FE 只渲染 `view`，历史靠 `payload_json.view` 回显。

**Architecture:** `GenerationOutputParser` 只拆 `{view, artifact}`（或纯文本）；`ComputerViewResolver`（Normalize + NoSkillMarkdown，无 Legacy）门禁 view；具体类 `ArtifactPersistPlugin` 写入 `{view, data}`；`persistAs=none` 映射 `artifact_type=chat` 仍落库。Agent 顺序固定为 project → persist → settle。

**Tech Stack:** Java 8 / Spring Boot 2.7（`forma-application` + domain ArtifactStore）、Vue3 / Vitest（`forma-web`）、JUnit 5 + Mockito

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-27-generation-artifact-persist-simplify-design.md`（方案 A）
- Dual-track / search_sku 规约仍有效；本计划**不**改 SKILL 软约束、**保留** ≥1 次成功 `search_sku` 门
- **禁止**新建 `interface ArtifactPersistPlugin`；目标态只有具体类
- **禁止**因 8–12 / `sourceUrl` / disclaimer「非实时」拒绝落库或 settle
- `payload_json` 钉死：`{ "view": <ComputerDocument>, "data": <businessPayload> }`
- 顺序钉死：project → persist → settle；投影失败不写库
- 密钥 / `.env` 不入仓；不改 Spine 全文（仅可选修订记录）
- Java 8：无 `var` / `List.of` / `Map.of`；用显式类型与 `Collections`

## File map

| Path | Responsibility |
|------|----------------|
| `.../domain/.../artifact/model/ArtifactType.java` (+test) | 增 `CHAT("chat")` |
| `APP-META/bootstrap/sql/010_ebus_artifact.sql` | 注释 `picklist \| sku \| chat`（无 DDL 迁移；VARCHAR 已够） |
| `.../agent/support/ParsedGenerationOutput.java` | Parser 输出：`rawView` + `businessPayload` |
| `.../agent/support/GenerationOutputParser.java` (+test) | 薄拆信封；无业务硬校验 |
| `.../agent/support/ArtifactPersistPlugin.java` | **具体类**（替换原 interface）；写库 |
| `.../agent/support/PersistedGenerationArtifact.java` | 收缩为 `artifactRef` + 可选薄 extras（无 items） |
| `.../agent/service/AgentApplicationService.java` (+test) | project → persist → settle；删插件列表 / none 跳过 |
| `.../computer/ComputerViewConfiguration.java` | 链：Normalize → NoSkillMarkdown |
| 删除 §6.1 所列 Picklist* / Legacy* 类型与专用测 | 见 Task 4 |
| `forma-web/.../types/business/agent.ts` | `GenerationArtifactPayload`（view + artifactRef） |
| `.../composables/agent/useAgentPicklistRun.ts` | 只解析 view |
| `.../views/.../EcommerceWorkspacePlaceholder.vue` (+test) | 删 items 旧布局 |
| 双轨 / search_sku / simplify 规约修订记录 | Task 6 |

---

### Task 1: `ArtifactType.CHAT` + `GenerationOutputParser`

**Files:**
- Modify: `forma-domain/src/main/java/com/xmut/ebus/domain/business/artifact/model/ArtifactType.java`
- Modify: `forma-domain/src/test/java/com/xmut/ebus/domain/business/artifact/model/ArtifactTypeTest.java`
- Modify: `APP-META/bootstrap/sql/010_ebus_artifact.sql`（注释一行）
- Create: `forma-application/src/main/java/com/xmut/ebus/application/business/agent/support/ParsedGenerationOutput.java`
- Create: `forma-application/src/main/java/com/xmut/ebus/application/business/agent/support/GenerationOutputParser.java`
- Create: `forma-application/src/test/java/com/xmut/ebus/application/business/agent/support/GenerationOutputParserTest.java`

**Interfaces:**
- Produces: `ArtifactType.CHAT` / `fromCode("chat")`
- Produces: `ParsedGenerationOutput { getRawView(): Map|null, getBusinessPayload(): Map }`
- Produces: `GenerationOutputParser#parse(String finalResponse): ParsedGenerationOutput`（永不因选品字段抛「不合格」；抽不出 JSON 时 `rawView=null`，`businessPayload={text: 原文}`）

- [ ] **Step 1: 写失败测 — ArtifactType.CHAT**

```java
@Test
void codes_include_chat() {
    assertEquals("chat", ArtifactType.CHAT.getCode());
    assertEquals(ArtifactType.CHAT, ArtifactType.fromCode("chat"));
}
```

- [ ] **Step 2: 实现 CHAT + SQL 注释**

`ArtifactType` 增加 `CHAT("chat")`。SQL 注释改为 `'picklist | sku | chat'`。

- [ ] **Step 3: 写失败测 — GenerationOutputParser**

```java
@Test
void dualTrack_extractsViewAndArtifactWithoutValidatingItems() {
    String raw = "{\"view\":{\"version\":1,\"title\":\"t\",\"blocks\":[]},"
            + "\"artifact\":{\"items\":[{\"title\":\"only-one\"}],\"disclaimer\":\"x\"}}";
    ParsedGenerationOutput out = parser.parse(raw);
    assertNotNull(out.getRawView());
    assertEquals(1, out.getRawView().get("version"));
    assertEquals("only-one", ((List<?>) ((Map<?, ?>) out.getBusinessPayload()).get("items")).get(0) /* via cast */);
    // 仅 1 条也不得抛错
}

@Test
void plainText_rawViewNull_payloadHasText() {
    ParsedGenerationOutput out = parser.parse("你好，这是草稿");
    assertNull(out.getRawView());
    assertEquals("你好，这是草稿", out.getBusinessPayload().get("text"));
}

@Test
void fencedJson_supported() {
    ParsedGenerationOutput out = parser.parse("```json\n{\"view\":{\"version\":1,\"blocks\":[]},\"artifact\":{}}\n```");
    assertNotNull(out.getRawView());
    assertTrue(out.getBusinessPayload().isEmpty() || out.getBusinessPayload() != null);
}
```

（实现时用 `ObjectMapper` + 与现 `PicklistArtifactParser` 相同的 fenced 正则；**禁止**拷贝 8–12 / 四维 / `非实时` / `sourceUrl` 校验。）

- [ ] **Step 4: 实现 Parser + DTO**

```java
// ParsedGenerationOutput — final fields, unmodifiable maps
// GenerationOutputParser — @Component, ObjectMapper ctor
// parse 钉死：无 JSON → businessPayload = singletonMap("text", trimmed)；有 artifact 键用其 Map；无 artifact 但有其它业务键 → 整对象去掉 view 后作 data（或整对象，二选一钉在类注释）
```

推荐钉死：**有 `artifact` 键 → data=artifact；否则若根对象无 `view` → data=整根；若仅有 `view` → data=emptyMap；纯文本 → `{text}`。**

- [ ] **Step 5: 跑测**

```bash
mvn -pl forma-domain,forma-application -am -Dtest=ArtifactTypeTest,GenerationOutputParserTest test
```

Expected: PASS

- [ ] **Step 6: Commit**

```bash
git add forma-domain/.../ArtifactType.java \
  forma-domain/.../ArtifactTypeTest.java \
  APP-META/bootstrap/sql/010_ebus_artifact.sql \
  forma-application/.../ParsedGenerationOutput.java \
  forma-application/.../GenerationOutputParser.java \
  forma-application/.../GenerationOutputParserTest.java
git commit -m "$(cat <<'EOF'
feat(agent): add GenerationOutputParser and ArtifactType.CHAT

Thin dual-track parse for history echo; chat type labels no-skill artifacts.
EOF
)"
```

---

### Task 2: 具体类 `ArtifactPersistPlugin`

**Files:**
- Delete content of interface then replace file: `forma-application/src/main/java/com/xmut/ebus/application/business/agent/support/ArtifactPersistPlugin.java`（改为 `@Component` 具体类）
- Modify: `.../PersistedGenerationArtifact.java`（仅 `artifactRef` + `readyExtras`；去掉业务 DTO / rawView 依赖亦可，见 Interfaces）
- Create: `.../test/.../agent/support/ArtifactPersistPluginTest.java`

**Interfaces:**
- Consumes: `ArtifactRepository`, `ObjectMapper`, `Clock`, `ArtifactType`
- Produces:

```java
/**
 * @param persistAs SkillRunProfile.getPersistAs()；none → ArtifactType.CHAT
 */
public PersistedGenerationArtifact persist(String userId,
                                           String runId,
                                           String sceneCode,
                                           String persistAs,
                                           Map<String, Object> projectedView,
                                           Map<String, Object> businessPayload);
```

- `PersistedGenerationArtifact`：`getArtifactRef()` 非空；`getReadyExtras()` 近端可空 map（**禁止**再塞 `items[]`）
- 映射：`none`→`CHAT`，`picklist`→`PICKLIST`，`sku`→`SKU`；未知 → `BusinessException`

- [ ] **Step 1: 写失败测**

```java
@Test
void persist_noneMapsToChat_andStoresViewPlusData() throws Exception {
    // mock ArtifactRepository.save capturing Artifact
    PersistedGenerationArtifact out = plugin.persist(
            "u1", "r1", "ecommerce", SkillRunProfile.PERSIST_NONE,
            markdownViewMap(), Collections.singletonMap("text", "草稿"));
    assertNotNull(out.getArtifactRef());
    ArgumentCaptor<Artifact> cap = ArgumentCaptor.forClass(Artifact.class);
    verify(artifactRepository).save(cap.capture());
    assertEquals(ArtifactType.CHAT, cap.getValue().getType());
    Map<?, ?> payload = objectMapper.readValue(cap.getValue().getPayloadJson(), Map.class);
    assertTrue(payload.containsKey("view"));
    assertEquals("草稿", ((Map<?, ?>) payload.get("data")).get("text"));
}

@Test
void persist_picklistType_noItemCountGate() {
    // businessPayload 只有 1 条 items 也必须 save 成功
    plugin.persist("u1", "r1", "ecommerce", SkillRunProfile.PERSIST_PICKLIST,
            listViewMap(), singletonArtifactWithOneItem());
    verify(artifactRepository).save(any(Artifact.class));
}
```

- [ ] **Step 2: 实现具体类**

替换原 `interface ArtifactPersistPlugin` 为：

```java
@Component
public class ArtifactPersistPlugin {
    public static final String PAYLOAD_VIEW = "view";
    public static final String PAYLOAD_DATA = "data";

    public PersistedGenerationArtifact persist(...) {
        ArtifactType type = resolveType(persistAs); // none → CHAT
        String id = UUID.randomUUID().toString();
        Map<String, Object> payload = new LinkedHashMap<String, Object>();
        payload.put(PAYLOAD_VIEW, projectedView);
        payload.put(PAYLOAD_DATA, businessPayload != null ? businessPayload : Collections.emptyMap());
        String json = objectMapper.writeValueAsString(payload);
        String title = resolveTitle(projectedView); // view.title 或首个 markdown 截断 ≤256
        Artifact artifact = Artifact.create(id, userId, runId, type, sceneCode,
                null, title, json, Instant.now(clock));
        artifactRepository.save(artifact);
        return new PersistedGenerationArtifact(id, Collections.<String, Object>emptyMap());
    }
}
```

同步改 `PersistedGenerationArtifact` 构造：`(String artifactRef, Map readyExtras)`；删除 `artifact` / `rawView` 字段（全仓改编译点在 Task 3）。

若 Task 2 单独编译不过：可暂留 deprecated getter 返回 null，Task 3 删干净——**优先本 Task 直接改签名，并只保证本模块单测编译**（Agent 改动放 Task 3）。

- [ ] **Step 3: 跑测**

```bash
mvn -pl forma-application -am -Dtest=ArtifactPersistPluginTest test
```

Expected: PASS（若 Agent 未改导致 compile 失败，先最小 stub Agent 构造注入具体类，完整行为 Task 3）

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(agent): concrete ArtifactPersistPlugin for echo payload

Persist projected view + data for all settle paths; map none to chat.
EOF
)"
```

---

### Task 3: Agent 管道 — project → persist → settle

**Files:**
- Modify: `forma-application/src/main/java/com/xmut/ebus/application/business/agent/service/AgentApplicationService.java`
- Modify: `forma-application/src/test/java/com/xmut/ebus/application/business/agent/service/AgentApplicationServiceTest.java`
- Modify: `.../computer/ComputerViewConfiguration.java`（可在本 Task 去掉 Legacy 注入，若删类在 Task 4 则先留编译依赖）

**Interfaces:**
- Consumes: `GenerationOutputParser`, `ArtifactPersistPlugin`（单例字段，非 `List`）, `ComputerViewResolver`
- 删除：`List<ArtifactPersistPlugin>`、`requirePersistPlugin`、`persistIfNeeded` 中 `PERSIST_NONE` 早退
- 保留：`profile.isBilledPicklist() && searchSkuOk < 1` → fail

目标 `streamBilledRun` 片段：

```java
ParsedGenerationOutput parsed = generationOutputParser.parse(result.getFinalResponse());
Map<String, Object> projectedView = computerViewResolver.resolve(ViewProjectContext.builder()
        .skillBound(profile.isSkillBound())
        .finalResponse(result.getFinalResponse())
        .rawView(parsed.getRawView())
        .artifact(parsed.getBusinessPayload())
        .build());
PersistedGenerationArtifact persisted = artifactPersistPlugin.persist(
        context.getUserId(), context.getRunId(), context.getSceneCode(),
        profile.getPersistAs(), projectedView, parsed.getBusinessPayload());
creditHoldSupport.settle(...);
markRunSettled(context.getRunId(), persisted.getArtifactRef());
emit artifact_ready(view + artifactRef); // readyExtras 不再含 items
```

- [ ] **Step 1: 改失败测（无 Skill 必须有 artifactRef + 调 persist）**

将 `streamGenerationRunNoSkillSettlesOnUsableMarkdownView` 中：

```java
assertFalse(ready.getData().containsKey("artifactRef"));
verify(picklistApplicationService, never()).persistUsable(any());
```

改为：

```java
assertTrue(StringUtils.hasText((String) ready.getData().get("artifactRef")));
verify(artifactPersistPlugin).persist(eq(USER_ID), eq("run-ns-ok"), eq(ECOM_SCENE_CODE),
        eq(SkillRunProfile.PERSIST_NONE), anyMap(), anyMap());
// 不再注入 PicklistApplicationService
```

新增：`streamGenerationRun_whenViewUnavailable_doesNotPersistOrSettle`（Normalize 失败 / 空 view → never persist、never settle）。

选品成功测：仍要求 `search_sku`≥1；persist 后 settle；**不再** mock `PicklistArtifactParser` 抛「不合格」作为业务硬门（可改为 mock `ComputerViewResolver` 抛 `MSG_VIEW_UNAVAILABLE`）。

- [ ] **Step 2: 跑测确认旧断言失败**

```bash
mvn -pl forma-application -am -Dtest=AgentApplicationServiceTest#streamGenerationRunNoSkillSettlesOnUsableMarkdownView test
```

Expected: FAIL（仍无 artifactRef 或仍 skip persist）

- [ ] **Step 3: 实现 Agent 重排 + 构造注入**

- 字段改为：`GenerationOutputParser generationOutputParser` + `ArtifactPersistPlugin artifactPersistPlugin`
- 删除 `PicklistArtifactParser` import / `MSG_UNUSABLE` 回退文案 → 用 `ComputerViewResolver.MSG_VIEW_UNAVAILABLE` 或写库失败文案
- 测试构造：mock parser（或真实 thin parser）+ mock plugin 返回 `new PersistedGenerationArtifact("art-1", emptyMap())`

- [ ] **Step 4: 全量 Agent 测**

```bash
mvn -pl forma-application -am -Dtest=AgentApplicationServiceTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(agent): project then persist then settle for all profiles

No-skill chat artifacts get artifactRef; drop persistAs=none skip path.
EOF
)"
```

---

### Task 4: 删除 Picklist 硬校验栈 + Legacy 投影

**Files — delete（主源码 + 对应测试）：**
- `.../picklist/support/PicklistArtifactParser.java` + `PicklistParseResult.java` + `PicklistArtifactParserTest.java`
- `.../picklist/support/PicklistViewProjector.java` + `PicklistViewProjectorTest.java`
- `.../picklist/service/PicklistApplicationService.java` + `PicklistApplicationServiceTest.java`
- `.../picklist/dto/PicklistArtifactDTO.java`
- `.../picklist/command/PersistPicklistCommand.java` + `PersistPicklistItemCommand.java`
- `.../agent/support/PicklistArtifactPersistPlugin.java`
- `.../computer/LegacyPicklistFallbackProjector.java`
- 若 `PicklistDefaults` 仅被已删代码引用 → 删除 `domain/.../picklist/constant/PicklistDefaults.java`；若 SKILL/文案仍引用常量类则保留但**禁止**再作门禁

**Files — modify:**
- `ComputerViewConfiguration.java`：只注册 Normalize + NoSkillMarkdown
- `ComputerViewResolverTest.java`：去掉 Legacy 场景断言
- 全仓 `rg`：`PicklistApplicationService|PicklistArtifactParser|LegacyPicklist|PersistPicklist|PicklistArtifactDTO|PicklistArtifactPersistPlugin` → 0 命中（测试夹具字符串除外可清）

- [ ] **Step 1: 改 ComputerViewConfiguration**

```java
return new ComputerViewResolver(Arrays.asList(
        normalizeViewProjector,
        noSkillMarkdownProjector));
```

- [ ] **Step 2: 删除 §6.1 类与专用测；修编译**

- [ ] **Step 3: 跑相关测**

```bash
mvn -pl forma-application -am test
```

Expected: PASS；无缺失符号

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(agent): remove picklist hard-persist stack and Legacy projector

Settle gates on projectable Computer view; history uses generic artifact rows.
EOF
)"
```

---

### Task 5: FE view-only

**Files:**
- Modify: `forma-web/src/types/business/agent.ts`
- Modify: `forma-web/src/composables/agent/useAgentPicklistRun.ts`
- Modify: `forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.vue`
- Modify: `forma-web/src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts`
- Modify: `forma-web/src/views/business/scene/ecommerceWorkspaceSession.css`（删 Legacy pick-list 样式若已无引用）

**Interfaces:**
- Produces:

```ts
export interface GenerationArtifactPayload {
  artifactRef: string
  view: ComputerDocument
}
```

- `useAgentPicklistRun`：`artifact` 改为 `GenerationArtifactPayload | null`；`artifact_ready` **仅** `parseComputerDocument(data.view)` + `artifactRef`；无 view → null
- Workspace：Computer **只** `ComputerRenderer`；删除「有 items 无 view 仍开旧 pick-list DOM」；`canPreviewFromStatus` 认 `view`
- 删除测试：`falls back to old pick-list layout when artifact has items but no view`

- [ ] **Step 1: 改类型 + composable**

```ts
function toGenerationArtifact(data: Record<string, unknown>): GenerationArtifactPayload | null {
  const view = parseComputerDocument(data.view)
  const artifactRef = typeof data.artifactRef === 'string' ? data.artifactRef.trim() : ''
  if (!view || !artifactRef) return null
  return { artifactRef, view }
}
```

（若过渡期后端偶发无 ref，可临时允许仅 view——**以规格为准：成功路径必有 ref**，测里断言有 ref。）

- [ ] **Step 2: Workspace 删 items 分支**

- `picksIsLive` / `computerDoc` 只看 `view`
- 模板去掉 FallbackPicklistCard / `items` v-for
- 测试 SSE fixture 改为只带 `view` + `artifactRef`；删 items-only 用例

- [ ] **Step 3: 跑 FE**

```bash
cd forma-web && npm run test -- --run src/views/business/scene/EcommerceWorkspacePlaceholder.test.ts src/components/business/computer/ComputerRenderer.test.ts
cd forma-web && npm run lint
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(web): Computer view-only artifact_ready; drop items fallback

History echo will read payload_json.view; preview no longer needs items[].
EOF
)"
```

---

### Task 6: 规约修订记录 + 回归

**Files:**
- Modify: `docs/superpowers/specs/2026-09-27-skill-dual-track-computer-contract-design.md`（修订记录：废「无 Skill 无 artifactRef」；settle=可投影 view；落库=回显）
- Modify: `docs/superpowers/specs/2026-09-27-picklist-marketplace-search-skill-design.md`（修订记录：硬校验 sourceUrl/条数不再挡 settle；指向 simplify）
- Modify: `docs/superpowers/specs/2026-09-27-generation-artifact-persist-simplify-design.md`（Status → accepted；勾验收若需）
- Optional: Spine AD-5 一行注释（Ask first 若大段改写——本 Task 仅一行操作化指针）

- [x] **Step 1: 写修订记录（各加一行表格）**

双轨示例文案若仍写「无 Skill：artifact_ready(view) 无 artifactRef」→ 改为「带 artifactRef，`artifact_type=chat`」。

- [x] **Step 2: 后端全量相关测 + 前端 lint**

```bash
mvn -pl forma-starter -am test
cd forma-web && npm run lint && npm run build
```

Expected: PASS

- [x] **Step 3: 验收对照（手工勾选）**

| # | 规格 Acceptance | 验证 |
|---|-----------------|------|
| 1 | 选品成功：行 `picklist` + view + artifactRef + settle | Agent 测 + 可选手跑 |
| 2 | 无 Skill：行 `chat` + markdown view + artifactRef + settle | `streamGenerationRunNoSkillSettlesOnUsableMarkdownView` |
| 3 | 不可投影：不 settle、不写库 | 新增 Agent 测 |
| 4 | §6.1 类不存在；无 none 跳过分支 | `rg` + 读 `persist` 调用点 |
| 5 | FE 仅 ComputerRenderer | Workspace 测 |
| 6 | Parser / Persist 单测 | Task 1–2 |

- [x] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs: align dual-track and search_sku specs with persist simplify A

Echo-first artifact rows; settle on projectable view including no-skill chat.
EOF
)"
```

---

## Spec coverage (self-check)

| Spec 要求 | Task |
|-----------|------|
| GenerationOutputParser | 1 |
| ArtifactPersistPlugin 具体类；无 SPI | 2–3 |
| none→chat 仍落库；payload `{view,data}` | 2–3 |
| project → persist → settle | 3 |
| 删 Picklist* / Legacy | 4 |
| FE view-only | 5 |
| 保留 search_sku 门 | 3（不删） |
| 规约修订 | 6 |
| AD-5 操作化 | 6（文档）；3（代码行为） |

## Out of scope

- 淘宝客真客户端（已有 picklist-search-sku Task 6 deferred）
- History 页完整 API UI（近端仍 Placeholder；载荷形状已为回显预留）
- 按 Skill 的 ViewContractStrategy 收紧
- Spine 全文 course-correction（可选一行指针）
