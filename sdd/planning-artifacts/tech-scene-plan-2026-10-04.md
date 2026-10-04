# 科技场景计划：科技速读

**日期：** 2026-10-04  
**状态：** draft  
**所属总路线：** `sdd/planning-artifacts/scene-product-plan-2026-10-04.md`（总表阶段 2.3）  
**本文件职责：** 只讲科技分类怎么做；第一张可用卡是 **科技速读**。

---

## 1. 这个场景是干什么的

科技分类要解决的不是「值不值买」，而是：

> 丢一个链接（或粘贴一段正文）——科技产品页、AI 技术文章、传统工程/技术文档——解析后交出一页能带走的摘要。

| 用户带着什么来 | 带走什么 |
|----------------|----------|
| 产品官网 / 发布页 | 这是什么、给谁、核心能力、别误解什么 |
| AI 模型 / 论文 / 科普文 | 主张是什么、怎么做的、局限、适不适合跟 |
| 传统技术文档 / RFC / 工程介绍 | 问题是什么、方案要点、适用边界 |

**不是：** 选型对比表、本周热点早报、竞品拆解矩阵（后置场景再做）。

---

## 2. 命名与画廊

| 项 | 取值 |
|----|------|
| 一级分类 | 科技 `tech` |
| 展示名 | **科技速读** |
| 场景码 | `tech_digest` |
| Skill id | `tech-digest` |
| 卡片短句 | 丢产品页、AI 文章或技术文档链接：解析正文，一页摘要带走。 |
| 近端状态 | `COMING_SOON`（阶段 0 已占位）→ 本计划做完后 `AVAILABLE` |

同分类后置（本文不实施）：

| 场景 | 和速读的差别 |
|------|----------------|
| 趋势早报 | 多条热点，要时效，容易编造 |
| 选型说明书 | 多个候选里帮你挑，要对比维度 |

---

## 3. 体验（工作台）

对齐电商 / 小红书：画廊亮卡 → `/scenes/tech_digest` → 提问门面 + 一颗胶囊。

| 胶囊 | 文案示意 |
|------|----------|
| 科技速读 | 例：「请速读这个链接，我关心它适不适合小团队用：https://…」 |

空态提示三选一即可：产品页 / AI 文 / 技术文档。超范围（要订票、要发帖）短聊拉回，不扣分。

Computer：GitHub-README 风格 HTML 一页纸，可复制带走。文末可选手递「帮我写成选型说明书」（后置，近端可不做按钮）。

---

## 4. 技术方案

原则：**复制 `xhs-break` 的形状**（链接或粘贴 → 至多拉 1 次 → artifact → `render_view` → 结算），不要新造运行时。

### 4.1 模块落点

| 层 | 做什么 | 不做什么 |
|----|--------|----------|
| `forma_scene` | 已有种子 `tech_digest` / `tech`；开放时改 `AVAILABLE` | 不新开表 |
| `forma-web` | 工作台 spec（`workspace/registry` 注册 `tech_digest`） | 不直连模型、不自己抓网页 |
| `forma-pi-extension` | 场景包 `scenes/tech_digest/tech-digest/`（目录第一段必须是画廊 `scene_code`，否则 `listByScene` 对不上） | 不进 `pi-agent` |
| 通用 tool | **`fetch_web_page`** + **`excerpt_chunks`**（内部 `ModelProvider.completeBatch`） | 不把长文塞进主会话；不代登 |

业务 tool 进 extension（Spine：不进 `pi-agent`）。

### 4.2 Skill 工作流（端到端）

对齐 `xhs-break`：**原文必须有出处；没有原文禁止摘要。**

```text
输入（链接或粘贴）
  → 1 判定原文从哪来
  → 2 取原文（仅链接且无粘贴时，fetch_web_page × 1）
  → 3 抽正文（HTML → 标题 + 纯文本）
  → 4 够不够长？不够 → Fail，请粘贴
  → 5 代码按段切片
  → 6 Skill 对每块单独调摘录（旁路小模型 / 独立 useCase，不进主对话上下文）
  → 7 主模型只拿全部摘录写一页摘要（条数不写死）
  → 8 write_file artifact.json
  → 9 render_view → view.json
  → 10 对话只回 {"output":"view.json"}；有可用 view 才结算
```

1. **判断原文从哪来**  
   - 已有可用粘贴正文 → `source=paste`，**不调**拉取。  
   - 只有链接 → `fetch_web_page` **至多 1 次**；成功 `source=fetch`。  
   - 都没有 → Fail，请贴链接或正文。  
   - 拉取失败 / 空正文 / 抽完太短 → Fail，请改贴或粘贴正文；**禁止编造原文再摘要**。
2. **按 output 合同写 artifact** → `write_file` `artifact.json`。要点必须能对照原文句子；数字/评测分原文没有就进 `uncertainties`，不许编。
3. **`render_view`** → `view.json`；对话终稿只回 `{"output":"view.json"}`。
4. **结算：** 仅可用 view 落库之后。拉链失败未出成果不扣。

---

### 4.3 调研：链接怎么变成「原文」

业界把这件事拆成两段，**不能混成「让大模型自己打开网页」**（浏览器不直连模型）。

| 段 | 做什么 | 谁做 |
|----|--------|------|
| A 取页 | 拿到渲染后的 HTML/正文 | 后端 tool（走 Apify，不走本机裸 GET） |
| B 抽正文 | 去掉导航/广告 | Actor 内 Readability / Markdown |
| C 切片 | 按标题/段落切成块 | **规则代码** |
| D 摘录 | **每块单独**选出原文句子（条数模型自定） | **旁路模型**（`forma.tech.excerpt`，对齐 SKU rerank：不走主会话） |
| E 总结 | 一页摘要（要点条数模型自定） | **主模型**，只吃标题 + 摘录，不吃各块全文 |

#### 为什么不用 OkHttp + Jsoup 当主路径

本机 GET **过不了**常见反爬（指纹、挑战页、空壳 SPA）和登录墙。Jsoup 只能解析已经拿到的 HTML。科技链接站点杂，失败率会很高。小红书拉笔记已经证明：难站走 **Apify Actor + 同一套 `ApifyActorTransport`**。

#### Apify 店里可比的 Actor

| Actor | 规模（店页口径） | 适不适合科技速读 |
|-------|------------------|------------------|
| **`apify/website-content-crawler`**（官方 WCC） | 约 16 万用户、4.6 分；专为 LLM/RAG 抽正文 | **采用。** Firefox/自适应浏览器、代理、指纹；输出 `text` / `markdown`；可用 Cookie 打开「已登录」页 |
| `apify/url-to-markdown` | 官方、单 URL→Markdown；HTTP 或浏览器 | 更轻，但抗封能力弱于 WCC；可当以后的便宜档 |
| `morph_coder/web-content-extractor` | Cheerio→代理→Playwright 四级回退 + Readability | 形状合适，第三方维护；不如官方 WCC 稳 |
| `erng/general-web-scraper` | Playwright/Cheerio + Readability | 用户少；不优先 |
| 现有 `khadinakbar/xiaohongshu-note-detail-scraper` | 仅小红书笔记 | **不能**拿来拉任意科技站 |

Jina Reader 一类托管「URL→Markdown」也能抗一部分 JS，但链接出站、和现有 Apify Token 栈不一致，不作为默认。

**结论：** `fetch_web_page` 的 **apify client** 调 **`apify/website-content-crawler`**；无 Token 时 **mock**（与 SKU/XHS 相同）。粘贴正文仍是失败兜底，不是「先本机再 Apify」双通道默认（避免两套抽正文对不齐）。

官方说明（调研当日店页）：

- 可「用无头 Firefox 或裸 HTTP」；「用浏览器指纹和代理绕过反爬」；「通过提供 cookies 爬登录后的页」（[apify/website-content-crawler](https://apify.com/apify/website-content-crawler)）。
- 默认会**顺着子链爬整站**。科技速读只要当前这一页：必须把深度打到 0。
- 单 URL 场景他们也提到 WCC 偏「整站」；我们仍选它，是因为抗封 + 清洗正文比 `url-to-markdown` 完整，并用输入把爬行锁死在 1 页。
- **登录边界（产品）：** Actor 支持 `initialCookies`，**不是**用户把账号密码交给 Forma 去代登。近端不做 Cookie 采集（隐私/盗号风险）。付费墙、必须登录才出正文 → 请用户**粘贴**。反爬（公开页打不开）才靠浏览器 + 代理。

---

### 4.4 `fetch_web_page`：取文逐步（Apify 主路径，Agent 只调一次）

工具入参：`url`。出参：`ok`、`finalUrl`、`title`、`text`、`extractMethod=apify`、失败时 `errorCode`。

**硬规则：** 单次 Skill 至多调用 1 次；失败不自动换 Actor、不换 URL 重试。

#### 步骤 A — 守卫（本机，不发 Apify）

1. 仅 `http`/`https`；拒绝 `file:`、`javascript:`、localhost / 明显内网（防 SSRF，也不要把内网打到云上）。  
2. URL 长度上限约 2048。非法 → `bad_url`。

#### 步骤 B — 无 Token → mock

3. `forma.web-fetch.client=apify` 且 `APIFY_TOKEN` 空 → mock fixture（形状与真拉一致）。测试/本地无 Key 不扣外部费用。

#### 步骤 C — 调 WCC（有 Token）

4. 复用 `ApifyActorTransport.post`。  
5. **锁死单页**（避免账单失控）：

```json
{
  "startUrls": [{ "url": "<用户链接>" }],
  "maxCrawlDepth": 0,
  "maxCrawlPages": 1,
  "maxResults": 1,
  "crawlerType": "playwright:firefox",
  "proxyConfiguration": { "useApifyProxy": true },
  "saveMarkdown": true,
  "useSitemaps": false
}
```

| 字段 | 为什么 |
|------|--------|
| `maxCrawlDepth: 0` | 只爬 Start URL，不跟站内链接 |
| `crawlerType: playwright:firefox` | 店页写明抗封更好；比 Cheerio 贵、慢，换可靠 |
| 以后可改 `playwright:adaptive` | 静态页走 HTTP，动态页再开浏览器，省钱 |
| `saveMarkdown: true` | dataset 有 `markdown`；正文优先 `text`，没有再用 markdown |

6. 超时建议 **120s**（与 `xhs-note-fetch` 同量级；浏览器 Actor 比 Cheerio 慢）。  
7. 映射 dataset 第一条：`url`→`finalUrl`，`metadata.title` 或页面 title→`title`，`text`（空则 `markdown`）→`text`。  
8. 截断仍 **32_000** 字符；`truncated=true` 时摘要写「原文已截断」。  
9. 去掉空白 **< 400 字符** → `empty_body`（登录墙、挑战页、抽失败）。

#### 步骤 D — 失败对人话

| errorCode | 用户看到 |
|-----------|----------|
| `bad_url` | 请贴以 https 开头的公开链接 |
| `missing_token`（仅当误配成强制真拉） | 本地无 Key 应走 mock；生产缺 Token 配环境变量 |
| `apify_error` / `timeout` | 打不开或太慢，请换链或直接粘贴正文 |
| `empty_body` | 这页几乎没有可读正文（登录墙/反爬仍拦），请粘贴你看到的那段 |
| `unsupported_type` | 暂不支持当 PDF 附件解析（WCC 可下文件，近端不做） |

**明确不做：** 用户密码代登；无头浏览器跑在 Forma 自己的 JVM 里；Cheerio 失败再自动开第二次 Actor（一次调用内由 WCC 自己选引擎即可，Skill 层不套两轮）。

---

### 4.5 解析：每块旁路摘句，主模型只做总结

长文不要塞进**主对话模型**（工作台那一轮的上下文会被撑满）。采用：

> 代码切块 → **Skill 一次把块列表交给 `excerpt_chunks`**（旁路小模型、`completeBatch` 每块独立窗口）→ 摘录条数、最后要点条数都 **由模型自己定** → **主模型只看见摘录** 写一页摘要。

短文也可以只切 1 块，仍走摘录工具再总结。

#### 谁走哪颗模型

对齐测款 `forma.sku.rerank`：tool 内部 `ModelProvider.complete` / **`completeBatch`**（`useCase=forma.tech.excerpt`），**不写入** Agent 主会话。

`completeBatch` 是 pi-ai 端口：结果与请求下标对齐；共享线程池并行 `complete`（限流/重试仍按条生效）。近端不接厂商离线 Batch Job；以后真 Batch API 可覆盖该方法。禁止把 N 块拼成一条 `complete`。

| 步骤 | 调用方式 | 模型 | 窗口里有什么 |
|------|----------|------|----------------|
| 切块 | 代码 `TechDigestSourcePrep` | 无 | — |
| **每块摘录** | Skill **一次** `excerpt_chunks`（块列表） | 旁路 `forma.tech.excerpt`，经 **`completeBatch`** | 每条请求 **只有一块**；主会话只有这一次 tool 进出 |
| **总结** | 主 Agent 一轮 | 主模型 | 标题 + 全部 quotes，**没有**各块全文 |

禁止把多块正文拼进主模型的一条消息。摘录须为该块连续子串。**12 是切片次数上限，不是摘要条数上限。**

#### 切片规则（确定性，仍是代码）

在 extension 里做 `TechDigestSourcePrep`（拉页成功后、以及用户粘贴后都要跑）。

1. 按 Markdown/纯文本的标题行切开；没有标题就按空行分段。  
2. 过短段（< 40 字）并入上一段。  
3. 相邻段打包：每块大约 **800～1500 字**，重叠约 **80 字**。  
4. 最多 **12 块**；超出则头/中/尾都覆盖，并写「原文过长，只覆盖了部分段落」。  
5. `pre` / 代码块不要从中间切开。

#### 摘录（Skill 一次交块列表 + 代码核对）

Tool `excerpt_chunks` 入参：`chunks[]`（每项 `heading` + `text`）。出参：与入参对齐的 `excerpts[]`。

实现：为每块组一条 `ModelRequest`（窗口里只有该块），`completeBatch`；每条响应里的 `quotes[]` **条数不规定**。

- 从本块 **原样复制** 值得留下的句子。  
- 禁止改写、拼接、用块外知识。  
- 代码：非该块子串的 quote 丢掉；全空则用该块第一句完整句保底。

产出：`excerpts[] = { heading, quotes[] }`。主会话只进这一份列表，不进各块全文。

#### 总结（仅主模型）

只看到：`title` + `excerpts` + 可选 `concern`。**不要**再附全文。

| 步 | 规则 |
|----|------|
| 1 一句话 | 根据摘录概括原文在说什么，不是评测「值不值买」 |
| 2 要点 | **条数不写死**；每条仍应能指回某一 quote |
| 3 适合谁 | 摘录里没说受众 → 「原文未说明」 |
| 4 用户关心 | 用摘录回答；没覆盖就明说 |
| 5 出处 | `sourceUrl` 用工具 `finalUrl` |

**禁止：** 二次 `fetch_web_page`；把各块全文送进主模型；跳过子串核对；编跑分。

Computer 展示要点 + 原文摘录（太多可折叠，不卡死条数）。标明「AI 摘要，请对照原文」。

---

### 4.6 输出合同（Computer 字段）

artifact 建议字段（实现时落 `references/output.md`）：

| 字段 | 说明 |
|------|------|
| `title` | 页标题或自拟短标题 |
| `oneLiner` | 一句话：这是什么 |
| `points[]` | 要点，**条数不限**，模型自定；须能对照摘录 |
| `excerpts[]` | 切片摘录：`heading` + `quotes[]`（原文子串）；短文可只有一块 |
| `forWhom` | 适合谁看 / 不适合谁（各一句即可） |
| `uncertainties[]` | 原文没说清、需人工核实；过长被抽块也写这里 |
| `sourceUrl` | 有则必填 |
| `source` | paste / fetch |
| `concern` | 用户说的「我关心…」，没有则空 |

view：标题 + 一句话 + 要点 + 原文摘录 + 适合谁 + 需核实 + 原文链接。摘录/要点都不卡死条数（太多时可折叠）。标明「AI 摘要，请对照原文」。

### 4.7 样例输入（验收用）

开工前准备 4 份 fixture（不必真打外网）：

1. 科技产品发布页（或粘贴）  
2. AI 模型/框架介绍  
3. 传统技术文档长文（用来验切片摘录，不能只验短页）  
4. 坏例：空输入、死链、明显不是科技的美食文（友好拉回）

---

## 5. 实施切片（本场景自己的阶段）

不绑死日历；做完一块再下一块。

| 切片 | 产出 | 完成标准 |
|------|------|----------|
| **T0 规格** | 本文件冻结 + `output.md` 假数据样例 | 产品/实现对字段无争议 |
| **T1 包骨架** | `scenes/tech_digest/tech-digest/`：SKILL、launch、mustache、静态样例能投影 | 不接 Agent 也能看出 Computer 长什么样 |
| **T2 拉文 + 切片 + 摘录 tool** | WCC 单页；切块；`excerpt_chunks` + `completeBatch` + 子串核对 | 主模型不见块全文；批内每块独立窗口 |
| **T3 工作台** | 前端 spec + 胶囊；画廊仍灰卡 | 点灰卡仍 toast；本地可进路由调试 |
| **T4 跑通** | Agent 一条路径：粘贴正文出 view | 结算点正确；失败不扣 |
| **T5 拉链** | 真 URL 成功 1 例；失败走粘贴提示 | 不编造 |
| **T6 开放** | 种子 `AVAILABLE`；画廊科技亮卡 | 登录 → 科技速读 → Computer |

**T0～T1 可与总路线阶段 1（打磨电商/小红书）并行。T4 起建议总路线阶段 1 出口后再做，避免两条半成品。**

---

## 6. 风险

| 风险 | 做法 |
|------|------|
| 反爬 / JS 空壳 | Apify Firefox + 代理；仍失败则粘贴 |
| 必须登录 / 付费墙 | 不代登、不收 Cookie；请粘贴可见正文 |
| 摘要胡编数据 | 每块旁路摘句 + 子串核对；主模型只根据摘录总结 |
| 版权大段照抄 | Computer 是摘要 + 要点，不整页转载 |
| 和法律/医疗/投资建议搅在一起 | 科技场景不做投顾/医嘱；超范围拉回 |
| 和「产品解读」旧名混淆 | 对外只用「科技速读」；旧码 `tech_product` 仅兼容删除种子 |

---

## 7. 做完长什么样（对外一句话）

打开 **科技 → 科技速读**，丢链接或粘贴，右侧 Computer 出现一页摘要：一句话、要点、适合谁、原文在哪。失败时请你改贴，不扣积分。

---

## 8. 建议的下一步

编码任务清单（给执行 Agent）：[`.artifacts/plan/2026-10-04_17-10-00_tech-digest.md`](../../.artifacts/plan/2026-10-04_17-10-00_tech-digest.md)。

1. 按该实现计划从 T001 起做；`completeBatch` 已在 `pi-ai`，不要重写。  
2. 亮卡（T6）前画廊保持 `COMING_SOON`；可用 `/scenes/tech_digest` 直进调试。
