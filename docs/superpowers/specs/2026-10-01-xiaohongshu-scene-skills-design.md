# 小红书种草场景：三技能近端设计

**Date:** 2026-10-01  
**Status:** accepted  
**Decision:**  
- 场景 `xiaohongshu` 近端上线 **三个独立计费技能**（方案 1）：选题清单、笔记种草稿、爆文拆解  
- 配图分镜 **内嵌**进笔记成稿，不单独做第四技能  
- 选题：`search_xhs_note` = **厚管线**（对标 `SkuSearcher`）；Apify **只做 Port**；管线失败后 skill 允许 `source=model_fallback`  
- 爆文：链接优先 **详情 Actor** 拉正文；失败必须粘贴正文，**禁止**无原文空拆  
- 交付形状对齐电商：workspace 写盘 + `{"output":"final.json"}` 指针 + `view`/`artifact` 双轨 + 可用成果落库后 settle  

**Related:**  
- [`2026-10-01-agent-run-workspace-design.md`](./2026-10-01-agent-run-workspace-design.md)  
- [`2026-10-01-sku-search-multi-recall-rerank-design.md`](./2026-10-01-sku-search-multi-recall-rerank-design.md)（选品厚管线样板）  
- [`2026-09-29-apify-sku-search-client-design.md`](./2026-09-29-apify-sku-search-client-design.md)（Apify Port 接线样板）  
- 电商对照：`ecommerce-picklist` / `SkuSearcher` / `ecommerce-skulist`  
- PRD FR-15/16/17；画廊卡文案：笔记结构与种草表达  

---

## 1. Problem

首页「小红书种草」现为 `COMING_SOON` 灰卡。电商场景已用「场景 → 工作台胶囊 → SKILL.md → 计费 Run」形状跑通选品与 Listing。需要在不改首页信息架构的前提下，为 `xiaohongshu` 定近端能力边界与技能合同，避免一上来做投流/自动发帖等超范围能力。

---

## 2. Goals / Non-goals

### Goals

1. 场景工作台三颗胶囊：选题清单、笔记种草稿、爆文拆解。  
2. 三个 skill 各自独立 billed run；成功合格成果落库后各扣 **1** 积分。  
3. 选题 `search_xhs_note` 对标 `search_sku`：主 agent 只调一次；扩词/校验/重排在服务端管线内完成；Port 可 `mock`/`apify`。  
4. 爆文可接链接拉正文；CI 不打真网。  
5. 可选弱串联：选题 / 爆文 → 笔记（交接提示），不强制。  
6. 超出能力时友好拉回（对齐 FR-16），闲聊不扣分。

### Non-goals（近端）

- 独立「配图分镜」技能、人设定位、评论区话术、系列日历  
- 自动发帖 / 私信 / 投流文案 API  
- 小红书官方开放平台登录或 cookie 池产品化  
- Listing 式策划→确认双段扣费（笔记一次成稿）  
- 强制从选题链接再抓正文做爆文（爆文输入以用户链接或粘贴为准）  
- 主 agent / skill 内扩词、连搜、二次重排（与选品同禁）  
- Demo 阶段真实多路扩词（`expandQuery` 先原词单元素，与选品 Demo 同）

---

## 3. 技能总览

| Skill id | 胶囊名 | 用户价值 | `persistAs` |
|----------|--------|----------|-------------|
| `xhs-topiclist` | 选题清单 | 从品类/商品产出 8–12 条可发选题 | `xhs_topiclist` |
| `xhs-note` | 笔记种草稿 | 标题备选 + 正文 + 标签 + 配图提示 | `xhs_note` |
| `xhs-break` | 爆文拆解 | 结构拆解 + 骨架 + 改写稿 | `xhs_break` |

Classpath：`scenes/xiaohongshu/{skill}/SKILL.md`（及 `references/output.md`）。

场景装包：`sceneCode=xiaohongshu` 必须能 resolve 上述三个 id；亮卡上线时 Catalog 将该场景改为 `AVAILABLE`。

---

## 4. 工作台与胶囊

示例句须可直接发送（无【占位】）：

| 胶囊 | 示例句 |
|------|--------|
| 选题清单 | `请帮我生成「厨房收纳」类小红书种草选题清单，面向租房党。` |
| 笔记种草稿 | `请为商品「硅胶沥水垫」写一篇小红书种草笔记，语气像真人分享。` |
| 爆文拆解 | `请拆解下面这篇笔记（分享链接或正文），并改写成我的商品「硅胶沥水垫」：…` |

拉回：近端拿手是这三颗；超出范围先接住意图再引导胶囊。

---

## 5. 交接

```text
选题清单 ──「写成笔记」──► 笔记种草稿
爆文拆解 ──「按骨架写笔记」──► 笔记种草稿
```

| 从 → 到 | 交接携带 | 笔记约束 |
|---------|----------|----------|
| 选题 → 笔记 | `topicId`（`tp-n`）、角度/钩子、可选 `sourceNoteUrl` | `artifact.topicItemId` 必填且一致；假设写入 `assumptions` |
| 爆文 → 笔记 | 骨架摘要、目标商品、改写要点 | 写入 `assumptions`；近端不强制 DB 关联 id |
| 口述笔记 | 无 | `topicItemId` 可省略 |

不做选题 ↔ 爆文互跳。

---

## 6. 计费与落库

| 技能 | settle 时机 | 失败 |
|------|-------------|------|
| 选题 | `final.json` 合格（含 `source=model_fallback`） | 结构不合格 → release |
| 笔记 | 成稿 `final.json` 合格 | 不合格 → release；无二次 HITL 扣费 |
| 爆文 | 拆解+改写稿合格 | 无原文（链接失败且无粘贴）→ 人话 Fail，不扣 |

`metadata.output`：`billing: true`，`requiresView: true`，`persistAs` 见 §3。  
写盘与指针结算复用 agent-run-workspace 设计。

历史筛选：`sceneCode=xiaohongshu` + 上述 `persistAs`。

---

## 7. 工具与检索管线

### 7.1 `search_xhs_note`（选题）= 厚管线，对标 `SkuSearcher`

**原则：** 工具对外合同薄（主 agent 只见 `query` + `pageSize`）；内部厚。Apify **不是**工具本身，只是 `doSearch` 的一种 `XhsNoteSearchPort` 实现。扩词 / 校验 / 重排 **禁止**写进 skill。

#### 外层（`xhs-topiclist` skill）

```text
提炼 1 个 query（假设写入 assumptions，勿先追问）
  → 只调用 search_xhs_note 一次（禁止并行 / 换词连搜）
  → 成功 hits：source=apify（或配置名等价），从候选写 8–12 条选题叙事
  → 工具失败 / 空 hits：允许 model_fallback（与选品 Fail 不同）；禁止假链
  → 写盘 final.json → 指针
```

主 agent **不**做检索式再排序；排序与合法链路由服务端完成；agent 负责钩子 / 角度 / 优先发等质量字段。

#### 内层（建议类名 `XhsNoteSearcher`）

装配对标选品：

```text
SearchXhsNoteToolHandler
  → XhsNoteSearcher.search(query, pageSize)
       → expandQuery(query)
       → doSearch(queries, …)      // 仅经 XhsNoteSearchPort
       → doCheck(hits)
       → pooling(hits)
       → rerank(intent, pool)      // 独立 pi-ai useCase，不进主对话
       → topHits(ranked, pageSize)
  → ToolResult：{ hits: [ … ] }
```

| 方法 | Demo 行为 | 日后 |
|------|-----------|------|
| `expandQuery` | 原词单元素列表 | 规则扩词，最多 3 路 |
| `doSearch` | 只对第一路调一次 Port | 并行多路 |
| `doCheck` | 丢空标题、无效/非 https 笔记链、违禁词 | 可顺带轻量去重 |
| `pooling` | 截到 `rerankPoolSize`（默认 40） | 同左 |
| `rerank` | pi-ai（如 `ebus.xhs.rerank`）；失败回原池序 | 同左 |
| `topHits` | 截断为 `pageSize`（建议 max 20） | 同左 |

`searcher.enabled=false` 时可退化成「直接 Port.search」（与 `SkuSearcher` 开关同构）。

#### Port / Apify

| 项 | 约定 |
|----|------|
| 接口 | `XhsNoteSearchPort`（mock / apify 可切换） |
| 默认 Actor | `opspilot.cc/xiaohongshu-keyword-search-scraper` |
| 配置 | 分立命名空间（如 `ebus.xhs-note-search.*`）：`client`、`actorId`、token、timeout、`searcher.*` |
| hits 字段（最小集） | 标题、摘要/正文片段、笔记 URL、可选互动数；供 agent 写选题与挂 `sourceNoteUrl` |
| 空结果 | Handler 返回 failed / empty；**skill** 决定是否 `model_fallback`（管线本身不编造笔记） |

CI：打桩 Port + reranker；不打真 Apify / 真网大模型。

### 7.2 `fetch_xhs_note`（爆文）— 详情拉取，非召回管线

按 URL 取 **一篇** 正文，不对标六步召回（无多候选可重排）。

| 项 | 约定 |
|----|------|
| 默认 Actor | `khadinakbar/xiaohongshu-note-detail-scraper` |
| 入参 | 笔记 URL / 分享短链 / noteId（优先完整分享链，常含 `xsec_token`） |
| 调用 | 有链接时 **至多 1 次**；已有粘贴正文则可不调 |
| 成功 | `source=apify`；`sourceTitle`/`sourceBody` 来自工具 |
| 失败 | **禁止**编造原文；人话提示改贴分享链或粘贴正文 → Fail |

与 `search_sku` / `search_xhs_note` 分立工具名与配置，避免电商场景误用。

---

## 8. 产出合同（字段）

交付：`artifact.json` + `view.json` → `final.json`；对话仅 `{"output":"final.json"}`。

### 8.1 `xhs-topiclist`

**artifact：** `title`、`disclaimer`（含字面量 `非实时平台全站行情`）、`assumptions?`、`source`（`apify`\|`model_fallback`）、`query`、`items[8–12]`。  

**items[]：** `id=tp-{n}`、`title`（全清单 1–2 条以 `【优先发】` 开头）、`hook`、`angle`、`whyFirst`、`risk`、`sourceNoteUrl?`（仅检索成功且 URL 来自工具 hits）。  

**质量：** ≥3 不同角度/人群；反同质凑数；有 hits 时勿臆造工具未返回的笔记；fallback 无假链。不必在 skill 内再做检索式筛选——管线已 check/rerank。  

**view：** mute `note` + 有序 `list`。

### 8.2 `xhs-note`

**artifact：** `title`、`topicItemId?`、`productName`、`titleOptions[3–5]`、`body`、`tags[5–10]`、`imageHints[3–5]`、`assumptions?`。  

**质量：** 真人分享感；不编造未提供功效/数据；一次成稿。  

**view：** 标题备选 + 正文 + 标签 + 配图提示（markdown 或 section）。

### 8.3 `xhs-break`

**artifact：** `source`（`apify`\|`paste`）、`sourceUrl?`、`sourceTitle`、`sourceBody`、`structure`、`skeleton`、`rewrite`、`targetProduct?`。  

**质量：** 无原文 → Fail；apify 正文必须来自 `fetch_xhs_note`；拆解须可对照原文。  

**view：** 上拆解要点，下骨架+改写。

---

## 9. 错误与边界

- 不宣称官方全站实时热榜或官方推广池。  
- 不二次/并行调用同一检索或详情工具；不在 skill 内扩词或二次重排。  
- 成功路径除指针（及爆文失败人话）外无闲聊。  
- 前端只展示；不直连 Apify / 大模型。  
- 积分只经 CreditLedger；仅可用成果落库后 settle。

---

## 10. 实现分期（建议，非本设计阻塞）

1. Skill 文案 + output.md + 场景装包 + Catalog 亮卡  
2. `XhsNoteSearchPort`（mock）+ `XhsNoteSearcher` 六步骨架 + `search_xhs_note` Handler；rerank 可先 identity  
3. Apify Port（关键词 Actor）+ 真 rerank useCase；`fetch_xhs_note` 详情客户端  
4. `persistAs` 扩展 + Computer 投影 + 工作台三胶囊与交接  
5. 真 Actor 试跑与 disclaimer / 失败文案打磨  

---

## 11. 已拍板决定（会话纪要）

| 主题 | 决定 |
|------|------|
| 近端技能集 | A（选题+笔记）+ 爆文拆解 |
| 拆分方式 | 方案 1：三独立胶囊 |
| 选题检索门禁 | B：失败可 model_fallback |
| 选题工具形态 | `search_xhs_note` = 厚管线对标 `SkuSearcher`；Apify 只做 Port |
| 搜索默认 Actor | A：`opspilot.cc/xiaohongshu-keyword-search-scraper` |
| 爆文链接 | A：链接优先；失败须粘贴，禁空拆 |
| 详情默认 Actor | B：`khadinakbar/xiaohongshu-note-detail-scraper` |
| 字段命名 | `source`（不用 `sourceMode`） |
