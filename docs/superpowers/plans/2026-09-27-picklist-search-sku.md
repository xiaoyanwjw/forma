# Picklist search_sku + 原链预览 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 选品先调 `search_sku`（推广池抽样），再排名产出双轨 `{view, artifact}`；Computer 每条候选可点开平台原链；无搜/无链则不 settle。

**Architecture:** ebus 注册 `search_sku` Tool（Mock 默认 + 淘宝客真客户端开关）并入 `ToolCatalog`；Skill `allowed-tools` 启用；artifact 校验每条 `sourceUrl`；Normalize/FE 透传并渲染 `ListItem.href`。近端仍用 `SkillRunProfile.billedPicklist()`，不解析 YAML `output:`。

**Tech Stack:** Java 8 / Spring Boot 2.7（`lippi-ai-ebus-application` + `lippi-pi-agent` Tool API）、Vue3 / Vitest（`lippi-ai-ebus-web`）

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-27-picklist-marketplace-search-skill-design.md`
- Dual-track: `docs/superpowers/specs/2026-09-27-skill-dual-track-computer-contract-design.md`（`ListItem.href` 仅 `https:`）
- AD-5：可用 artifact 落库 + view 门禁后才 settle；失败 release
- 密钥仅环境变量 / APP-META；前端零直连 `search_sku`
- 不做生意参谋/闲鱼/聚合爬虫；不做 iframe 内嵌商品页
- 品类模板选择器仍后置（通用默认）
- 多多客 / frontmatter `metadata.output` 解析 → Out of scope

## File map

| Path | Responsibility |
|------|----------------|
| `lippi-ai-ebus-web/src/types/business/computerView.ts` | `ComputerListItem.href?`；parse 仅保留 `https:` |
| `lippi-ai-ebus-web/src/components/business/computer/ComputerRenderer.vue` (+test) | 标题或「查看原商品」外链 |
| `.../computer/NormalizeViewProjector.java` (+test) | list item 透传清洗 `href` |
| `.../sku/SkuSearchPort.java` | 端口：`search(query, platform, pageSize)` |
| `.../sku/MockSkuSearchClient.java` | 默认返回带 `detailUrl` 的摘要 |
| `.../sku/TaobaoTbkSkuSearchClient.java` | 真客户端（feature flag）；密钥 env |
| `.../sku/SearchSkuToolHandler.java` | pi `ToolHandler`；名 `search_sku` |
| `.../config/EbusToolCatalogConfiguration.java`（或 starter） | `ToolCatalog` = `read_skill` + `search_sku`（覆盖 pi 默认 MissingBean） |
| `.../picklist/dto/PicklistArtifactDTO.java` + Item/Command | 增 `sourceUrl` |
| `.../picklist/support/PicklistArtifactParser.java` (+test) | 每条强制非空 `https` `sourceUrl` |
| `.../picklist/support/PicklistViewProjector.java` | Legacy 投影时带上 `href`←`sourceUrl` |
| `.../agent/support/PicklistArtifactPersistPlugin.java` | extras items 含 `sourceUrl` |
| `.../agent/service/AgentApplicationService.java` | 计费选品回合累计 `search_sku` 成功次数；&lt;1 → 不 settle |
| 三份 `ecommerce-picklist/SKILL.md` | §4 模板 + `search_sku` + 原链合同 |
| `.env.example` / APP-META 注释 | `TBK_*` / `ebus.sku-search.mode=mock\|tbk` |

---

### Task 1: FE + Normalize — `ListItem.href`

**Files:**
- Modify: `lippi-ai-ebus-web/src/types/business/computerView.ts`
- Modify: `lippi-ai-ebus-web/src/components/business/computer/ComputerRenderer.vue`
- Modify: `lippi-ai-ebus-web/src/components/business/computer/ComputerRenderer.test.ts`
- Modify: `lippi-ai-ebus-application/.../computer/NormalizeViewProjector.java`
- Modify: `.../NormalizeViewProjectorTest.java`

**Interfaces:**
- Produces: list item 可选 `href: string`（仅 `https:`）；FE 新标签打开

- [x] **Step 1: 写失败测（FE）**

在 `ComputerRenderer.test.ts` 增加：带 `href: 'https://item.example/1'` 的 list item 渲染出 `<a target="_blank" rel="noopener noreferrer">`；`http://` 与缺省不渲染链接。

- [x] **Step 2: 实现类型 + parse + Renderer**

```ts
// ComputerListItem
export interface ComputerListItem {
  badge?: string
  title: string
  lines?: ComputerListLine[]
  tags?: ComputerTag[]
  /** https only; omit if invalid */
  href?: string
}

function sanitizeHttpsHref(raw: unknown): string | undefined {
  if (typeof raw !== 'string') return undefined
  const t = raw.trim()
  if (!t.startsWith('https://')) return undefined
  return t
}
```

Renderer：有 `href` 时标题包 `<a>`，或在条目下增加文案「查看原商品」链接（二选一，优先标题可点 + 旁路短链文案均可）。

- [x] **Step 3: Normalize 透传**

在 `cleanList` 的 `out` map 中：

```java
String href = asHttpsHref(item.get("href"));
if (href != null) {
    out.put("href", href);
}
```

`asHttpsHref`：trim 后必须以 `https://` 开头，否则丢弃。

- [x] **Step 4: 跑测**

```bash
cd lippi-ai-ebus-web && npm test -- --run src/components/business/computer/ComputerRenderer.test.ts
mvn -pl lippi-ai-ebus-application -am -Dtest=NormalizeViewProjectorTest test
```

Expected: PASS

- [x] **Step 5: Commit**

```bash
git add lippi-ai-ebus-web/src/types/business/computerView.ts \
  lippi-ai-ebus-web/src/components/business/computer/ComputerRenderer.vue \
  lippi-ai-ebus-web/src/components/business/computer/ComputerRenderer.test.ts \
  lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/computer/NormalizeViewProjector.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/computer/NormalizeViewProjectorTest.java
git commit -m "$(cat <<'EOF'
feat(computer): support https ListItem.href for product deep links

EOF
)"
```

---

### Task 2: `search_sku` Tool（Mock + Catalog 注册）

**Files:**
- Create: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/sku/SkuSearchPort.java`
- Create: `.../sku/SkuSearchHit.java`（immutable：platform, title, price, category, detailUrl, rawRef）
- Create: `.../sku/MockSkuSearchClient.java`
- Create: `.../sku/SearchSkuToolHandler.java`
- Create: `.../config/EbusPiToolCatalogConfiguration.java`
- Create: `.../sku/SearchSkuToolHandlerTest.java`（或 Mock client test）
- Modify: `APP-META/docker-config/environment/.env.example`（占位键，无真密）

**Interfaces:**
- Consumes: `com.xmut.lims.pi.agent.tool.Tool` / `ToolHandler` / `InMemoryToolCatalog`；`AgentConfiguration.readSkillTool` 模式
- Produces: 工具名恒为 `search_sku`；JSON 数组 hits，每项含必填 `detailUrl`

- [x] **Step 1: 写失败测**

`SearchSkuToolHandlerTest`：`query=香薰` → 返回 JSON 含 ≥1 条且每条 `detailUrl` 以 `https://` 开头；空 query → 错误结果（不抛未捕获异常炸环）。

- [x] **Step 2: Port + Mock**

```java
public interface SkuSearchPort {
    List<SkuSearchHit> search(String query, String platform, int pageSize);
}
```

Mock：固定 10 条样例，`detailUrl = "https://mock.tbk.local/item/" + i`，title 含 query 片段。

- [x] **Step 3: ToolHandler + schema**

参数 JSON Schema：`query`（required）、`platform`（默认 `taobao_tbk`）、`pageSize`（默认 10，上限 20）。  
Handler 调 Port，序列化为 tool result 文本/JSON（与现有 ToolResult 约定一致）。

- [x] **Step 4: ToolCatalog Bean**

`@Configuration` + `@ConditionalOnMissingBean` 不适用（要覆盖 pi 默认）：在 ebus-starter/application 提供 **主键** `ToolCatalog`：

```java
return InMemoryToolCatalog.of(Arrays.asList(
    AgentConfiguration.readSkillTool(skillCatalog), // 若包可见；否则复制 read_skill 构造
    searchSkuTool(port)
));
```

确认启动后 `toolCatalog.resolve("search_sku").isPresent()`（可加 starter 烟测或 application 测）。

- [x] **Step 5: 跑测 + Commit**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=SearchSkuToolHandlerTest test
```

```bash
git commit -m "$(cat <<'EOF'
feat(agent): register search_sku tool with mock marketplace client

EOF
)"
```

---

### Task 3: artifact `sourceUrl` 校验 + Legacy 投影带 href

**Files:**
- Modify: `PicklistArtifactDTO.PicklistItemDTO`、`PersistPicklistItemCommand`
- Modify: `PicklistArtifactParser.java` + `PicklistArtifactParserTest.java`
- Modify: `PicklistApplicationService.java`（映射 sourceUrl 落库字段：若表无列则先存 JSON payload / 扩展列——**先读现有 picklist 表结构**；无列则仅 DTO+SSE extras，库内可进 JSON blob）
- Modify: `PicklistViewProjector.java` + test：`href` ← `sourceUrl`
- Modify: `PicklistArtifactPersistPlugin.toReadyExtras`

**Interfaces:**
- Produces: 每条 item 必有 `sourceUrl`（https）；缺则 `MSG_UNUSABLE`；view list `href` 对齐

- [x] **Step 1: 查表**

打开 `APP-META/bootstrap` SQL / MyBatis entity：若 `picklist_item` 无 URL 列，本任务 **DTO + SSE extras + 校验** 先落地；持久化列作为同任务最小 ALTER（`source_url VARCHAR(512)`）或写入已有 JSON 扩展字段。选定一种并在 PR 说明。

- [x] **Step 2: 失败测**

Parser：8 条齐但缺 `sourceUrl` → `MSG_UNUSABLE`；合法 https → pass。

- [x] **Step 3: 实现校验与投影**

Parser `requiredHttps(itemNode, "sourceUrl")`。  
`PicklistViewProjector.projectItems`：`row.put("href", item.getSourceUrl())`（已是 https）。

- [x] **Step 4: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=PicklistArtifactParserTest,PicklistViewProjectorTest test
```

- [x] **Step 5: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(picklist): require https sourceUrl and project list href

EOF
)"
```

---

### Task 4: 重写三份 `ecommerce-picklist` SKILL（§4 模板）

**Files:**
- Modify: `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md`
- Modify: `lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md`
- Modify: `lippi-pi-agent/src/test/resources/...`（若存在同路径镜像；以 Glob 为准同步）

**Interfaces:**
- Produces: frontmatter `allowed-tools: read_skill search_sku`；Workflow 含先搜再排；Output 含 `sourceUrl`/`href` 示例

- [x] **Step 1: 按规格 §4 重写正文**

必须包含章节：When to use / Workflow / Tools / Boundaries / Output contract / Failures。  
删除「不做实时平台数据」类旧边界；改为「必须 search_sku；链接来自工具」。  
双轨 JSON 示例：list item 带 `"href": "https://..."`，artifact item 带 `"sourceUrl": "https://..."`。  
disclaimer 点名推广池抽样 + 可点原链核对。

- [x] **Step 2: 确认 Skill 装载**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=SceneCapabilityPackLoaderTest test
```

断言（若测试读 allowedTools）：含 `search_sku`。必要时改断言。

- [x] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs(skill): rewrite ecommerce-picklist for search_sku and source links

EOF
)"
```

---

### Task 5: 选品回合硬门禁 — ≥1 次成功 `search_sku`

**Files:**
- Modify: `AgentApplicationService.java`（billed picklist / `streamGenerationRun` 路径）
- Modify: `AgentApplicationServiceTest.java`

**Interfaces:**
- Consumes: 流式过程中 `tool_finished` / Pi `TOOL_EXECUTION_END` 且 `toolName=search_sku` 且结果非错误
- Produces: 计数 &lt; 1 时与不可用成果同等：release，不 settle，发 `run_failed`（人话：需要先检索商品）

- [x] **Step 1: 写失败测**

Mock Agent：无 tool 调用直接吐合格 JSON → **不**调用 settle；有一次成功 `search_sku` + 合格 JSON → settle。

- [x] **Step 2: 实现计数器**

在回合事件循环内：`AtomicBoolean` / int `searchSkuOk`；识别 tool 结束成功。Persist 前：

```java
if (profile.isBilledPicklist() && searchSkuOk.get() < 1) {
    creditHoldSupport.tryRelease(...);
    emit run_failed(...);
    return;
}
```

（方法名以现有 `SkillRunProfile` / CreditHold 为准。）

- [x] **Step 3: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -Dtest=AgentApplicationServiceTest test
```

- [x] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(agent): require successful search_sku before picklist settle

EOF
)"
```

---

### Task 6: 淘宝客真客户端开关（可选同 PR 或紧随）

**Status:** deferred（follow-up；Mock 已满足 §8.2–5）

**Files:**
- Create: `TaobaoTbkSkuSearchClient.java`
- Modify: `@ConfigurationProperties(prefix = "ebus.sku-search")`：`mode=mock|tbk`
- Modify: `.env.example`：`TBK_APP_KEY` / `TBK_APP_SECRET` / `TBK_ADZONE_ID`（名以官方为准）

- [ ] **Step 1:** mode=mock 时仍走 Mock；mode=tbk 缺密钥启动失败或回退 Mock 并打 error 日志（选一种，推荐：**缺密钥 fail-fast 仅当 mode=tbk**）。
- [ ] **Step 2:** 单测用 WireMock 或对 Port 打桩；不在 CI 打真网。
- [ ] **Step 3:** Commit `feat(sku): optional taobao tbk sku search client`

若时间紧：本任务可整段挪到 follow-up，Mock 已满足规格验收 2/5。

---

### Task 7: 规格勾选 + 文档收尾

- [x] 规格 §4.3 / §8 验收项在设计稿打勾（实现已覆盖的）。
- [x] dual-track 修订记录已有 `href`；本计划路径写入 `docs/superpowers/plans/`（本文件）。
- [ ] 手动冒烟清单（开发机）：选品 → Computer 每条可点 https → 新标签；断网/Mock 空结果 → 不扣分。（未在本轮执行。）

---

## Out of scope

- 多多客 `pdd_ddk` 真客户端  
- 解析 SKILL `metadata.output` 驱动 `SkillRunProfile`  
- 删除 `LegacyPicklistFallbackProjector`  
- 前端直连搜索 API  
- 强制模型「搜索次数」与业务排名算法精细化（仅 ≥1 次成功 + 人工排名指引）

## Spec coverage (self-review)

| Spec 要求 | Task |
|-----------|------|
| `search_sku` 工具 + 注册 | 2 |
| `detailUrl` → `sourceUrl` → `href` | 1, 2, 3 |
| FE 新标签 https 外链 | 1 |
| SKILL §4 + allowed-tools | 4 |
| ≥1 次成功搜索才 settle | 5 |
| 无结果 / 缺链不 settle | 3, 5 |
| 密钥不进前端 | 2, 6 |
| 淘宝客真客户端 | 6（可后置） |
| 标准模板三份镜像 | 4 |

## 执行方式

Plan 已保存。可选：

1. **Subagent-Driven（推荐）** — 每 Task 新开子代理，中间人工过一眼  
2. **Inline Execution** — 本会话按 `executing-plans` 连续做  

要哪种？
