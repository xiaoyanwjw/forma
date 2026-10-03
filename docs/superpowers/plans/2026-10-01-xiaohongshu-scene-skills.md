# 小红书种草三技能 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `xiaohongshu` 场景亮卡可用：选题（厚管线 `search_xhs_note`）、笔记种草稿、爆文拆解（链接 `fetch_xhs_note`）三条计费技能跑通写盘结算。

**Architecture:** 对标电商选品。`search_xhs_note` → `XhsNoteSearcher`（expand→search→check→pool→rerank→topHits），Apify 只做 `XhsNoteSearchPort`。`fetch_xhs_note` 为单篇详情 Port，不套召回管线。三个 `SKILL.md` 只调工具一次（或笔记零检索）后写 `final.json` 指针。`SkillRunProfile` / `ArtifactType` 扩展三种 `persistAs`；场景装包校验三 skill；Catalog 将 `xiaohongshu` 置 `AVAILABLE`；前端新工作台三胶囊。

**Tech Stack:** Java 8 / Spring Boot 2.7、`forma-application`、`pi-agent` skills classpath、`pi-ai` rerank、Vue3 工作台、JUnit 5 + Mockito

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-01-xiaohongshu-scene-skills-design.md`（accepted）
- 厚管线方法名钉死：`search` / `expandQuery` / `doSearch` / `doCheck` / `pooling` / `rerank` / `topHits`（对标 `SkuSearcher`）
- Demo：`expandQuery` 不扩词；`doSearch` 单路；CI 打桩 Port + Reranker，不打真 Apify / 真网模型
- 选题工具失败 → skill 可 `source=model_fallback`；爆文无原文 → Fail（无兜底编造）
- 字段名 `source`（非 `sourceMode`）
- 默认 Actor：搜 `opspilot.cc/xiaohongshu-keyword-search-scraper`；详情 `khadinakbar/xiaohongshu-note-detail-scraper`
- Java 8：无 `var` / `List.of`；配置前缀 `ebus.xhs-note-search.*` / `ebus.xhs-note-fetch.*`
- Skill 路径：`scenes/xiaohongshu/{skillId}/SKILL.md`；starter + application/pi-agent test resources 镜像同步

## Naming

| 角色 | 名字 |
|------|------|
| 搜索 Port | `XhsNoteSearchPort` |
| 搜索命中 | `XhsNoteSearchHit` |
| 管线入口 | `XhsNoteSearcher` |
| 内部候选 | `XhsNoteCandidate` |
| 重排 | `XhsNoteReranker` + `ModelXhsNoteReranker` + `identity()` |
| 搜索 Handler | `SearchXhsNoteToolHandler`（tool name `search_xhs_note`） |
| 详情 Port | `XhsNoteFetchPort` |
| 详情 Handler | `FetchXhsNoteToolHandler`（tool name `fetch_xhs_note`） |
| Skills | `xhs-topiclist` / `xhs-note` / `xhs-break` |
| persistAs | `xhs_topiclist` / `xhs_note` / `xhs_break` |

## File map

| Path | Responsibility |
|------|----------------|
| `.../tool/xhs/XhsNoteSearchHit.java` | 回传/候选字段 |
| `.../tool/xhs/XhsNoteSearchPort.java` | 搜索 Port |
| `.../tool/xhs/MockXhsNoteSearchClient.java` | 本地假数据 |
| `.../tool/xhs/ApifyXhsNoteSearchClient.java` | 关键词 Actor |
| `.../tool/xhs/XhsNoteSearchProperties.java` | client + searcher 嵌套配置 |
| `.../tool/xhs/XhsNoteCandidate.java` | 管线内部候选 |
| `.../tool/xhs/XhsNoteReranker.java` | 重排接口 |
| `.../tool/xhs/ModelXhsNoteReranker.java` | pi-ai `ebus.xhs.rerank` |
| `.../tool/xhs/XhsNoteSearcher.java` (+test) | 六步编排 |
| `.../tool/xhs/SearchXhsNoteToolHandler.java` (+test) | Pi tool |
| `.../tool/xhs/XhsNoteFetchPort.java` | 详情 Port |
| `.../tool/xhs/XhsNoteFetchHit.java` | title/body/url… |
| `.../tool/xhs/MockXhsNoteFetchClient.java` | mock |
| `.../tool/xhs/ApifyXhsNoteFetchClient.java` | 详情 Actor |
| `.../tool/xhs/XhsNoteFetchProperties.java` | 详情配置 |
| `.../tool/xhs/FetchXhsNoteToolHandler.java` (+test) | Pi tool |
| `.../config/PiToolCatalogConfiguration.java` 或 `EbusPiToolCatalogConfiguration` | 注册两工具 |
| `scenes/xiaohongshu/xhs-*/SKILL.md` + `references/output.md` | 三技能（多模块镜像） |
| `SceneCapabilityPackLoader.java` | xiaohongshu 必含三 skill |
| `SkillRunProfile.java` + `ArtifactType.java` + `ArtifactPersistPlugin.java` | 三种 persist |
| `APP-META/...` SQL 或 seed | `xiaohongshu` → `AVAILABLE` |
| `forma-web/.../XiaohongshuWorkspace*.vue` | 三胶囊工作台 |

## Spec → Task

| Spec | Task |
|------|------|
| §7.1 Port + 厚管线 | 1–3 |
| §7.2 fetch | 4 |
| §3/§8 skills | 5 |
| §6 persistAs / 装包 / 亮卡 | 6 |
| §4 工作台 | 7 |

**可拆 PR：** Tasks 1–4（工具）→ Tasks 5–6（skill+结算）→ Task 7（FE）。本计划按顺序一次写完，执行时可按 PR 切开。

---

### Task 1: `XhsNoteSearchHit` + Port + Mock + Properties

**Files:**
- Create: `forma-application/src/main/java/com/xmut/ebus/application/business/agent/tool/xhs/XhsNoteSearchHit.java`
- Create: `.../tool/xhs/XhsNoteSearchPort.java`
- Create: `.../tool/xhs/MockXhsNoteSearchClient.java`
- Create: `.../tool/xhs/XhsNoteSearchProperties.java`
- Create: `.../tool/xhs/XhsNoteSearchPropertiesTest.java`

**Interfaces:**
- Produces: `XhsNoteSearchPort#search(String query, int pageSize): List<XhsNoteSearchHit>`
- Produces: Hit getters：`noteId`, `title`, `desc`, `noteUrl`, `likedCount`（String 可空）, `author`（可空）
- Produces: `XhsNoteSearchProperties`：`client`（`mock`\|`apify`）、`apify.actorId` 默认 `opspilot.cc/xiaohongshu-keyword-search-scraper`、`searcher.enabled/rerankPoolSize/rerankUseCase/bannedTitleKeywords/...`（字段名对齐 `SkuSearchProperties.Searcher`）

- [ ] **Step 1: 写失败测 — Properties 默认值**

```java
@Test
void defaults_mock_and_default_actor() {
    XhsNoteSearchProperties p = new XhsNoteSearchProperties();
    assertEquals("mock", p.getClient());
    assertEquals("opspilot.cc/xiaohongshu-keyword-search-scraper", p.getApify().getActorId());
    assertTrue(p.getSearcher().isEnabled());
    assertEquals(40, p.getSearcher().getRerankPoolSize());
    assertEquals("ebus.xhs.rerank", p.getSearcher().getRerankUseCase());
}
```

- [ ] **Step 2: Run test — expect FAIL（类不存在）**

```bash
mvn -pl forma-application -Dtest=XhsNoteSearchPropertiesTest test
```

- [ ] **Step 3: 最小实现 Hit / Port / Mock / Properties**

`MockXhsNoteSearchClient`：query 非空时返回 ≥8 条带 `https://www.xiaohongshu.com/explore/...` 的假笔记；空 query → empty。

- [ ] **Step 4: Run test — expect PASS**

- [ ] **Step 5: Commit**

```bash
git add forma-application/src/main/java/com/xmut/ebus/application/business/agent/tool/xhs \
  forma-application/src/test/java/com/xmut/ebus/application/business/agent/tool/xhs
git commit -m "feat(xhs): add note search port, mock client, and properties"
```

---

### Task 2: `XhsNoteSearcher` 六步（identity rerank）

**Files:**
- Create: `.../tool/xhs/XhsNoteCandidate.java`
- Create: `.../tool/xhs/XhsNoteReranker.java`
- Create: `.../tool/xhs/XhsNoteSearcher.java`
- Create: `.../tool/xhs/XhsNoteSearcherTest.java`

**Interfaces:**
- Produces: `XhsNoteSearcher#search(String query, int pageSize): List<XhsNoteSearchHit>`
- Produces: 步骤方法签名与 `SkuSearcher` 同构（候选类型换为 `XhsNoteCandidate`）
- Produces: `XhsNoteReranker#orderIds(String intent, List<XhsNoteCandidate> pool): List<String>`
- Produces: `XhsNoteReranker.identity()`
- Consumes: `XhsNoteSearchPort`, `XhsNoteSearchProperties`, `XhsNoteReranker`

- [ ] **Step 1: 写失败测**

```java
@Test
void expandQuery_trimsToSingleton() {
    XhsNoteSearcher s = newSearcher(mockPort, XhsNoteReranker.identity());
    assertEquals(Collections.singletonList("杯垫"), s.expandQuery(" 杯垫 "));
}

@Test
void doCheck_drops_non_https_and_empty_title() {
    // 构造 candidate：空 title / http:// / 正常 https → 仅保留正常
}

@Test
void search_enabled_pipeline_returns_topHits() {
    // mock Port 返回 15 条合法；pageSize=10 → size 10
}

@Test
void search_disabled_short_circuits_to_port() {
    // searcher.enabled=false → 直接 Port，不经 doCheck 违禁过滤（与 SkuSearcher 行为对齐）
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
mvn -pl forma-application -Dtest=XhsNoteSearcherTest test
```

- [ ] **Step 3: 实现 Searcher（可直接对照 `SkuSearcher.java` 改写字段：`detailUrl`→`noteUrl`，title 违禁同逻辑）**

- [ ] **Step 4: Run — expect PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(xhs): add XhsNoteSearcher pipeline with identity rerank"
```

---

### Task 3: `SearchXhsNoteToolHandler` + Spring 装配 + Apify Port 骨架

**Files:**
- Create: `.../tool/xhs/SearchXhsNoteToolHandler.java`
- Create: `.../tool/xhs/SearchXhsNoteToolHandlerTest.java`
- Create: `.../tool/xhs/ApifyXhsNoteSearchClient.java`（可先抛 `UnsupportedOperationException` 或 parse 空数组；至少能绑定 actorId）
- Create: `.../tool/xhs/ApifyXhsNoteHitMapper.java`
- Modify: `.../config/PiToolCatalogConfiguration.java`（或现网 ebus 覆盖配置类）— 注册 `search_xhs_note`
- Modify: 若有 `ModelCatalog` 注册 useCase — 增加 `ebus.xhs.rerank`（可与 sku rerank 同模型配置）

**Interfaces:**
- Produces: tool name 常量 `search_xhs_note`
- Produces: 成功 JSON `{"hits":[{noteId,title,desc,noteUrl,likedCount,author},...]}`
- Produces: 空 hits → `ToolResult.failed(..., "search_xhs_note empty hits")`
- Consumes: `XhsNoteSearcher`

- [ ] **Step 1: 写 Handler 测（Mockito Searcher）**

```java
@Test
void handle_ok_writes_hits_json() { /* ... */ }

@Test
void handle_empty_fails() { /* ... */ }

@Test
void handle_missing_query_fails() { /* ... */ }
```

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: 实现 Handler + `@Bean` 装配；`client=apify` 时用 `ApifyXhsNoteSearchClient`（复用现有 `ApifyActorTransport`）**

Apify input 最小集（按 Actor 文档）：`keyword` + 可选 `page`；dataset 映射到 `XhsNoteSearchHit`（缺字段则空字符串，URL 必须能取到才进 hits）。

- [ ] **Step 4: 可选 — `ModelXhsNoteReranker` 对标 `ModelSkuReranker`（失败回池序）；单测打桩 `ModelProvider`**

若本 Task 时间紧：装配仍用 `identity()`，把 Model rerank 作为 Task 3b 同 PR 或紧随提交。

- [ ] **Step 5: Run 相关测 PASS + Commit**

```bash
git commit -m "feat(xhs): register search_xhs_note tool and Apify search port"
```

---

### Task 4: `fetch_xhs_note` 详情工具

**Files:**
- Create: `.../tool/xhs/XhsNoteFetchHit.java`（`title`, `body`, `noteUrl`, `author?`, `tags?`）
- Create: `.../tool/xhs/XhsNoteFetchPort.java` — `XhsNoteFetchHit fetch(String noteRef)`
- Create: `.../tool/xhs/MockXhsNoteFetchClient.java`
- Create: `.../tool/xhs/ApifyXhsNoteFetchClient.java`（默认 actor `khadinakbar/xiaohongshu-note-detail-scraper`）
- Create: `.../tool/xhs/XhsNoteFetchProperties.java`
- Create: `.../tool/xhs/FetchXhsNoteToolHandler.java` (+test)
- Modify: tool catalog 注册 `fetch_xhs_note`

**Interfaces:**
- Tool 入参：`url`（或 `noteUrl`）必填字符串
- 成功：JSON 含 `title`/`body`/`noteUrl`
- 失败：空 body 或 Port 异常 → `ToolResult.failed`

- [ ] **Step 1: 写测 — mock 返回固定正文；空 url failed**

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: 实现 Mock + Handler + Apify 客户端（input 按 Actor：`noteUrls` 数组包一层用户 URL）**

- [ ] **Step 4: Run — PASS + Commit**

```bash
git commit -m "feat(xhs): add fetch_xhs_note detail tool"
```

---

### Task 5: 三个 SKILL.md + output.md（镜像三处）

**Files:**（每处同样结构）
- Create: `forma-starter/src/main/resources/scenes/xiaohongshu/xhs-topiclist/SKILL.md`
- Create: `.../xhs-topiclist/references/output.md`
- Create: `.../xhs-note/SKILL.md` + `references/output.md`
- Create: `.../xhs-break/SKILL.md` + `references/output.md`
- Mirror: `forma-application/src/test/resources/scenes/xiaohongshu/...`
- Mirror: `pi-agent/src/test/resources/scenes/xiaohongshu/...`

**Interfaces:**
- Frontmatter：`name` / `description` / `allowed-tools` / `metadata.output.billing=true` / `persistAs` / `requiresView=true`
- topiclist `allowed-tools`: `read_skill search_xhs_note write_file read_file bash`
- note: `read_skill write_file read_file bash`（无搜索）
- break: `read_skill fetch_xhs_note write_file read_file bash`

- [ ] **Step 1: 按 spec §7.1 外层 + §8.1 写 `xhs-topiclist`（对标 `ecommerce-picklist` 结构：Workflow / Tool / Quality / Verification / Failures）**

要点钉死：
- 至多 1 次 `search_xhs_note`
- 成功 `source=apify`；失败/空 → `source=model_fallback`，禁假链
- items `tp-n`，8–12，1–2 条 `【优先发】`
- 终稿仅 `{"output":"final.json"}`

- [ ] **Step 2: 写 `xhs-note`（一次成稿，无 HITL；交接 `topicItemId`）**

- [ ] **Step 3: 写 `xhs-break`（链接→fetch 至多 1 次；无原文 Fail；`source=apify|paste`）**

- [ ] **Step 4: 同步三处 resources；跑装包/扫描测**

```bash
mvn -pl forma-starter -am -Dtest=SceneCapabilityPackBootstrapTest,SkillsTest,SkillCatalogAndBodyTest test
```

（若现测只认 ecommerce：先改 Task 6 装包再跑；本步至少保证 classpath 能 parse 三个 SKILL。）

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(xhs): add topiclist, note, and break skills"
```

---

### Task 6: 装包、计费 Profile、落库类型、亮卡

**Files:**
- Modify: `SceneCapabilityPackLoader.java` — `SCENE_XHS="xiaohongshu"`；必含三 skill 常量
- Modify: `SkillRunProfile.java` — `PERSIST_XHS_*` + `billedXhsTopiclist/Note/Break` + `resolve` 分支
- Modify: `ArtifactType.java` — `XHS_TOPICLIST` / `XHS_NOTE` / `XHS_BREAK`
- Modify: `ArtifactPersistPlugin.java` — `resolveType` 映射；payload 校验可先做非空 view+artifact 根字段（严校验可跟 output.md 迭代）
- Modify: 相关单测（`SkillRunProfile` 若有测、`ArtifactTypeTest`、`SceneCapabilityPackLoaderTest`、`SceneCatalogIntegrationTest`）
- Modify: seed SQL — 新文件 `APP-META/bootstrap/sql/0xx_xhs_scene_available.sql` **或** 改 `008` 仅当团队允许改种子；推荐 **增量 SQL**：`UPDATE ebus_scene SET status='AVAILABLE' WHERE scene_code='xiaohongshu';`
- Modify: 集成测期望 `xiaohongshu` 为 `AVAILABLE`

**Interfaces:**
- `SkillRunProfile.resolve("xhs-topiclist", false)` → persist `xhs_topiclist`
- 同理 note / break
- `ArtifactType.fromCode("xhs_topiclist")` 等

- [ ] **Step 1: 写/改失败测 — resolve 三 skill；fromCode；pack 缺一则不可用**

- [ ] **Step 2: Run — FAIL**

- [ ] **Step 3: 实现常量与分支；增量 SQL；更新集成测**

- [ ] **Step 4: Run**

```bash
mvn -pl forma-starter -am -Dtest=ArtifactTypeTest,SceneCapabilityPackLoaderTest,SceneCatalogIntegrationTest,SceneCapabilityPackBootstrapTest test
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(xhs): wire skill billing profiles, artifact types, and available scene"
```

---

### Task 7: 前端小红书工作台（三胶囊）

**Files:**
- Create: `forma-web/src/views/business/scene/XiaohongshuWorkspace.vue`（可先 clone `EcommerceWorkspacePlaceholder.vue` 再删 listing/picklist 专用逻辑）
- Create: `.../XiaohongshuWorkspace.test.ts`
- Modify: 路由 — `xiaohongshu` AVAILABLE 进入本工作台（对标 ecommerce 路由）
- Modify: `SceneGallery` 点击逻辑（若仍写死 only ecommerce）
- Capsule templates（spec §4 示例句）
- Computer：近端可用通用 markdown/list 渲染已有 `view.blocks`；三种 kind 枚举 `topiclist|note|break`

**Interfaces:**
- Run API 带 `sceneCode=xiaohongshu` + `skillId=xhs-topiclist|xhs-note|xhs-break`
- 交接：列表项「写成笔记」拼 prompt（含 `tp-n`），对标选品→上架

- [ ] **Step 1: 写测 — 三胶囊可见；点胶囊填入示例句；sceneCode 正确**

- [ ] **Step 2: Run FE test — FAIL**

```bash
cd forma-web && npm test -- XiaohongshuWorkspace
```

- [ ] **Step 3: 实现页面与路由；复用现有 SSE/agent composable 若可泛化，否则薄包一层**

- [ ] **Step 4: lint + test PASS**

```bash
cd forma-web && npm run lint && npm test -- XiaohongshuWorkspace
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(xhs): add Xiaohongshu workspace with three capsules"
```

---

## Self-review（对照 spec）

| Spec 要求 | Task |
|-----------|------|
| 三独立技能 + persistAs | 5–6 |
| 选题厚管线 / Apify=Port | 1–3 |
| model_fallback | 5（skill 文案） |
| 爆文链接优先 + 详情 Actor | 4–5 |
| 笔记一次成稿 / 内嵌配图提示 | 5 |
| 交接弱串联 | 5 + 7 |
| 亮卡 AVAILABLE | 6–7 |
| CI 不打真网 | 1–4 mock/桩 |
| Computer 三种预览精细 UI | Task 7 用通用 blocks；专项美化可另开（YAGNI） |

无 TBD 占位；命名与 spec §Naming / §7 一致。

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-10-01-xiaohongshu-scene-skills.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每 Task 新开子代理，Task 间人工过一眼  
2. **Inline Execution** — 本会话按 `executing-plans` 连续做，设检查点  

Which approach?
