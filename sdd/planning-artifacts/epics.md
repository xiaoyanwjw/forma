---
stepsCompleted:
  - step-01-validate-prerequisites
  - step-02-design-epics
  - step-03-create-stories
  - step-04-final-validation
status: ready-for-dev
inputDocuments:
  - sdd/planning-artifacts/prds/prd-lippi-ai-ebusiness-2026-09-23/prd.md
  - sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md
  - sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/DESIGN.md
  - sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/EXPERIENCE.md
---

# lippi-ai-ebusiness - Epic Breakdown

## Overview

This document provides the complete epic and story breakdown for lippi-ai-ebusiness，基于已定稿 PRD、Architecture Spine（含 AD-14～18）与 UX 09-26。本轮输入**排除** pi-agent-slim、09-26 architecture companion、以及既有 epics/sprint 清单（实现进度仍以 `sprint-status.yaml` 为准，本文件重新盘点需求）。

## Requirements Inventory

### Functional Requirements

FR1: 用户可注册与登录；未登录不能消耗积分完成生成；登录态可见剩余积分与套餐档  
FR2: 用户可查看当前套餐（免费/Pro/Plus）、本月剩余积分、下次重置时间  
FR3: 成功产出可用结果时扣积分——选品清单 1 分、Listing 套装 1 分；不足不可开始；失败不扣或退回  
FR4: 月积分额度——免费 20 / Pro 200 / Plus 600  
FR5: 积分按月重置且未用完清零不结转  
FR6: 用户可从免费升级到 Pro/Plus（月费数字待定；近端可管理改档）  
FR7: 用户可生成选品清单（约 8–12 条候选，每条含可卖理由）；结果可进历史  
FR8: 用户可选用不同品类模板；三档套餐均可使用全部已上线模板（**本轮近端范围：通用默认风格即可，品类选择器后置**）  
FR9: 用户可生成 Listing 套装（主图方案 + 详情文案 + 展示说明）；国内通用风格  
FR10: 用户可下载图片 / 复制文案以便上架  
FR11: 用户可对结果重试（再扣分）或提交「质量差」简短反馈  
FR12: 用户可查看本人近 60 天选品清单与 Listing  
FR13: 产品首页为场景画廊（大卡片），非直达提问门面；近端至少 1 亮卡 + 若干灰卡  
FR14: 点击可用场景进入场景工作台（提问门面 + 该场景胶囊）；电商承载选品/Listing；可回画廊  
FR15: 未开放场景灰显可见；点击仅「即将推出」提示，不进工作台、不扣分；近端三灰卡：短视频带货、小红书种草、本地生活  
FR16: 场景内超范围请求允许短聊后友好拉回至选品/Listing；不假交付、不扣生成积分  
FR17: 产品形状保持「画廊 → 工作台 → 场景内固定路径」，新场景可接入而不改首页信息架构  
FR18: 顶栏头像进账户页；含个人资料 / 使用情况 / 安全分区；可跳转套餐；近端改密为真能力（架构已钉）

### NonFunctional Requirements

NFR1: 选品理由可读、无明显违规胡编；Listing 文案可改后上架；抽检比例待定  
NFR2: 可统计单次选品/Listing 模型成本；短聊拉回须有轮次/策略上限，避免无界高成本循环  
NFR3: 失败给人话原因（额度不足 / 服务繁忙 / 输入不完整 / 场景未开放等）  
NFR4: 避免明显违禁宣传；协议声明 AI 生成须人工复核后再上架  
NFR5: v1 登录 + 异常频率限制（轻量防刷）  
NFR6: 新增场景不得拆除场景画廊范式；未开放场景可以灰显/配置方式扩展（配置机制可后置，交互契约锁定）

### Additional Requirements

- SceneCatalog 为场景元数据唯一写者（含灰卡真记录）；稳定键 `sceneCode`；与 CatalogTemplate（品类）分离（AD-14）  
- 创建计费会话 / GenerationRun 必须带 `sceneId` 或 `sceneCode`；历史默认全球、可按场景筛；积分全站共用（AD-15）  
- 提示词 / skill / tool 正文在代码资源，按 `sceneCode` 绑定加载；禁止浏览器下发系统提示词或 tool 定义（AD-16）  
- 账户 API 统一 `/api/v1/account/**`：邮箱只读、显示名可改、改密真接口、用量只读；禁止 account 下写积分结算（AD-17）  
- 未开放场景后端硬拒近端不做，后置必补（AD-18）  
- 继承既有：模块化单体、SSE 计费生成、CreditLedger 预占结算、AgentSession 门面、OSS 媒体、JWT（AD-1～13）  
- 无 greenfield starter 变更；继续现有 Vue SPA + Spring Boot 仓  

### UX Design Requirements

UX-DR1: 全站顶栏全宽贴边——左 Logo + 场景（或面包屑「场景 / 当前场景」）+ 历史 + 套餐；右积分 + 升级 + 头像；禁止顶栏 max-width 居中悬浮胶囊  
UX-DR2: 实现 DESIGN token（canvas/surface/ink/mute/line/accent/success、圆角与间距、Instrument Sans + Noto Sans SC、点阵底）  
UX-DR3: 场景画廊页——2×2 大卡片；亮卡整卡可点、无「开始使用」脚链；灰卡降饱和 +「即将推出」；产品文案冷静短句  
UX-DR4: 灰卡点击 toast「即将推出」，可引导去电商开店；不进假工作台  
UX-DR5: 电商工作台——空态居中「我能为你做什么？」+ 选品/上架胶囊 + 提问壳；会话态侧栏历史 + 对话 + 生成后右侧 Computer split  
UX-DR6: 超范围输入友好拉回气泡 + 能力入口按钮；不扣分  
UX-DR7: 套餐页——本月剩余余额卡 + 免费/Pro/Plus 三档方案卡；无支付按钮（即将开放订阅）  
UX-DR8: 历史页——近 60 天列表；顶栏契约一致  
UX-DR9: 账户页——左栏个人资料/使用情况/安全；资料行、用量摘要与流水、改密/退出入口；头像进账户  
UX-DR10: 窄屏适配——画廊单列；工作台侧栏可收；账户导航改顶部分页签  
UX-DR11: 无障碍底线——灰卡标明未开放、焦点环可见、账户分区可键盘切换  
UX-DR12: 用户可见文案禁止内部黑话（示意、支付网关、空壳、近端等）  

### FR Coverage Map

FR1: Epic 1 - 注册与登录；未登录不可计费生成  
FR2: Epic 1 - 查看套餐档、剩余积分、下次重置  
FR3: Epic 1 - 成功产出扣分规则与不足不可开始（账本侧；生成触发在 Epic 3）  
FR4: Epic 1 - 月额度免费 20 / Pro 200 / Plus 600  
FR5: Epic 1 - 按月重置、未用完清零  
FR6: Epic 1 - 免费升级 Pro/Plus（近端管理改档）  
FR7: Epic 3 - 生成选品清单并进历史  
FR8: Epic 3 - 近端通用默认风格；品类模板选择器后置（本轮不做）  
FR9: Epic 3 - 生成 Listing 套装  
FR10: Epic 3 - 下载图片 / 复制文案  
FR11: Epic 3 - 重试（再扣分）与「质量差」反馈  
FR12: Epic 3 - 近 60 天本人历史  
FR13: Epic 2 - 首页场景画廊大卡片  
FR14: Epic 2 - 可用场景进入工作台；电商承载选品/Listing 入口  
FR15: Epic 2 - 灰卡即将推出；三灰卡固定文案场景  
FR16: Epic 3 - 超范围短聊后友好拉回且不扣生成分  
FR17: Epic 2 - 画廊→工作台→固定路径形状可扩展  
FR18: Epic 4 - 账户页资料 / 用量 / 安全与改密  

## Epic List

### Epic 1: 登录、积分与套餐可见
用户能注册登录，看清套餐档、本月剩余积分与重置时间；额度不足有人话提示；近端可管理改档升级。
**FRs covered:** FR1, FR2, FR3, FR4, FR5, FR6

### Epic 2: 场景画廊与进入电商工作台
用户打开首页见到场景大卡片画廊（1 亮 + 3 灰）；点「电商开店」进入工作台空态；灰卡仅「即将推出」提示；全站顶栏契约一致，形状支持后续加场景。
**FRs covered:** FR13, FR14, FR15, FR17

### Epic 3: 电商场景内选品、Listing 与历史
用户在已绑定场景的会话中生成选品清单与 Listing，经积分预占结算；可预览、导出、重试与反馈；可查近 60 天历史；超范围输入短聊后友好拉回且不扣生成积分。近端不做品类模板选择器（通用默认）。
**FRs covered:** FR7, FR8（通用默认/后置）, FR9, FR10, FR11, FR12, FR16

### Epic 4: 账户资料、用量与安全
用户从头像进入账户页，管理个人资料、查看使用情况、修改密码与退出；账户 API 统一 `/api/v1/account/**`。
**FRs covered:** FR18

## Epic 1: 登录、积分与套餐可见

用户能注册登录，看清套餐档、本月剩余积分与重置时间；额度不足有人话提示；近端可管理改档升级。

### Story 1.1: 注册与登录拿 JWT

As a 一人店主,
I want 用邮箱和密码注册并登录拿到会话令牌,
So that 我能以本人身份使用需要积分的能力。

**Acceptance Criteria:**

**Given** 我尚未注册
**When** 我用有效邮箱与密码完成注册
**Then** 系统创建账户并返回可使用的 JWT（或等价登录态）
**And** 未登录请求受保护的计费/生成接口时被拒绝（FR1）

**Given** 我已注册
**When** 我用正确密码登录
**Then** 我获得有效 JWT 并可调用需认证接口
**And** 密码错误时得到人话错误提示，不泄露账户是否存在的多余细节（NFR3）

### Story 1.2: 积分账本：额度、预占、结算、月重置

As a 已登录用户,
I want 系统按套餐发放月积分并在生成成功时扣分、失败时不扣或退回,
So that 计费规则清楚且不会因失败白扣分。

**Acceptance Criteria:**

**Given** 新用户处于免费档
**When** 账本初始化或本月周期开始
**Then** 本月额度按档位发放：免费 20 / Pro 200 / Plus 600（FR4）
**And** 积分只经 CreditLedger 记账，前端不可直接改余额

**Given** 一次计费生成即将开始且余额充足
**When** 系统预占所需积分（选品 1 分或 Listing 1 分）
**Then** 可用余额相应减少或锁定，不足时不可开始并返回人话原因（FR3, NFR3）

**Given** 预占已发生
**When** 可用成果落库成功
**Then** 预占转为结算扣减；若失败或取消则退回预占，不因「仅 SSE 结束」扣分（FR3）

**Given** 订阅/计费周期到点
**When** 执行月重置
**Then** 按当前档位重新发放额度，上月未用完清零不结转（FR5）

### Story 1.3: 登录后看见套餐与积分

As a 已登录用户,
I want 在产品里随时看到当前套餐、剩余积分和下次重置时间,
So that 我知道还能生成几次、要不要升级。

**Acceptance Criteria:**

**Given** 我已登录
**When** 我打开需登录的主界面（或拉取 me/credits 接口）
**Then** 可见当前套餐档、本月剩余积分、下次重置时间（FR2）
**And** 顶栏右侧展示积分摘要入口，与全站顶栏契约一致的右侧位（为 Epic 2 顶栏承接预留）

**Given** 余额为 0 或不足一次生成
**When** 我查看积分状态
**Then** 提示清楚且可用人话引导去套餐页（NFR3）

### Story 1.4: 套餐页三档方案

As a 已登录用户,
I want 在套餐页看到本月余额与免费/Pro/Plus 三档差异,
So that 我能理解升级能换来多少积分。

**Acceptance Criteria:**

**Given** 我已登录并打开套餐页
**When** 页面加载完成
**Then** 展示本月剩余余额卡，以及免费 / Pro / Plus 三档方案卡（额度 20 / 200 / 600）（FR2, FR4, UX-DR7）
**And** 无真实支付下单按钮；订阅操作用「即将开放」类产品文案，禁止内部黑话（UX-DR7, UX-DR12）

**Given** 我从顶栏「套餐」或「升级」进入
**When** 导航完成
**Then** 顶栏左「套餐」高亮语义正确，右仍可见积分与头像位

### Story 1.5: 近端管理改档升级

As a 运营或已授权管理员（近端）,
I want 把用户从免费改到 Pro/Plus（或回退）,
So that 在支付上线前用户仍能按档位使用更高额度。

**Acceptance Criteria:**

**Given** 目标用户存在且调用方具备改档权限（或约定的管理入口）
**When** 将其套餐改为 Pro 或 Plus
**Then** 用户档位更新，本周期额度按新档规则生效（FR6）
**And** 禁止经账户自助写口直接改积分结算；改档不走 `/account` 结算写路径（对齐 AD-17 精神）

**Given** 用户打开套餐/积分视图
**When** 改档已生效
**Then** FR2 展示的档位与剩余额度与账本一致

### Story 1.6: 协议声明与轻量防刷

As a 使用产品的用户与平台方,
I want 看到 AI 生成须人工复核的声明，并对异常高频请求有限制,
So that 合规预期清楚且免费额度不易被刷穿。

**Acceptance Criteria:**

**Given** 用户在注册流或首次使用关键页
**When** 展示服务协议/合规提示
**Then** 明确声明 AI 生成内容须人工复核后再上架（NFR4）

**Given** 同一账号或 IP 出现异常高频登录/注册/计费尝试
**When** 触发频率阈值
**Then** 请求被限制并返回人话提示（NFR5）
**And** 正常使用节奏不受明显误伤（阈值可配置或后续调参）

## Epic 2: 场景画廊与进入电商工作台

用户打开首页见到场景大卡片画廊（1 亮 + 3 灰）；点「电商开店」进入工作台空态；灰卡仅「即将推出」提示；全站顶栏契约一致，形状支持后续加场景。

### Story 2.1: SceneCatalog 与画廊列表 API

As a 已登录用户（及前端画廊）,
I want 从后端读取真实的场景元数据（含未开放场景）,
So that 首页卡片不是写死假数据，后续加场景也不改画廊范式。

**Acceptance Criteria:**

**Given** SceneCatalog 为场景元数据唯一写者（AD-14）
**When** 种子/迁移写入近端四条场景
**Then** 至少含：电商开店 `AVAILABLE`，以及短视频带货、小红书种草、本地生活均为 `COMING_SOON` 真记录（FR13, FR15）
**And** 每行含 `biz_id`、稳定 `sceneCode`、展示名、状态、排序；与 CatalogTemplate（品类）分离

**Given** 客户端请求画廊列表 API
**When** 返回成功
**Then** 只读 SceneCatalog，按排序给出卡片所需字段（名、文案、状态等）
**And** 响应不含系统提示词或 tool 定义正文（AD-16）

**Given** 将来新增一条 `COMING_SOON` 或 `AVAILABLE` 场景行
**When** 画廊再次加载
**Then** 无需改首页信息架构即可展示（FR17, NFR6）

### Story 2.2: 全站顶栏契约与 DESIGN token

As a 使用 Adam 网页的用户,
I want 各页顶栏位置与视觉语言一致,
So that 导航不漂移，品牌感统一。

**Acceptance Criteria:**

**Given** 任意主页面（画廊 / 工作台 / 历史 / 套餐；账户页顶栏位一并对齐）
**When** 我查看顶栏
**Then** 顶栏全宽贴边：左 Logo + 场景（或面包屑）+ 历史 + 套餐；右积分 + 升级 + 头像（UX-DR1）
**And** 禁止顶栏 `max-width` 居中悬浮胶囊

**Given** 全局样式接入
**When** 页面渲染
**Then** 使用 DESIGN token（canvas/surface/ink/mute/line/accent/success、圆角间距、Instrument Sans + Noto Sans SC、点阵底）（UX-DR2）
**And** 用户可见文案无内部黑话（UX-DR12）

**Given** 键盘用户
**When** 聚焦可交互控件
**Then** 焦点环可见（UX-DR11）

### Story 2.3: 场景画廊页（1 亮 + 3 灰）

As a 一人店主,
I want 打开首页先选场景而不是直接提问,
So that 我清楚当前能做什么、以后还会有什么。

**Acceptance Criteria:**

**Given** 我已登录并打开场景首页
**When** 画廊加载 SceneCatalog 数据
**Then** 以大卡片展示场景：桌面约 2×2；「电商开店」为可用亮卡，三张灰卡降饱和并标「即将推出」（FR13, FR15, UX-DR3）
**And** 亮卡整卡可点，无底部「开始使用」脚链；文案为冷静短句产品文案（UX-DR3, UX-DR12）

**Given** 窄屏视口
**When** 查看画廊
**Then** 卡片改为单列适配（UX-DR10）

**Given** 顶栏「场景」
**When** 我在画廊页
**Then** 场景导航呈当前页语义；右上头像可进账户（账户页实现见 Epic 4）

### Story 2.4: 灰卡「即将推出」提示

As a 浏览画廊的用户,
I want 点到未开放场景时得到明确提示且不进入空壳,
So that 我不会误以为已经能用，也不会误扣分。

**Acceptance Criteria:**

**Given** 场景状态为 `COMING_SOON`
**When** 我点击该灰卡
**Then** 仅出现「即将推出」类 toast/轻提示，不进入工作台路由（FR15, UX-DR4）
**And** 不创建计费会话、不扣积分

**Given** toast 展示中
**When** 提供引导时
**Then** 可引导用户去「电商开店」可用场景（UX-DR4）
**And** 灰卡对辅助技术标明未开放（UX-DR11）

### Story 2.5: 进入电商工作台空态

As a 一人店主,
I want 点「电商开店」进入该场景工作台空态,
So that 我能从提问壳和选品/上架胶囊开始干活（具体生成在 Epic 3）。

**Acceptance Criteria:**

**Given** 「电商开店」为 `AVAILABLE`
**When** 我点击亮卡
**Then** 进入电商场景工作台路由；顶栏面包屑为「场景 / 电商开店」（或等价）（FR14, UX-DR1）

**Given** 工作台空态（尚无生成结果）
**When** 页面展示
**Then** 居中「我能为你做什么？」+ 选品/上架胶囊 + 提问壳；可回画廊（FR14, UX-DR5）
**And** 会话上下文已具备绑定该场景的 `sceneId`/`sceneCode` 的前置（创建计费会话时必须带场景，落实在 Epic 3）（AD-15）

**Given** 窄屏
**When** 打开工作台
**Then** 侧栏可收起，不挡住主提问区（UX-DR10）

## Epic 3: 电商场景内选品、Listing 与历史

用户在已绑定场景的会话中生成选品清单与 Listing，经积分预占结算；可预览、导出、重试与反馈；可查近 60 天历史；超范围输入短聊后友好拉回且不扣生成积分。

**近端范围说明：** FR8 品类模板选择器本 Epic **不做**；选品/Listing 使用**单一通用默认风格**即可，多品类模板后置。

### Story 3.1: GenerationRun、SSE 与会话必绑场景

As a 已登录用户,
I want 每次计费生成都挂在明确的场景会话上，并通过 SSE 收到进度,
So that 结果可归属场景，且不会出现无场景的计费跑批。

**Acceptance Criteria:**

**Given** 我在电商工作台发起计费会话或 GenerationRun
**When** 创建请求提交
**Then** 必须携带 `sceneId` 或 `sceneCode`；缺失则拒绝并给人话错误（AD-15, NFR3）
**And** 积分仍全站共用，不按场景拆账本

**Given** 计费生成进行中
**When** 前端以 `fetch` + `ReadableStream` + JWT 消费 SSE
**Then** 能收到约定 Agent 事件流；不用原生 `EventSource` 作为计费通道

**Given** 可用成果尚未落库
**When** 仅 SSE 流结束或中断
**Then** 不结算扣分；预占按失败/取消规则退回（对齐 FR3 / Epic 1.2）

### Story 3.2: 电商场景能力包按 sceneCode 加载

As a 平台,
I want 电商开店的提示词 / skill / tool 正文放在代码资源并按 sceneCode 绑定,
So that 浏览器无法下发系统提示词，发版即可更新场景能力。

**Acceptance Criteria:**

**Given** SceneCatalog 中电商场景有稳定 `sceneCode`
**When** Application / AgentRuntime 在 prompt 前加载包
**Then** 从仓库代码/资源加载该码对应包并注入 `AgentSession`（AD-16）
**And** 电商包须覆盖选品与 Listing 两条固定路径；风格为**国内通用默认**（不依赖品类模板选择器）

**Given** 前端客户端
**When** 调用业务 API
**Then** 响应与请求均不得下发或接受系统提示词 / tool 定义正文（AD-16）

### Story 3.3: 会话态工作台（侧栏 + 对话 + Computer）

As a 一人店主,
I want 生成过程中看到对话，并在有结果后看到右侧 Computer 预览,
So that 我能边聊边确认清单或上架素材。

**Acceptance Criteria:**

**Given** 工作台已从空态进入会话态
**When** 页面展示
**Then** 布局为侧栏历史 + 主对话 +（有生成结果后）右侧 Adam's Computer split（UX-DR5）
**And** 顶栏面包屑保持「场景 / 电商开店」契约（UX-DR1）

**Given** 选品或 Listing 产出可用结果
**When** Computer 区域更新
**Then** 能预览清单条目或主图/文案类成果，而不仅是纯文字聊天气泡

**Given** 窄屏
**When** 会话进行中
**Then** 侧栏可收；Computer 不破坏主对话可读性（UX-DR10）

### Story 3.4: 生成选品清单并结算 1 积分

As a 一人店主,
I want 在电商场景内生成一份带理由的选品候选清单,
So that 我知道可以卖什么。

**Acceptance Criteria:**

**Given** 我已登录、余额 ≥ 1，且会话已绑定电商场景
**When** 我通过胶囊或提问发起选品生成
**Then** 预占 1 积分；成功落库约 8–12 条候选且每条含可卖理由（FR7, NFR1）
**And** 使用通用默认生成约束（无品类模板选择步骤）

**Given** 生成失败或用户取消
**When** 结算判定
**Then** 不扣或退回预占，并给人话原因（FR3, NFR3）

**Given** 余额不足
**When** 我尝试开始选品
**Then** 不可开始，提示去套餐/升级（FR3）

**Given** 单次选品调用
**When** 运行结束
**Then** 可统计该次模型成本（至少有可观测字段/日志）（NFR2）

### Story 3.5: 超范围短聊后友好拉回

As a 在电商工作台提问的用户,
I want 问到近端做不到的事时被友好拉回选品/Listing,
So that 我不被假装交付，也不被乱扣生成分。

**Acceptance Criteria:**

**Given** 我输入超范围请求（如代回千牛客服、其它行业任务等）
**When** Agent 回应
**Then** 允许短暂友好说明，并明确拉回近端能力，提供选品/上架入口按钮或等价引导（FR16, UX-DR6）
**And** **不**假装已完成未支持任务；**不**按选品/Listing 规则扣生成积分

**Given** 用户持续偏离固定路径
**When** 达到轮次或策略上限
**Then** 停止无界高成本闲聊循环，继续引导回胶囊能力（NFR2）

### Story 3.6: 生成 Listing 套装并结算 1 积分

As a 一人店主,
I want 针对选定或输入的品生成主图方案 + 详情文案 + 展示说明,
So that 我能拿去店铺上架修改。

**Acceptance Criteria:**

**Given** 我已登录、余额 ≥ 1，会话绑定电商场景
**When** 我发起 Listing 生成
**Then** 预占 1 积分；成功后产出主图方案（经媒体对象/OSS，MySQL 不存大图字段）+ 详情文案 + 展示说明（FR9）
**And** 风格为国内通用默认；Listing 主图真相引用 `mediaObjectId`，不另造第二套 URL 真相

**Given** 生成失败
**When** 结算判定
**Then** 退回预占并人话提示（FR3, NFR3）

**Given** 单次 Listing 调用
**When** 运行结束
**Then** 可统计该次模型成本（NFR2）

### Story 3.7: 下载图片与复制文案

As a 一人店主,
I want 下载 Listing 图片并复制文案,
So that 我能粘贴到淘宝/闲鱼/拼多多上架。

**Acceptance Criteria:**

**Given** 一次 Listing 套装已成功落库且 Computer 可见
**When** 我点击下载图片
**Then** 能获取可用图片文件/链接（FR10）

**Given** 详情或展示文案已生成
**When** 我点击复制
**Then** 文案进入剪贴板，并有成功反馈（FR10）

**Given** 选品清单结果
**When** 提供复制类操作（若 UI 提供）
**Then** 行为一致、不额外扣分

### Story 3.8: 重试、「质量差」反馈与近 60 天历史

As a 一人店主,
I want 对不满意结果重试或反馈，并回看近 60 天记录,
So that 我能改进产出并找回过往清单/Listing。

**Acceptance Criteria:**

**Given** 已有一次选品或 Listing 结果
**When** 我选择重试
**Then** 发起新的 GenerationRun 与新的预占/结算（再扣分规则同 FR3）；不覆盖导致账本混乱的静默重写（FR11）

**Given** 我对结果不满意
**When** 提交「质量差」简短反馈
**Then** 反馈被保存并可被运营/后续分析读取；本动作本身不额外扣生成分（FR11）

**Given** 我打开历史页
**When** 列表加载
**Then** 可见本人近 60 天选品清单与 Listing；默认全球（全场景）列表，并可按场景筛选（FR12, AD-15, UX-DR8）
**And** 顶栏契约与全站一致（UX-DR1）

## Epic 4: 账户资料、用量与安全

用户从头像进入账户页，管理个人资料、查看使用情况、修改密码与退出；账户 API 统一 `/api/v1/account/**`。

### Story 4.1: 账户 API 骨架与个人资料

As a 已登录用户,
I want 通过统一账户 API 读取邮箱并修改显示名,
So that 我的资料有单一入口，且积分结算不会被账户页误写。

**Acceptance Criteria:**

**Given** 我已登录
**When** 调用 `/api/v1/account/**` 下的资料读取接口
**Then** 可取得邮箱（只读）与当前显示名等资料字段（FR18, AD-17）

**Given** 我提交合法的新显示名
**When** 调用改名接口
**Then** 显示名更新成功并在后续读取中可见（FR18）

**Given** 任意账户写接口
**When** 被实现或评审
**Then** 不存在经 account 路径写入积分结算/改档扣减的能力（AD-17）
**And** 既有 `GET /api/v1/me`、`GET /api/v1/credits` 可保留兼容，但不替代 account 作为账户页主契约

### Story 4.2: 账户页个人资料分区 UI

As a 已登录用户,
I want 从头像进入账户页并在「个人资料」里看到/改我的信息,
So that 设置入口和 Manus 式账户心智一致。

**Acceptance Criteria:**

**Given** 我已登录
**When** 点击顶栏头像
**Then** 进入账户页；默认落在个人资料分区（FR18, UX-DR9）

**Given** 桌面视口
**When** 账户页展示
**Then** 左栏为个人资料 / 使用情况 / 安全；右侧为当前分区内容（UX-DR9）
**And** 顶栏契约与全站一致（UX-DR1）

**Given** 窄屏
**When** 打开账户页
**Then** 分区导航改为顶部分页签（或等价），仍可键盘切换（UX-DR10, UX-DR11）

**Given** 个人资料区
**When** 展示与保存显示名
**Then** 邮箱只读展示；显示名可编辑保存；文案无内部黑话（UX-DR12）

### Story 4.3: 使用情况分区（只读用量）

As a 已登录用户,
I want 在账户里看到积分用量摘要与流水,
So that 我知道本月额度怎么花掉的。

**Acceptance Criteria:**

**Given** 我打开「使用情况」分区
**When** 数据加载成功
**Then** 展示与 CreditLedger 一致的用量摘要，以及可读的流水/明细（只读）（FR18, AD-17）
**And** 可提供跳转套餐页的入口（与 FR2/UX-DR7 一致），但不在此页支付

**Given** 账本暂无流水
**When** 打开该分区
**Then** 展示空态说明，而不是报错白屏（NFR3）

### Story 4.4: 安全分区：改密真能力与退出

As a 已登录用户,
I want 在安全分区修改密码并退出登录,
So that 我能真正管好账户安全。

**Acceptance Criteria:**

**Given** 我在「安全」分区提交正确的旧密码与符合规则的新密码
**When** 调用改密真接口（Identity，经 `/api/v1/account/**`）
**Then** 密码更新成功；此后须用新密码登录（FR18, AD-17）

**Given** 旧密码错误或新密码不合规
**When** 提交改密
**Then** 人话错误提示，密码不变（NFR3）

**Given** 我点击退出登录
**When** 操作完成
**Then** 本地会话/JWT 失效或清除，再访问受保护页需重新登录

**Given** 「删除账户」若展示
**When** 近端未实现注销
**Then** 控件可禁用/即将开放，不得假装已删除成功（UX-DR12）
