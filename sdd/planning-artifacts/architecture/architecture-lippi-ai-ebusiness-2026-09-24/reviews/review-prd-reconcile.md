---
title: PRD ↔ Architecture Spine reconcile
status: draft
created: 2026-09-24
updated: 2026-09-24
verdict: gaps
sources:
  - sdd/planning-artifacts/prds/prd-lippi-ai-ebusiness-2026-09-23/prd.md
  - sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md
  - sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-24/EXPERIENCE.md (brief: Agent chat + preview)
---

# Review: PRD reconcile against Architecture Spine

**Verdict: gaps** — core FR billing/auth/generation spine is bound; several quiet PRD rules (tone, FR business rules, NFR-1/4, §4.7 outs) never landed in an AD, Consistency Convention, Capability map row, or Deferred bullet.

**Method:** Walk PRD §1–9 + UX EXPERIENCE brief; for each durable requirement ask whether it appears in Spine AD-1..11, Consistency Conventions table, Capability → Architecture Map, or Deferred. Product success metrics / ops ownership that are not build-substrate are noted as out-of-scope for spine (not gaps).

---

## 1. What landed (aligned)

| PRD item | Where it landed |
| --- | --- |
| Scope: 网页自助选品 + Listing；积分；近端不含全链路 | Spine `scope` + Design Paradigm |
| FR-1 注册登录；未登录不可计费生成 | AD-8, Cap map |
| FR-2 套餐/积分展示（能力层） | Cap map → CreditLedger + web; AD-5/6 |
| FR-3 成功产出才扣 1；失败不扣 | AD-5 预占结算；AD-7 可用成果 |
| FR-5 月重置（周期锚点） | AD-5；Convention「订阅周期锚点」 |
| FR-6 升级；支付后置 | AD-9 手工改档；Deferred 支付/月费 |
| FR-7 ~8–12 候选 + 理由 | AD-7 |
| FR-8 品类模板存在 | AD-6 CatalogTemplate；Cap FR-7..8 |
| FR-9 Listing 文案/展示 + 主图 URL | AD-7；AD-9 OSS |
| FR-10 导出依赖媒体 URL | Cap FR-9..10；AD-9 |
| FR-11 重试再计费 | AD-7「重试视为新的计费动作」；Cap FR-11 |
| FR-12 历史；30天 vs 50条 | AD-6 HistoryQuery；Deferred 精确策略 |
| NFR-2 成本统计 | Convention「成本」；Cap NFR-2 |
| NFR-3 人话失败 | Convention「错误」REST/SSE |
| NFR-5 登录 + 频率限制 | AD-8；Cap NFR-5 |
| UJ Agent 壳 + 预览 | Cap「UJ Agent 壳 + 预览」；AD-4 SSE |
| UX：Agent 对话主入口 + 右侧预览 | Cap + sources UX；与 EXPERIENCE 一致 |
| 小程序后置；微信 OAuth/手机号后置 | Deferred；AD-8 |
| Open Q：月费数字；历史策略 | Deferred |
| binds: FR-1..12, NFR-1..5, UJ-1..3 | Frontmatter（声明绑定，但 NFR-1/4 无落地治理面） |

---

## 2. Quiet requirements — did not land

定义：**Quiet** = PRD 中可验证或会约束实现的表述，在 Spine 的 AD / Convention / Capability map / Deferred **均无对应句**（或仅有过弱暗示、不足以指导实现）。

### 2.1 Tone / product disclaimers（语气与承诺边界）

| ID | PRD 原文要点 | Gap |
| --- | --- | --- |
| T-1 | 愿景：「不承诺一定卖得出去」；反指标不承诺 GMV；近端非人工陪跑主交付 | 无 AD/Convention/Deferred。若需在协议/UI 固定 disclaimer，应挂 NFR-4 或产品文案 Convention；当前沉默。 |
| T-2 | 「准主业；目标感 月入约 1–2 万（非承诺）」 | 属成功指标/生意目标，可不进架构；**非 build gap**。记录以免误当成系统 KPI。 |

### 2.2 Functional rules（FR 细则未钉）

| ID | PRD | Gap |
| --- | --- | --- |
| Q-FR4 | **FR-4** 免费 20 / Pro 200 / Plus 600 | Cap 仅写「FR-3..5 扣分/额度/月重置」，**额度数字未写入**任何 AD/Convention/Deferred。实现者可能自拟档位。 |
| Q-FR5 | **FR-5** 上月剩余 **清零不结转** | Spine 写「月重置」与周期锚点，**未显式写清零/不结转**。易实现成结转余额。 |
| Q-FR8 | **FR-8** 三档 **均可使用全部已上线模板**；不按套餐解锁；运营上新 | CatalogTemplate 所有权在；**「不按套餐门控模板」业务规则未落地**。 |
| Q-FR9a | **FR-9** 产出含主图（或方案）、详情文案、展示说明；**不是**实体包装/纸箱设计 | AD-7 钉了持久化形状；**「非包装设计」排除边界未写**。 |
| Q-FR9b | **FR-9** v1 **国内通用一种**风格；不做淘宝/闲鱼/拼多多三套强适配（§4.7 亦列） | **未进 Deferred / AD**。与「明确不做」清单一并沉默。 |
| Q-FR11 | **FR-11** 可提交简短「**质量差**」反馈 | Cap/AD 只覆盖重试计费；**反馈采集/存储/谁读**无所有者、无 Deferred。 |
| Q-FR2 | **FR-2** 展示 **下次重置时间** | FR-2 能力行在；Convention 有时区/锚点，**未要求 API/UI 暴露 nextResetAt**。弱 quiet。 |

### 2.3 Non-functional（NFR）

| ID | PRD | Gap |
| --- | --- | --- |
| Q-NFR1 | **NFR-1** 选品理由可读、无明显违规胡编；Listing 可粘贴修改后上架；抽检比例待定 | Frontmatter binds NFR-1，但 **Cap map 无行、无 AD、无 Convention、Deferred 未列「抽检待定」**。质量底线无治理面。 |
| Q-NFR4 | **NFR-4** 避免明显违禁宣传；**协议声明 AI 生成须人工复核后再上架** | **完全未落地**（无协议/合规 AD、无 Identity/Legal 钩子、Deferred 无 ToS）。与 T-1 相关。 |
| — | **NFR-3** | 已在 Convention「错误」；Cap map 未单列——**不算 quiet**（Convention 即落地）。 |
| — | **NFR-2 / NFR-5** | 已落地。 |

### 2.4 Explicit out-of-scope（§4.7）未镜像到 Deferred

Spine Deferred 已覆盖：支付、小程序、OAuth/手机、云部署、历史策略、模型选型等。

**未出现在 Deferred（或等价 out-of-scope 列表）的 PRD「明确不做」：**

| ID | PRD §4.7 | Note |
| --- | --- | --- |
| Q-OUT1 | 询盘 / 客服 / 物流 / 收款自动化（终局后置） | 「收款」部分与 Deferred 支付重叠；**询盘/客服/物流**未写后置，易被故事误拉进 v1。 |
| Q-OUT2 | 出海平台深度适配（Shopify / 亚马逊等） | 未 Deferred。 |
| Q-OUT3 | 人工陪跑、代运营、效果对赌为主交付 | 产品边界；建议至少一句 Deferred/scope「非系统能力」。 |
| Q-OUT4 | 承诺销售额或 GMV | 与 T-1/NFR-4；未钉。 |
| Q-OUT5 | v1 三平台分风格 listing | 同 Q-FR9b；**应进 Deferred**。 |

（v1 小程序 — 已 Deferred，对齐。）

### 2.5 UX brief vs Spine

| Item | Status |
| --- | --- |
| Agent 对话为主入口 + 预览面板 | **Aligned**（Cap map + AD-4 + UX sources） |
| 快捷栏 / 落地页 → Agent 旅程细节 | UX EXPERIENCE 负责；Spine 不要求逐屏 AD — OK |

---

## 3. Capability map holes（binds 声明 vs 表）

Spine frontmatter `binds: [FR-1..FR-12, NFR-1..NFR-5, UJ-1..UJ-3]`，但 Capability 表 **未映射**：

- NFR-1
- NFR-3（虽有 Convention，表缺行）
- NFR-4

建议：要么补 Cap 行指向 Convention/未来 AD，要么从 binds 收窄并显式 Deferred，避免「声明已绑定、表上空洞」。

---

## 4. Severity triage（给修订用）

**P0 — 实现易做错或合规缺口**

1. Q-NFR4 — AI 人工复核协议 + 违禁规避无落点  
2. Q-FR5 — 清零不结转未写清  
3. Q-FR4 — 20/200/600 数字未钉  
4. Q-FR8 — 模板不按套餐解锁未钉  

**P1 — 功能半落地**

5. Q-FR11 — 「质量差」反馈无所有者  
6. Q-NFR1 — 质量底线/抽检无治理  
7. Q-FR9b / Q-OUT5 — 国内通用单风格、非三平台适配未 Deferred  

**P2 — 边界与语气**

8. Q-OUT1..4 — §4.7 其余「不做」未进 Deferred  
9. Q-FR9a — 非包装设计排除  
10. T-1 — 不承诺卖得出去 / GMV（可并入 NFR-4 协议文案）  
11. Q-FR2 — nextResetAt 弱项  

---

## 5. Suggested landing targets（非本评审执行）

| Quiet | Suggested home |
| --- | --- |
| FR-4 额度数字 | Convention 或 AD-5 附档位表 |
| FR-5 清零不结转 | AD-5 一句 |
| FR-8 模板全档通用 | AD-6 CatalogTemplate 规则或 Convention |
| FR-11 反馈 | Cap 行 → 新所有者或 Picklist/Listing 附属写；或 Deferred「反馈仅日志」 |
| NFR-1 | Cap + Convention「生成质检抽检待定」或 Deferred 抽检比例 |
| NFR-4 + T-1 | AD 或 Convention：上线须有 ToS/生成须复核文案；违禁过滤策略后置可 Deferred |
| §4.7 outs | Deferred 子弹 镜像 PRD 明确不做 |

---

## 6. Bottom line

- **Aligned:** 账号、预占积分、SSE Agent、选品/Listing 可用成果、历史、OSS、手工升级、防刷登录、成本计量、Agent+预览 UX。  
- **Gaps:** 额度数字与清零规则、模板门控规则、质量反馈、NFR-1/NFR-4、国内单风格与 §4.7 多项 outs 未进 Deferred——属 **quiet requirements**，修订 Spine 前实现易漂移。

**Reviewer recommendation:** 保持 paradigm 不变；补一短轮「PRD quiet → AD/Convention/Deferred」再进故事拆分。
