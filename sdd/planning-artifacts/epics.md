---
stepsCompleted: ["step-01-extract-requirements", "step-02-design-epics", "step-03-create-stories", "step-04-final-validation"]
status: ready-for-development
inputDocuments:
  - sdd/planning-artifacts/prds/prd-lippi-ai-ebusiness-2026-09-23/prd.md
  - sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md
  - sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-24/DESIGN.md
  - sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-24/EXPERIENCE.md
---

# lippi-ai-ebusiness - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for lippi-ai-ebusiness (产品品牌 **Adam**), decomposing the requirements from the PRD, UX Design if it exists, and Architecture requirements into implementable stories.

## Requirements Inventory

### Functional Requirements

FR1: 用户可以注册并登录后使用生成能力；未登录不能消耗积分完成生成；登录态可见剩余积分与套餐档。
FR2: 用户可以查看当前套餐（免费/Pro/Plus）、本月剩余积分、下次重置时间。
FR3: 成功产出可用选品清单或 Listing 套装时各扣 1 积分；积分不足不能开始；失败且无可用结果不扣分或退回。
FR4: 三档月额度——免费 20 / Pro 200 / Plus 600。
FR5: 订阅周期到点按档位重发积分；上月剩余清零不结转。
FR6: 用户可从免费升级到 Pro 或 Plus（月费数字待成本实测；近端可用手工/管理改档联调）。
FR7: 用户选择品类模板（及简单输入）后生成选品清单；约 8–12 条候选，每条含可读可卖理由；结果可进历史。
FR8: 用户可选用不同已上线品类模板；三档套餐均可使用全部已上线模板；不按套餐解锁。
FR9: 用户基于候选或自填信息生成 Listing 套装（主图 + 详情文案/展示说明）；国内通用风格；非包装设计。
FR10: 用户可将 Listing 套装下载图片或复制文案以便上架。
FR11: 用户可对不满意结果重试（再扣分）或提交简短「质量差」反馈。
FR12: 用户可查看本人近 60 天内的选品清单与 Listing 套装。

### NonFunctional Requirements

NFR1: 选品理由可读、无明显违规胡编；Listing 文案可粘贴修改后上架（抽检比例待定）。
NFR2: 须能统计单次选品/单次 Listing 的模型成本，支撑定价覆盖成本。
NFR3: 失败时给人话原因（额度不足/服务繁忙/输入不完整等）。
NFR4: 避免明显违禁宣传；协议声明 AI 生成须人工复核后再上架。
NFR5: v1 轻量防刷——登录 + 异常频率限制。

### Additional Requirements

- 绿色场脚手架：扁平多模块 Maven（`lippi-ai-ebus-*`）+ `lippi-ai-ebus-web`；Pi 拷贝模块名为 `lippi-pi-ai` / `lippi-pi-agent`；包根 `com.xmut.ebus`（AD-3/AD-11/AD-13）。
- 部署：`APP-META/docker-config` + `bootstrap`；compose 至少 starter + MySQL；本地可 OSS/模型桩（AD-10）。
- 计费生成走 SSE；闭合事件名；浏览器 fetch+ReadableStream+JWT，不用 EventSource（AD-4）。
- 仅 CreditLedger 可变积分；预占→可用成果落库后结算；禁止 SSE 结束即扣分（AD-5/AD-7）。
- 领域所有权表强制分家：Identity / CreditLedger / AgentRuntime / CatalogTemplate / PicklistArtifact / ListingArtifact / MediaStore / Feedback / HistoryQuery（AD-6）。
- GenerationRun 关联 hold+session+artifact；重试=新 Run+新预占；历史成功记录不覆盖（AD-7）。
- 认证：用户名/邮箱+密码 + JWT；频率限制在 interfaces（AD-8）。
- 图片仅阿里云 OSS；Listing 存 `mediaObjectId`；支付网关 v1 不做，改档手工（AD-9）。
- 业务 ID：UUID 字符串（AD-12）。
- NFR2 由 Story 2.5 覆盖（GenerationRun 成本计量）；NFR4 由 Story 1.7 覆盖（协议/人工复核声明）。
- 近端非目标：询盘/客服/物流/收款自动化、出海平台、人工陪跑主交付、GMV 承诺、小程序、三平台分风格 Listing、微信/支付宝支付。

### UX Design Requirements

UX-DR1: 落地视觉：冷荧光纸色底 + 真黑字 + 店章红（#E11D48）唯一大胆色；禁用奶油陶土/紫霓虹/薄荷绿空壳风。
UX-DR2: 字体：Space Grotesk（标题与数字）+ Noto Sans SC（中文正文）；标题字距收紧、左对齐。
UX-DR3: 落地页：品牌 Adam + 清单实物感并置；第一眼看到交付物（清单），禁用全大写 eyebrow、每段 fade-up、按钮尾「→」。
UX-DR4: 工具页布局：工作板与列表，不用三列同款圆角卡片墙；主按钮实心店章红、小圆角 2–4px。
UX-DR5: 套餐展示用价目表行，不用三张孪生套餐卡。
UX-DR6: IA——落地页进入 Agent；中间会话区为主入口；输入框上方快捷栏（选品清单 / 生成上架素材）。
UX-DR7: 右侧预览面板展示生成后的清单或 Listing；左侧提供会话历史 + 套餐入口。
UX-DR8: Agent 壳与预览行为对齐 `mockups/app.html`（点快捷栏即可看到预览）；品牌展示名 Adam。

### FR Coverage Map

FR1: Epic 1 - 注册登录与鉴权
FR2: Epic 1 - 套餐/积分/重置时间展示
FR3: Epic 1 - CreditLedger 预占/结算/释放（Epic 2/3 接线扣分）
FR4: Epic 1 - 三档月额度 20/200/600
FR5: Epic 1 - 月重置清零不结转
FR6: Epic 1 - 手工改档升级 Pro/Plus
FR7: Epic 2 - 选品清单生成（约 8–12 带理由）
FR8: Epic 2 - 品类模板选用（三档通用）
FR9: Epic 3 - Listing 套装生成（主图+文案/展示）
FR10: Epic 3 - 下载图片 / 复制文案
FR11: Epic 4 - 重试与质量差反馈
FR12: Epic 4 - 近 60 天生成历史

## Epic List

### Epic 1: 进入 Adam，看清积分与套餐
用户能注册登录，看到免费/Pro/Plus、剩余积分与下次重置；不足时知道要升级；近端可手工改档。落地绿色场脚手架、JWT、CreditLedger、频率限制，以及落地页/套餐价目表视觉。
**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6
**Also:** NFR4 → Story 1.7

### Epic 2: 对话生成选品清单
用户在 Agent 里选模板、点「选品清单」，流式看到约 8–12 条带理由候选；成功扣 1 分；失败不扣。落地 GenerationRun+SSE、Pi AgentSession、品类模板，以及 Agent 壳与右侧预览。
**FRs covered:** FR7, FR8
**Also:** NFR2 → Story 2.5

### Epic 3: 生成并带走 Listing 套装
用户从候选或自填商品生成主图+详情文案；下载图/复制文案去上架；成功扣 1 分。落地 ListingArtifact、MediaStore/OSS 与导出。
**FRs covered:** FR9, FR10

### Epic 4: 重试、吐槽与回看历史
不满意可重试（再扣分、旧成果保留）；可提「质量差」；能查近 60 天本人选品与 Listing。
**FRs covered:** FR11, FR12

## Epic 1: 进入 Adam，看清积分与套餐

用户能注册登录，看到免费/Pro/Plus、剩余积分与下次重置；不足时知道要升级；近端可手工改档。落地绿色场脚手架、JWT、CreditLedger、频率限制，以及落地页/套餐价目表视觉。

### Story 1.1: 可跑的仓库与本地 Docker

As a 开发者（为后续用户交付铺路）,
I want Maven 扁平多模块 + Vue 壳 + APP-META compose（starter+MySQL）以及 vendor-copy 的 `lippi-pi-ai`/`lippi-pi-agent`,
So that 本地能一键起后端环境，后续故事有统一承载体。

**Acceptance Criteria:**

**Given** 仓库尚无业务脚手架
**When** 落地 parent 与平铺的 `lippi-ai-ebus-*`、`lippi-pi-ai`、`lippi-pi-agent`、`lippi-ai-ebus-web`（无 `backend/` 包一层），Java 包根 `com.xmut.ebus`
**Then** `mvn -pl lippi-ai-ebus-starter -am` 可编译；模块依赖方向符合 AD-11
**And** `APP-META/docker-config` 的 compose 至少拉起 starter + MySQL
**And** Pi 模块不依赖 LIMS 发版构件，命名为 `lippi-pi-ai` / `lippi-pi-agent`

### Story 1.2: 注册与登录拿 JWT

As a 一人店主,
I want 用用户名或邮箱 + 密码注册并登录,
So that 我能进入工具，且未登录时无法消耗积分生成。

**Acceptance Criteria:**

**Given** 服务已可启动（Story 1.1）
**When** 用户提交合法注册信息并登录
**Then** 系统签发 JWT；后续 API 可用 `Authorization: Bearer` 访问需登录接口
**And** 未带有效 JWT 时，任何计费生成入口返回未授权，且不扣积分
**And** 异常高频请求在 interfaces 层被限制（NFR5）；密码不明文落库
**And** 业务主键为 UUID 字符串（AD-12）

### Story 1.3: 积分账本（额度 / 预占 / 结算 / 月重置）

As a 一人店主,
I want 系统按套餐管理我的积分余额，并在生成成功时才真正扣分,
So that 额度公平、失败不白扣、不会被乱用。

**Acceptance Criteria:**

**Given** 用户已登录且归属某套餐档
**When** 查询或初始化额度
**Then** 免费 20 / Pro 200 / Plus 600 与档位一致（FR4）
**And** 仅 CreditLedger 可写余额；提供检查→预占 1→可用成果落库后结算，否则释放预占（FR3/AD-5）
**And** 订阅周期到点按档重发，上月剩余清零不结转（FR5）
**And** Agent 工具与前端均无法直接改积分表

### Story 1.4: 登录后看见套餐与积分

As a 一人店主,
I want 登录后立刻看到当前套餐、本月剩余积分和下次重置时间,
So that 我知道还能生成几次、要不要升级。

**Acceptance Criteria:**

**Given** 用户已登录且账本有档位与余额（Story 1.2–1.3）
**When** 打开工具或账户/套餐入口
**Then** 展示套餐档（免费/Pro/Plus）、剩余积分、下次重置时间（FR2）
**And** 套餐呈现为价目表行，而非三张孪生卡（UX-DR5）
**And** 积分不足时人话提示可升级或等待重置（NFR3）

### Story 1.5: 手工改档升级

As a 运营/管理员（联调近端付费）,
I want 将用户从免费改为 Pro 或 Plus 并刷新额度,
So that 用户升级后能马上按新档使用（无需微信/支付宝）。

**Acceptance Criteria:**

**Given** 目标用户当前为免费档
**When** 经管理入口或受控脚本改档为 Pro 或 Plus
**Then** 用户可见新套餐档与对应月额度（FR6）
**And** 不接入微信支付/支付宝（AD-9）
**And** 改档可审计（谁/何时/从→到）

### Story 1.6: Adam 落地页（品牌 + 清单感）

As a 潜在用户,
I want 打开落地页一眼认出 Adam，并看到「选品清单」交付物感,
So that 我愿意点进工具开始用。

**Acceptance Criteria:**

**Given** 前端工程已存在（Story 1.1）
**When** 访问落地页
**Then** 品牌 Adam 为英雄级信号；清单实物感并置；第一眼看到交付物（UX-DR3/UX-DR8）
**And** 视觉：冷荧光纸色 + 真黑字 + 店章红 #E11D48；字体 Space Grotesk + Noto Sans SC（UX-DR1/UX-DR2）
**And** 禁用奶油陶土/紫霓虹/薄荷绿空壳风；无全大写 eyebrow、按钮尾「→」堆砌（UX-DR1/UX-DR3）
**And** 有明确 CTA 进入登录或 Agent 工具

### Story 1.7: 协议声明与合规提示（AI 须人工复核）

As a 一人店主,
I want 在注册或进入工具前看到「AI 生成须人工复核后再上架」等协议说明,
So that 我清楚责任边界，产品也满足基础合规（NFR4）。

**Acceptance Criteria:**

**Given** 用户访问落地页、注册页或首次进入工具
**When** 完成注册或开始使用生成能力前
**Then** 可见协议/声明：AI 生成内容须人工复核后再上架；不承诺销售效果
**And** 文案避免引导明显违禁宣传；用户须确认已知悉（勾选或等效明示）方可注册或首次生成
**And** 不引入完整法务 CMS；近端静态文案即可

## Epic 2: 对话生成选品清单

用户在 Agent 里选模板、点「选品清单」，流式看到约 8–12 条带理由候选；成功扣 1 分；失败不扣。落地 GenerationRun+SSE、Pi AgentSession、品类模板，以及 Agent 壳与右侧预览。

### Story 2.1: GenerationRun 与 SSE 事件骨架

As a 一人店主,
I want 生成过程以流式事件推送，并有一次完整的 GenerationRun 记录,
So that 我能看见进度，且扣分与成果可追溯。

**Acceptance Criteria:**

**Given** 用户已登录且具备 JWT
**When** 启动一次计费生成回合（可先用桩/空跑）
**Then** 创建新的 `GenerationRun`，关联 holdId、sessionId、artifact 引用位（AD-7）
**And** SSE 仅使用闭合事件名：`run_started` | `message_delta` | `tool_started` | `tool_finished` | `artifact_ready` | `run_failed` | `run_settled`（AD-4）
**And** 客户端契约为 fetch + ReadableStream + Authorization；不要求原生 EventSource
**And** 禁止仅因流结束而结算积分

### Story 2.2: 品类模板（三档通用）

As a 一人店主,
I want 选用已上线的品类模板（如家居、电子）,
So that 生成选品时风格与字段受模板约束，且不因套餐被锁模板。

**Acceptance Criteria:**

**Given** 运营已配置至少 1 个已上线品类模板
**When** 任意套餐档（免费/Pro/Plus）用户查询可用模板
**Then** 返回全部已上线模板，不按套餐解锁（FR8）
**And** CatalogTemplate 为唯一写者；模板有稳定 `templateId`（UUID）
**And** 未上线模板不对用户可见

### Story 2.3: Agent 对话壳与右侧预览

As a 一人店主,
I want 在网页中间对话、上方快捷栏、右侧预览清单结果,
So that 我一眼能操作「选品清单」并看到产出。

**Acceptance Criteria:**

**Given** 用户已登录
**When** 进入 Agent 工具页
**Then** IA 为：中间会话区、输入框上方快捷栏（选品清单 / 生成上架素材）、右侧预览、左侧会话历史+套餐入口（UX-DR6/UX-DR7）
**And** 布局为工作板与列表，非三列同款圆角卡片墙；主按钮店章红、小圆角 2–4px（UX-DR4）
**And** 行为对齐 `mockups/app.html`：点快捷栏可驱动预览区（UX-DR8）
**And** 浏览器不持有模型密钥、不直接改积分

### Story 2.4: 生成选品清单并结算 1 积分

As a 一人店主,
I want 选模板后生成一份带理由的选品清单,
So that 我知道可以卖什么，并只在成功时扣 1 积分。

**Acceptance Criteria:**

**Given** 用户已登录、积分充足、已选 `templateId`；SSE/Run 与模板可用（2.1–2.3）
**When** 经快捷栏或对话发起选品生成（AgentSession + 工具）
**Then** 成功时持久化约 8–12 条候选，每条含可读可卖理由；清单必含 `templateId`（FR7）
**And** 成果挂到当前 `GenerationRun.artifactRef`；经 CreditLedger 结算 1 积分后发 `artifact_ready` / `run_settled`
**And** 积分不足时不能开始，人话提示升级或等待重置（FR3/NFR3）
**And** 失败且无可用成果时释放预占、不扣分；右侧预览展示清单
**And** 模型调用只经 `lippi-pi-ai`；可用模型桩，端口形状不变（AD-3/AD-10）

### Story 2.5: 单次生成模型成本可统计

As a 运营/产品负责人,
I want 每次选品或 Listing 的 GenerationRun 留下可汇总的模型成本指标,
So that 定价前能量化单次成本并覆盖毛利（NFR2）。

**Acceptance Criteria:**

**Given** 一次计费生成（选品或后续 Listing）经 `pi-ai` 完成或失败结束
**When** Run 结束（成功结算或失败释放）
**Then** `GenerationRun`（或关联成本记录）持久化可汇总字段：至少含 runId、用途类型（picklist/listing）、模型标识、token/调用计量（若桩可得则记桩值）、估算成本或原始用量
**And** 提供只读查询或导出能力（API 或管理脚本），可按类型汇总单次平均成本
**And** 成本统计不替代 CreditLedger；前端用户不必看见内部成本
**And** 模型桩模式下仍写入可识别的计量占位，端口形状与真模型一致

## Epic 3: 生成并带走 Listing 套装

用户从候选或自填商品生成主图+详情文案；下载图/复制文案去上架；成功扣 1 分。落地 ListingArtifact、MediaStore/OSS 与导出。

### Story 3.1: 生成 Listing 套装（文案 + 主图媒体）

As a 一人店主,
I want 基于候选或自填信息生成主图与详情文案/展示说明,
So that 我有一套能拿去店铺上架的素材草稿。

**Acceptance Criteria:**

**Given** 用户已登录、积分充足；可选 `picklistItemId` 或自填商品信息
**When** 发起 Listing 生成（新 GenerationRun + 新预占）
**Then** 成功持久化详情文案与展示说明，且至少 1 个 `mediaObjectId`（FR9）
**And** 图片字节只进阿里云 OSS（或本地桩）；Listing 不另造 URL 真相（AD-9）
**And** 风格为国内通用一种；明确非包装/纸箱设计
**And** 成功结算 1 积分；失败释放预占；预览区可展示 Listing

### Story 3.2: 导出图片与复制文案

As a 一人店主,
I want 下载主图并复制详情文案,
So that 我能粘贴到淘宝/闲鱼/拼多多上架。

**Acceptance Criteria:**

**Given** 已有成功的 Listing 套装（含 mediaObjectId 与文案）
**When** 用户点击下载图片或复制文案
**Then** 可获得可读图片文件/URL 下载，以及可粘贴的文案内容（FR10）
**And** 导出不额外扣积分
**And** 失败时人话提示（如链接过期/服务繁忙）（NFR3）

## Epic 4: 重试、吐槽与回看历史

不满意可重试（再扣分、旧成果保留）；可提「质量差」；能查近 60 天本人选品与 Listing。

### Story 4.1: 不满意重试（新 Run + 新预占）

As a 一人店主,
I want 对不满意的选品或 Listing 再生成一次,
So that 我有机会拿到更好结果，并清楚会再扣分。

**Acceptance Criteria:**

**Given** 用户已有至少一次成功或失败的生成上下文，且积分充足
**When** 发起重试
**Then** 创建新的 `GenerationRun` 与新预占；可复用同一 AgentSession，但不得复用旧 hold（FR11/AD-7）
**And** 先前成功成果不被覆盖、不自动 superseded
**And** 成功则再结算 1 分；失败释放预占

### Story 4.2: 提交「质量差」反馈

As a 一人店主,
I want 对某次结果提交简短「质量差」反馈,
So that 产品能收集质量信号改进生成。

**Acceptance Criteria:**

**Given** 用户可定位到某次成果或 GenerationRun
**When** 提交简短质量差反馈
**Then** Feedback 域持久化该记录（FR11/AD-6）
**And** 提交反馈本身不扣积分
**And** 仅本人可对自己的成果反馈

### Story 4.3: 近 60 天生成历史

As a 一人店主,
I want 查看本人近 60 天内的选品清单与 Listing,
So that 我能回看和复用以前的产出。

**Acceptance Criteria:**

**Given** 用户曾成功生成过选品和/或 Listing
**When** 打开历史（如左侧会话/历史入口）
**Then** 仅返回本人近 60 天内的成功成果，选品与 Listing 均可查（FR12）
**And** HistoryQuery 只读聚合，无独立写模型；每次成功记录独立保留
**And** 不能看到其他用户数据
