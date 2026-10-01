# SkuSearcher + Model Rerank Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `search_sku` 经 `SkuSearcher.search` 完成 Demo 步骤（恒等 `expandQuery`、单路 `doSearch`、`doCheck`、`pooling`、`rerank`、`topHits`），重排走独立 pi-ai useCase，主 agent 仍只见最终瘦 hits。

**Architecture:** `SearchSkuToolHandler` 只调 `SkuSearcher#search`。步骤方法名钉死（spec §3.1）。`ModelSkuReranker` 注入 `ModelProvider`，失败降级为池序。`searcher.enabled=false` 时短路为单次 `SkuSearchPort.search`（≈现网）。

**Tech Stack:** Java 8 / Spring Boot 2.7、`lippi-ai-ebus-application`、`lippi-pi-ai`（`ModelProvider` / `ModelRequest`）、JUnit 5 + Mockito

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-01-sku-search-multi-recall-rerank-design.md`（accepted）
- 方法名钉死：`search` / `expandQuery` / `doSearch` / `doCheck` / `pooling` / `rerank` / `topHits` — **禁止**另起别名
- Demo：`expandQuery` 不扩词；`doSearch` 只查 `queries.get(0)`；**无**独立 dedupe 方法
- 重排 **不**进 `AgentSession`；不注册为 Pi Tool；prompt **不**进 SKILL.md
- Tool 对外仍 `query` + `platform` + `pageSize`；回传 `{hits:[…]}` 现网字段 + 可选 `rank`
- Java 8：无 `var` / `List.of`；用 `Collections` / 显式类型
- CI 不打真 Apify / 真网大模型（打桩 `SkuSearchPort` + `ModelProvider`）
- 配置前缀：`ebus.sku-search.searcher.*`（嵌在 `SkuSearchProperties`）

## Naming

| 角色 | 名字 |
|------|------|
| 入口编排 | `SkuSearcher` |
| 重排接口 | `SkuReranker` |
| 模型实现 | `ModelSkuReranker` |
| 恒等序 | `SkuReranker.identity()`（接口 static，**不**单开类） |
| 配置嵌套 | `SkuSearchProperties.Searcher` |

## File map

| Path | Responsibility |
|------|----------------|
| `.../tool/sku/SkuCandidate.java` | 内部候选（id + hit 字段） |
| `.../tool/sku/SkuSearcher.java` (+test) | §3.1 全步骤编排 |
| `.../tool/sku/SkuReranker.java` | 接口 + `identity()` |
| `.../tool/sku/ModelSkuReranker.java` (+test) | pi-ai 实现 |
| `.../tool/sku/SkuSearchProperties.java` | 增加 `searcher` 嵌套配置 |
| `.../tool/sku/SearchSkuToolHandler.java` (+test) | 注入 `SkuSearcher`；空结果 failed |
| `.../config/EbusPiToolCatalogConfiguration.java` | 装配 Searcher / Reranker / Handler |
| `scenes/.../ecommerce-picklist/SKILL.md` (+mirrors) | 薄改措辞 |

## Spec → Task

| Spec | Task |
|------|------|
| SkuCandidate + expand/doSearch/doCheck/pooling/topHits | 1 |
| rerank + ModelSkuReranker | 2 |
| Handler + Spring 装配 | 3 |
| Skill 薄改 | 4 |

---

### Task 1: `SkuCandidate` + `SkuSearcher`（无真模型）

**Files:**
- Create: `.../tool/sku/SkuCandidate.java`
- Create: `.../tool/sku/SkuReranker.java`
- Create: `.../tool/sku/SkuSearcher.java`
- Modify: `.../tool/sku/SkuSearchProperties.java`
- Create: `.../tool/sku/SkuSearcherTest.java`

**Interfaces:**
- Produces: `SkuCandidate` getters：`id`, `platform`, `title`, `price`, `category`, `detailUrl`, `rawRef`（+ 可选 `recallFrom`）
- Produces: `SkuReranker#orderIds(String intent, List<SkuCandidate> pool): List<String>`
- Produces: `SkuReranker.identity()` — 按 pool 原序返回 id 列表
- Produces: `SkuSearcher#search(String,String,int): List<SkuSearchHit>`
- Produces: 步骤方法（至少测 `expandQuery` / `pooling` / `doCheck`）：签名与 spec §3.1 一致
- Consumes: `SkuSearchPort`, `SkuSearchProperties`, `SkuReranker`

- [ ] **Step 1: 扩展 Properties**

```java
private final Searcher searcher = new Searcher();
public Searcher getSearcher() { return searcher; }

public static class Searcher {
    private boolean enabled = true;
    private int maxLegs = 1;
    private int sourcePageSize = 20;
    private int rerankPoolSize = 40;
    private String rerankUseCase = "ebus.sku.rerank";
    private long rerankTimeoutMs = 12_000L;
    private boolean exposeReasons = false;
    private String bannedTitleKeywords = ""; // 逗号分隔，可空
    // getters/setters…
}
```

绑定前缀：`ebus.sku-search.searcher.*`。

- [ ] **Step 2: 写失败测 — expand / pooling / doCheck / search**

```java
@Test
void expandQuery_trimsToSingleton() {
    SkuSearcher s = newSearcher(mockPort, SkuReranker.identity());
    assertEquals(Collections.singletonList("杯垫"), s.expandQuery(" 杯垫 "));
    assertTrue(s.expandQuery("  ").isEmpty());
}

@Test
void pooling_capsAtRerankPoolSize() {
    props.getSearcher().setRerankPoolSize(2);
    List<SkuCandidate> in = Arrays.asList(c("h1"), c("h2"), c("h3"));
    assertEquals(2, newSearcher(port, SkuReranker.identity()).pooling(in).size());
}

@Test
void doCheck_dropsNonHttpsAndBlankTitle() {
    List<SkuCandidate> out = searcher.doCheck(Arrays.asList(
        candidate("h1", "ok", "https://a.com/1"),
        candidate("h2", "", "https://a.com/2"),
        candidate("h3", "x", "http://insecure")));
    assertEquals(1, out.size());
    assertEquals("h1", out.get(0).getId());
}

@Test
void search_demo_callsPortOnce_andRespectsPageSize() {
    when(port.search(eq("q"), anyString(), anyInt()))
        .thenReturn(Arrays.asList(hitHttps("A"), hitHttps("B"), hitHttps("C")));
    List<SkuSearchHit> out = searcher.search("q", "taobao_tbk", 2);
    verify(port, times(1)).search(eq("q"), anyString(), anyInt());
    assertEquals(2, out.size());
}

@Test
void search_disabled_bypassToPortPageSize() {
    props.getSearcher().setEnabled(false);
    when(port.search(eq("q"), eq("p"), eq(5))).thenReturn(Collections.singletonList(hitHttps("A")));
    assertEquals(1, searcher.search("q", "p", 5).size());
    verify(port).search("q", "p", 5);
}
```

- [ ] **Step 3: 实现 SkuCandidate + SkuReranker.identity + SkuSearcher**

`SkuReranker`：

```java
public interface SkuReranker {
    List<String> orderIds(String intent, List<SkuCandidate> pool);

    static SkuReranker identity() {
        return (intent, pool) -> {
            if (pool == null || pool.isEmpty()) {
                return Collections.emptyList();
            }
            List<String> ids = new ArrayList<String>(pool.size());
            for (SkuCandidate c : pool) {
                ids.add(c.getId());
            }
            return ids;
        };
    }
}
```

`search`（`enabled==true`）：

```java
List<String> queries = expandQuery(query);
List<SkuCandidate> raw = doSearch(queries, platform, props.getSearcher().getSourcePageSize());
List<SkuCandidate> checked = doCheck(raw);
List<SkuCandidate> pool = pooling(checked);
List<SkuCandidate> ranked = rerank(query.trim(), pool);
return topHits(ranked, pageSize);
```

`rerank`：

```java
List<SkuCandidate> rerank(String intent, List<SkuCandidate> pool) {
  if (pool == null || pool.isEmpty()) return Collections.emptyList();
  try {
    List<String> ids = skuReranker.orderIds(intent, pool);
    if (ids == null || ids.isEmpty()) {
      return pool;
    }
    return reorderByIds(pool, ids); // 非法 id 跳过；缺的按原序补尾
  } catch (Exception ex) {
    log.warn("sku rerank failed: {}", ex.toString());
    return pool;
  }
}
```

`toCandidates`：按序赋 `h1`…`hN`。  
`doCheck`：`detailUrl` 必须以 `https:` 忽略大小写开头；title 有文本；banned 关键词（trim 后非空则 `title.contains`）。

- [ ] **Step 4: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -DfailIfNoTests=false -Dtest=SkuSearcherTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/tool/sku \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/tool/sku/SkuSearcherTest.java
git commit -m "$(cat <<'EOF'
feat(sku-search): add SkuSearcher with demo single-leg steps

EOF
)"
```

---

### Task 2: `ModelSkuReranker`

**Files:**
- Create: `.../tool/sku/ModelSkuReranker.java`
- Create: `.../tool/sku/ModelSkuRerankerTest.java`

**Interfaces:**
- Consumes: `ModelProvider#complete(ModelRequest)`；`SkuSearchProperties.Searcher#getRerankUseCase`
- Produces: `ModelSkuReranker implements SkuReranker`
- 输入：system（测款排序常量，代码内）+ user JSON：`{"intent":"…","candidates":[{"id":"h1","title":"…","price":"…","category":"…"},…]}`
- 期望输出：`{"ids":["h2","h1",…]}` 或纯 JSON 数组；解析容错（围栏剥离）
- **不**把 URL 放进 prompt
- 空/不可解析 → 返回 **空列表**（`SkuSearcher.rerank` 视为失败，用原 pool）

- [ ] **Step 1: 写失败测**

```java
@Test
void orderIds_parsesIdsJson() {
    when(model.complete(any())).thenReturn(/* content {"ids":["h2","h1"]} — 按仓库 ModelResponse API */);
    List<String> ids = new ModelSkuReranker(model, props).orderIds("杯垫", Arrays.asList(c("h1"), c("h2")));
    assertEquals(Arrays.asList("h2", "h1"), ids);
    ArgumentCaptor<ModelRequest> cap = ArgumentCaptor.forClass(ModelRequest.class);
    verify(model).complete(cap.capture());
    assertEquals("ebus.sku.rerank", cap.getValue().getUseCase());
    assertFalse(cap.getValue().getMessages().toString().contains("https://"));
}

@Test
void orderIds_garbage_returnsEmpty() {
    when(model.complete(any())).thenReturn(/* content "not-json" */);
    assertTrue(reranker.orderIds("q", Arrays.asList(c("h1"))).isEmpty());
}
```

先读 `ModelResponse` 工厂方法，与现网测试一致。

- [ ] **Step 2: 实现 ModelSkuReranker**

- messages：`Message.system(SYSTEM)` + `Message.user(payloadJson)`  
- `ModelRequest.builder().useCase(props.getSearcher().getRerankUseCase()).messages(...).temperature(0.0).maxTokens(512).build()`  
- 解析：优先 `ids` 数组；过滤非 pool 内 id  

- [ ] **Step 3: Searcher 集成测 — mock 重排序**

在 `SkuSearcherTest` 增加：

```java
when(reranker.orderIds(anyString(), anyList())).thenReturn(Arrays.asList("h3", "h1"));
List<SkuSearchHit> out = searcher.search("q", "p", 3);
assertEquals("title-h3", out.get(0).getTitle());
```

- [ ] **Step 4: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -DfailIfNoTests=false -Dtest=SkuSearcherTest,ModelSkuRerankerTest test
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/tool/sku/ModelSkuReranker.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/tool/sku/ModelSkuRerankerTest.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/tool/sku/SkuSearcherTest.java
git commit -m "$(cat <<'EOF'
feat(sku-search): add ModelSkuReranker via pi-ai useCase

EOF
)"
```

---

### Task 3: Handler 接线 + Spring 装配

**Files:**
- Modify: `SearchSkuToolHandler.java`
- Modify: `EbusPiToolCatalogConfiguration.java`
- Modify/Create: `SearchSkuToolHandlerTest.java`
- Modify: `APP-META/docker-config/environment/.env.example`（可选注释 `ebus.sku-search.searcher.*`）

**Interfaces:**
- Handler ctor：只依赖 `SkuSearcher`（`enabled` 短路在 Searcher 内）
- Beans：`SkuSearcher`、`SkuReranker`（有 `ModelProvider` → `ModelSkuReranker`；否则 `SkuReranker.identity()`）

```java
@Bean
public SkuReranker skuReranker(ObjectProvider<ModelProvider> models, SkuSearchProperties props) {
    ModelProvider mp = models.getIfAvailable();
    if (mp == null) {
        return SkuReranker.identity();
    }
    return new ModelSkuReranker(mp, props);
}

@Bean
public SkuSearcher skuSearcher(SkuSearchPort port, SkuSearchProperties props, SkuReranker reranker) {
    return new SkuSearcher(port, props, reranker);
}

// searchSkuTool(skuSearcher)
```

- [ ] **Step 1: 改 Handler**

```java
List<SkuSearchHit> hits = skuSearcher.search(query.trim(), platform, pageSize);
if (hits == null || hits.isEmpty()) {
  return ToolResult.failed(callId, TOOL_NAME, "search_sku empty hits");
}
return ToolResult.ok(callId, TOOL_NAME, writeHits(hits));
```

Demo：**不改** `SkuSearchHit` 模型；JSON 仍六字段。

- [ ] **Step 2: 测 Handler 调 searcher**

```java
when(searcher.search(eq("香薰"), anyString(), eq(10))).thenReturn(Collections.singletonList(hit));
ToolResult r = handler.handle(callWithQuery("香薰"), ctx);
assertTrue(r.isSuccess());
verify(searcher).search(eq("香薰"), anyString(), eq(10));
verify(port, never()).search(anyString(), anyString(), anyInt());
```

- [ ] **Step 3: 跑测**

```bash
mvn -pl lippi-ai-ebus-application -am -DfailIfNoTests=false -Dtest=SkuSearcherTest,ModelSkuRerankerTest,SearchSkuToolHandlerTest test
```

Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/tool/sku/SearchSkuToolHandler.java \
  lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/config/EbusPiToolCatalogConfiguration.java \
  lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/tool/sku
git commit -m "$(cat <<'EOF'
feat(sku-search): wire SearchSkuToolHandler through SkuSearcher

EOF
)"
```

---

### Task 4: Picklist skill 薄改

**Files:**
- Modify: `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md`
- Sync mirrors under application / pi-agent test resources（若存在）

- [ ] **Step 1: 改文案**

Workflow §2：服务端可能对同一次 `search_sku` 做检索与独立模型重排；**禁止**换词连搜。  
`pageSize`：「返回候选条数上限（重排后）」。  
Quality：「在工具返回的候选中」挑选与写字段。  
**不要**增加扩词表、重排 prompt、多路说明。

- [ ] **Step 2: 同步 mirrors + Commit**

```bash
git add lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist \
  lippi-ai-ebus-application/src/test/resources/scenes/ecommerce/ecommerce-picklist \
  lippi-pi-agent/src/test/resources/scenes/ecommerce/ecommerce-picklist
git commit -m "$(cat <<'EOF'
docs(skill): note server-side sku searcher rerank

EOF
)"
```

---

## Plan self-review

1. **Spec coverage:** §3.1 → T1；模型重排 → T2；Handler → T3；Skill → T4。  
2. **Naming:** `SkuSearcher` / `ModelSkuReranker` / `SkuReranker.identity()`；配置 `searcher`。  
3. **Placeholder scan:** `ModelResponse` 构造以仓库 API 为准。

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-10-01-sku-search-pipeline-rerank.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每 Task 新 subagent + 复核  
2. **Inline Execution** — 本会话连续执行  

Which approach?
