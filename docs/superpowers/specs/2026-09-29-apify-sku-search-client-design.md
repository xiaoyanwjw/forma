# Apify 淘宝搜品客户端（设计）

**Date:** 2026-09-29  
**Status:** accepted  
**Decision:**  
- 在现有 `SkuSearchPort` / `search_sku` 上增加 Apify 实现（方案 A）  
- 配置键：**`ebus.sku-search.client`** = `mock` \| `apify`（**不用** `mode`）；默认 `mock`  
- Actor：`zen-studio/taobao-search-scraper`  
- `client=apify` 时缺 Token / 调用失败 / 无可用 https hit → **回退 Mock + error 日志**（服务不崩）  
- 不实现淘宝客 `tbk`（可留扩展）；不接详情 enrichment / 1688  

**Related:**  
- `SkuSearchPort` / `MockSkuSearchClient` / `SearchSkuToolHandler`  
- 既有 deferred：淘宝客 Task 6（`docs/superpowers/plans/2026-09-27-picklist-search-sku.md`）— 本设计**不替代**官方联盟，仅提供免开联盟账号的可选数据源  
- 调研备选 Actor 见会话记录；近端仅淘宝搜索 Actor  

**Risks（接受）：** Apify 为第三方爬虫抽样，非联盟推广池；稳定性与平台 ToS 由运营侧自担；Skill 免责须如实表述。  

---

## 1. Problem

选品依赖 `search_sku`，当前仅 Mock（`mock.tbk.local` 链）。开通淘宝客联盟账号成本高；希望用不申请联盟的方式拿到可点的真实商品 https 链，同时开发机无密钥时仍能跑通。

---

## 2. Goals / Non-goals

### Goals

1. `ebus.sku-search.client=apify` 时，经 Apify 淘宝关键词搜索返回可映射的 `SkuSearchHit` 列表。  
2. 缺凭证或失败时回退 Mock，可观测、不拖垮进程。  
3. Tool 出参形状不变；前端零直连；密钥仅环境变量。  
4. Skill 免责与来源表述对齐「配置的检索客户端」，保留字面量 `非实时平台全站行情`。  

### Non-goals

- `client=tbk` 淘宝客真客户端（后续可加枚举值）  
- `taobao-detail-scraper` enrichment、拼多多 / 1688 Actor  
- 改选品条数门槛、积分、settle 语义  
- CI 打 Apify 真网  

---

## 3. Approach

**方案 A（采用）：** `ApifyTaobaoSkuSearchClient` 实现 `SkuSearchPort`；工厂按 `ebus.sku-search.client` 装配；`apify` 外包 Fallback 装饰器（失败 → Mock）。

不采用：独立搜品微服务；本地定时抓取缓存层。

---

## 4. Configuration & fallback

| 项 | 约定 |
|----|------|
| 选择器 | **`ebus.sku-search.client`** = `mock` \| `apify`（默认 `mock`） |
| Token | env `APIFY_TOKEN` |
| Actor | `ebus.sku-search.apify.actor-id`，默认 `zen-studio/taobao-search-scraper` |
| 超时 | `ebus.sku-search.apify.timeout-ms`，建议默认 `120000` |
| pageSize | tool 传入 → Actor `maxItems`，上限 ≤20 |

**回退：**

```text
client=apify
  ├─ 缺 APIFY_TOKEN → error 日志 (missing_token) → 使用 Mock
  ├─ 超时 / HTTP/Actor 错误 → error (timeout|http_error) → 本次 search 回退 Mock
  ├─ 映射后 0 条可用 https → error (empty_hits) → 本次 search 回退 Mock
  └─ 成功 → 返回映射 hits
client=mock → 始终 Mock
```

日志字段（禁止 Token）：`client`、`actorId`、`queryLen`、`hitCount`、原因枚举。

`.env.example` 仅占位，不写实密。

---

## 5. Field mapping & call sequence

### 5.1 Input

| `search_sku` | Actor |
|--------------|--------|
| `query` | `keyword` |
| `pageSize` | `maxItems` |
| `platform` | 近端**不**用于切换 Actor；命中行 `platform` 常量 **`taobao_apify`** |

`enrichWithDetails` / `fetchReviews`：默认 **false**。

### 5.2 Output → `SkuSearchHit`

| Hit 字段 | 来源 |
|----------|------|
| `platform` | `taobao_apify` |
| `title` | `titleOriginal` 优先，否则 `title` |
| `price` | `price` → 字符串 |
| `category` | 类目名，或 `"cat:" + id`，或 `""` |
| `detailUrl` | 绝对 `https` 的 `url`；否则**丢弃该行** |
| `rawRef` | `itemId` |

### 5.3 Sequence

```text
search_sku → SkuSearchPort.search
  → apify? run Actor → dataset items → map/filter → empty? Mock : hits
  → mock? MockSkuSearchClient
→ SearchSkuToolHandler 现有 JSON hits[] 不变
```

HTTP：Apify REST + Bearer；Java 8 可不引重 SDK。

---

## 6. Skill copy

- Tool / Boundaries：结果来自**配置的搜品客户端**（Mock / Apify 等），链接必须来自工具；不宣称联盟官方推广池。  
- Disclaimer **必须**含字面量：`非实时平台全站行情`。  
- 推荐整句方向：`候选基于配置的商品检索抽样与助手排序，非实时平台全站行情。点击可打开商品页核对。`  
- 更新 `ecommerce-picklist` 权威 SKILL + `references/output.md`，并同步 test mirrors。  

---

## 7. Testing & acceptance

| # | 验收 |
|---|------|
| 1 | 默认 `client=mock`，与现网一致 |
| 2 | `client=apify` + 有效 Token：Computer 出现非 mock 的 https 商品链 |
| 3 | 缺 Token / 超时 / 无可用 hit：回退 Mock，有枚举日志，进程不崩 |
| 4 | 单测覆盖映射与回退；CI 不打真网 |
| 5 | 密钥仅 env；前端零直连 |
| 6 | Disclaimer 含 `非实时平台全站行情` |

---

## 8. File map（实现时）

| 路径 | 职责 |
|------|------|
| `.../sku/ApifyTaobaoSkuSearchClient.java` | 调 Actor、映射 |
| `.../sku/FallbackSkuSearchClient.java`（或等价） | 失败回退 Mock |
| `.../sku/SkuSearchProperties.java` | `client` / apify.* |
| `EbusPiToolCatalogConfiguration`（或专属 `@Configuration`） | 按 `client` 装配 `SkuSearchPort` |
| `APP-META/.../.env.example` | 占位 |
| `ecommerce-picklist` SKILL + output + mirrors | 文案 |

清理注意：仓库若并存 `marketplace` 与 `sku` 包重复实现，本需求以 **`sku` 包 + 现配置引用** 为准，避免双注册。

---

## Spec self-review

- 配置名统一为 **`ebus.sku-search.client`**（非 mode）。  
- 回退、映射、Skill、验收一致；无 TBD。  
- TBK / 详情 Actor / 1688 明确 out of scope。  
