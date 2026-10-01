# 选品检索：多路召回 + LLM 重排（工具变厚）（设计）

**Date:** 2026-10-01  
**Status:** accepted  
**Decision（P0 / Demo）：**  
- **检索管线 + LLM-as-reranker** 下沉到 `search_sku`；主 agent / skill **仍只调一次**  
- 重排是 **管线内另一次 pi-ai 调用**（独立 useCase），**不**占用主 agent 对话上下文、**不**写进 skill  
- Tool **对外合同基本不变**（`query` + `platform` + `pageSize`）  
- 回传 **Top-K 瘦字段**  
- **Demo 收窄：** `expandQuery` **不扩词**（只返回原始 query 单元素列表）；`doSearch` **只跑一路**检索  
- **无独立去重步骤**（Demo 单路重复少；日后若多路需要，并入 `doCheck` 或 `doSearch`，不单开方法）  
- Skill 薄改；禁止模型连搜  
- **实现方法名以 §3.1 为准**  

**Related:**  
- [`2026-09-29-apify-sku-search-client-design.md`](./2026-09-29-apify-sku-search-client-design.md)  
- [`2026-10-01-agent-run-workspace-design.md`](./2026-10-01-agent-run-workspace-design.md)  
- `SkuSearchPort` / `SearchSkuToolHandler` / `ecommerce-picklist` / 后端 `pi-ai`  

---

## 1. Problem

正式上线选品时，单次关键词召回常偏窄；若让主 agent 自己扩词、连搜、重排：

1. 多份 `hits` 进入对话 → **上下文膨胀**  
2. Skill 变长 → 进一步挤占主窗口  
3. 行为不稳定  

需要更好的排序质量，同时 **不能**把重排推理塞进主 agent。Demo 先打通管线骨架与 LLM 重排；扩词 / 多路后置。

---

## 2. Goals / Non-goals

### Goals（P0 / Demo）

1. 一次 `search_sku` 走 `SkuSearcher#search`（步骤见 §3.1）。  
2. LLM 重排经 pi-ai（useCase 如 `ebus.sku.rerank`）；**不**进入 `AgentSession` 消息轴。  
3. Demo：`expandQuery` = 原词单元素列表；`doSearch` = **单路** `SkuSearchPort.search`。  
4. 回传足够主 agent 选 8–12（`pageSize` 上限，现网 max 20）。  
5. LLM 失败/超时 → **降级**为 `doCheck` 后（经 `pooling`）的顺序，再 `topHits`。  
6. 可观测：各步条数、重排耗时/失败（无密钥）。

### Non-goals（Demo）

- 真实多路扩词 / 并行多 query  
- 独立 `dedupe*` 方法  
- 主 agent 二次模型重排 / skill 内重排 prompt  
- 独立检索子 agent、向量中台  
- 改积分 / settle / 8–12 门槛  
- 强制 `recall.json` 落盘  
- `tbk` / 详情 enrichment / 第二平台  

---

## 3. 目标运行时形状

```text
主 agent（ecommerce-picklist）
  → 提炼一个 query
  → search_sku(query, platform, pageSize)
        → SkuSearcher.search(query, platform, pageSize)
              → expandQuery(query)              // Demo: [query]
              → doSearch(queries, …)            // Demo: 单路 Port
              → doCheck(hits)                   // 合法性过滤
              → pooling(hits)             // 见 §3.2：限制送入 LLM 的条数
              → rerank(intent, pool)            // pi-ai
              → topHits(ranked, pageSize)       // 最终回传条数
  → ToolResult.ok：{ hits: [ … ] }
  → 主 agent：写 artifact / view / final
```

### 3.1 实现方法名（钉死）

入口类：`com.xmut.ebus.application.business.agent.tool.sku.SkuSearcher`

| 方法 | 概念签名 | Demo 行为 | 日后 |
|------|----------|-----------|------|
| `search` | `List<SkuSearchHit> search(String query, String platform, int pageSize)` | 编排下列步骤；Handler **只调这个** | 不变 |
| `expandQuery` | `List<String> expandQuery(String query)` | `singletonList(trim(query))` | 规则扩词，最多 3 路 |
| `doSearch` | `List<SkuCandidate> doSearch(List<String> queries, String platform, int sourcePageSize)` | 只对 `queries.get(0)` 调一次 Port | 并行多路 |
| `doCheck` | `List<SkuCandidate> doCheck(List<SkuCandidate> hits)` | 丢空标题、非 https、违禁词 | 可顺带轻量去重 |
| `pooling` | `List<SkuCandidate> pooling(List<SkuCandidate> hits)` | 截到 `rerankPoolSize`（默认 40） | 同左 |
| `rerank` | `List<SkuCandidate> rerank(String intent, List<SkuCandidate> pool)` | pi-ai 重排；失败返回原 `pool` 序 | 同左 |
| `topHits` | `List<SkuSearchHit> topHits(List<SkuCandidate> ranked, int pageSize)` | 截断并映射为 `SkuSearchHit` | 同左 |

**已删除：** 独立 `dedupeCandidates` / 旧名 `planQueries`、`collectHits`、`rejectInvalid`、`rerankByLlm`、`takeTopHits`。

辅助类型：

- `SkuCandidate`：内部候选（短 `id`：`h1`…、hit 字段）  
- `SkuReranker`（接口）+ `ModelSkuReranker`（pi-ai）；恒等序用 `SkuReranker.identity()`，不单开 Passthrough 类；**不**注册为 Pi Tool  

Demo 伪代码：

```java
List<String> expandQuery(String query) {
  if (!StringUtils.hasText(query)) {
    return Collections.emptyList();
  }
  return Collections.singletonList(query.trim());
}

List<SkuCandidate> doSearch(List<String> queries, String platform, int sourcePageSize) {
  if (queries == null || queries.isEmpty()) {
    return Collections.emptyList();
  }
  List<SkuSearchHit> hits = skuSearchPort.search(queries.get(0), platform, sourcePageSize);
  return toCandidates(hits, "L0");
}
```

### 3.2 `pooling` 是干什么的？

**不是最终给用户的 Top-K**（那是 `topHits` / `pageSize`）。

| | `pooling` | `topHits` |
|--|-----------------|-----------|
| 时机 | **调用 LLM 重排之前** | **重排之后**、回传工具结果时 |
| 目的 | 限制**送进重排模型**的候选数，避免 prompt 过长、费用/延迟爆掉 | 限制**主 agent 看到**的条数 |
| 配置 | `rerankPoolSize`（默认 **40**） | 工具入参 `pageSize`（默认 10，max 20） |

例：`doSearch` 拉回 20～60 条 → `doCheck` 后还剩 35 条 → `pooling` 若上限 40 则原样送入 `rerank`；若日后多路合并到 80 条，则先截成 40 再让 LLM 排 → 最后 `topHits(..., 12)` 只把前 12 给主 agent。

Demo 单路 + `sourcePageSize=20` 时，池子往往已 ≤40，`pooling` 接近 no-op，但仍保留，避免日后开多路时 LLM 被上百条砸满。

---

## 4. 组件合同

### 4.1 工具入参

| 参数 | Demo 行为 |
|------|-----------|
| `query` | 必填；intent |
| `platform` | 透传 Port |
| `pageSize` | `topHits` 的 K |

### 4.2 `expandQuery` / `doSearch`

见 §3.1。扩词 / 多路 **禁止**进 SKILL。

### 4.3 `doCheck` / `pooling`

- **doCheck：** 空 title；非 https；配置违禁词。Demo 可不做 URL 去重；多路阶段若需要，在此方法内顺带做，**不**再拆方法。  
- **pooling：** 见 §3.2。

### 4.4 `rerank`

| 项 | 约定 |
|----|------|
| 模型 | 后端 pi-ai；`rerankUseCase` 默认 `ebus.sku.rerank` |
| 输入 | `intent` + `{id,title,price,category}`（URL 不进 prompt） |
| 输出 | 有序 `id[]`；非法 id 丢弃，缺的按原池序补尾 |
| 超时 / 失败 | 返回原 `pool` 序 + error 日志 |
| 禁止 | prompt 进 skill；主对话重排；改写 title / 编造 URL |

### 4.5 回传形状

`{ "hits": [ … ] }`：现网字段 + 可选 `rank`。空 → `failed("search_sku empty hits")`。

### 4.6 装配与配置

```text
SearchSkuToolHandler
  → SkuSearcher.search
       → expandQuery
       → doSearch
       → doCheck
       → pooling
       → rerank
       → topHits
```

| 键 | Demo 默认 |
|----|-----------|
| `enabled` | true |
| `maxLegs` | **1** |
| `sourcePageSize` | 20 |
| `rerankPoolSize` | 40 |
| `rerankUseCase` | `ebus.sku.rerank` |
| `rerankTimeoutMs` | 12000 |
| `exposeReasons` | false |

---

## 5. Skill 改动（极薄）

| 位置 | 改法 |
|------|------|
| 只调用一次 | 保留；服务端可能重排，禁止连搜 |
| `pageSize` | 「返回候选条数上限（重排后）」 |
| Quality | 「在工具返回的候选中」写质量字段 |

---

## 6. 分期

| 阶段 | 内容 |
|------|------|
| **Demo / P0** | §3.1 骨架 + 恒等 `expandQuery` + 单路 `doSearch` + `doCheck` + `pooling` + `rerank` + `topHits` + skill 薄改 + 单测 |
| **P0.5** | `expandQuery` 真扩词；`doSearch` 多腿；`doCheck` 内轻量去重 |
| **P0.6** | 可选 `recall.json` |
| **P1** | 约束入参；交叉编码器级联 |

---

## 7. 测试要点

1. `expandQuery(" 杯垫 ")` → `["杯垫"]`。  
2. `doSearch` 对 Port **只调一次**。  
3. `pooling`：输入 50 条、`rerankPoolSize=40` → 输出 40。  
4. `rerank` mock：序遵循 mock id。  
5. 重排失败 → `topHits` 仍 ≤ `pageSize`。  
6. CI 不打真 Apify / 真网 LLM。

---

## 8. 风险与接受

- Demo 无扩词/多路 → 召回面窄；靠 `rerank` 提升池内顺序。  
- `pooling` 过小会丢掉未进池的好品 → Demo 默认 40、单路 20 条通常够用。  
- 幻序：非法 id 过滤 + 原序补尾。  

---

## 9. Self-review

- [x] 方法名已按产品命名统一  
- [x] 已移除独立 dedupe；说明 `pooling` ≠ `topHits`  
- [x] Demo：不扩词、单路 `doSearch`  
- [x] LLM 重排不进主上下文  

---

## 10. Revision history

| Date | Note |
|------|------|
| 2026-10-01 | 初稿：规则粗排 |
| 2026-10-01 | 修订：LLM-as-reranker |
| 2026-10-01 | 修订：Pipeline 方法名第一版；Demo 单路 |
| 2026-10-01 | 修订：`expandQuery`/`doSearch`/`doCheck`/`rerank`/`topHits`；删除 dedupe；阐明 `pooling` |

| 2026-10-01 | 修订：`capRerankPool` → `pooling` |
| 2026-10-01 | 修订：命名 `SkuSearcher` / `ModelSkuReranker` / `SkuReranker.identity()` |
