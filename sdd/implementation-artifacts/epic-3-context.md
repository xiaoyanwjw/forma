# Epic 3 Context: 电商场景内选品、Listing 与历史

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

在已绑定「电商开店」场景的会话里，用户能走通选品清单与 Listing 两条计费生成路径（各预占/结算 1 积分），经 SSE 看进度、在 Computer 预览成果、导出/复制、重试与「质量差」反馈，并回看本人近 60 天历史；超范围输入只做短聊友好拉回且不扣生成分。近端不做品类模板选择器，统一国内通用默认风格。

## Stories

- Story 3.1: GenerationRun、SSE 与会话必绑场景
- Story 3.2: 电商场景能力包按 sceneCode 加载
- Story 3.3: 会话态工作台（侧栏 + 对话 + Computer）
- Story 3.4: 生成选品清单并结算 1 积分
- Story 3.5: 超范围短聊后友好拉回
- Story 3.6: 生成 Listing 套装并结算 1 积分
- Story 3.7: 下载图片与复制文案
- Story 3.8: 重试、「质量差」反馈与近 60 天历史

## Requirements & Constraints

- **计费：** 选品成功扣 1；Listing 成功扣 1。不足余额不可开始并提示升级。失败/取消不扣或退回预占。纯闲聊与拉回引导不扣选品/Listing 分。积分全站共用，不按场景拆账。
- **结算点：** 仅当可用成果已持久化后结算；SSE 流结束/中断本身不结算。
- **选品成果：** 约 8–12 条候选，每条含可读可卖理由；可进历史复看。近端无品类模板选择 UI（PRD 的模板选用后置），用通用默认约束即可。
- **Listing 成果：** 主图方案 + 详情文案 + 展示说明；国内通用一种风格；非包装/纸箱设计；可下载图、复制文案。
- **重试 / 反馈：** 重试=新计费生成；「质量差」短反馈可保存、本动作不加生成费。成功成果各自保留，重试不覆盖旧记录。
- **历史：** 本人近 60 天选品与 Listing；默认全球（全场景）列表，可按场景筛选。
- **拉回：** 超范围请求允许短暂友好说明，再明确拉回选品/Listing 并给入口；不假装完成未支持任务；须有轮次/策略上限，避免无界高成本闲聊。
- **质量 / 成本 / 错误：** 理由与文案须可读可改后上架；每次计费生成须可观测模型成本；失败给人话原因。
- **近端不做：** 询盘/客服/物流/收款、出海深度适配、三平台分风格 Listing、万能 Agent 叙事、按场景拆账本。

## Technical Decisions

- **边界：** 浏览器只调本系统 REST/SSE；不直连大模型、不写积分。模型经 `pi-ai`；业务入口 `AgentSession`。
- **会话 / Run：** 创建计费会话或 `GenerationRun` 必须带 `sceneId` 或 `sceneCode` 并持久化；缺失拒绝。每次计费（含重试）= 新 Run + 新预占；可复用同一聊天会话，不得复用旧 hold。
- **SSE：** 计费用 `fetch` + `ReadableStream` + JWT，不用原生 `EventSource`。闭合事件名：`run_started` | `agent_started` | `message_delta` | `tool_started` | `tool_finished` | `agent_ended` | `human_input_required` | `artifact_ready` | `run_failed` | `run_settled`。`human_input_required` 等待期间不结算。
- **积分写入者：** 仅 CreditLedger；流程为检查 → 预占 1 → 可用成果落库后结算，否则释放。Agent 工具不得写积分。Application 编排「落库 → 结算」。
- **成果所有权：** ArtifactStore 唯一写 `ebus_artifact`。选品形状 `artifact_type=picklist`（挂 `GenerationRun.artifactRef`）；Listing 形状 `artifact_type=sku`（文案/展示 + ≥1 `mediaObjectId`，可选 `picklistItemId`）。Feedback 记「质量差」；HistoryQuery 只读聚合。
- **媒体：** 图片字节只进阿里云 OSS；Listing 主图真相为 `mediaObjectId` / `objectKey`，MySQL 不存大图、不另造第二套 URL 真相。
- **场景包：** 提示词 / skill / tool 正文在仓库资源，按 `sceneCode` 加载并注入 `AgentSession`（槽位 allowlist）。禁止浏览器下发或接受系统提示词与 tool 定义正文。电商包须覆盖选品 + Listing 两条固定路径。
- **ID：** 对外业务 ID 用 UUID 字符串；库内可有 BIGINT 代理主键。

## UX & Interaction Patterns

- **顶栏契约（全站）：** 左 Logo +「场景 / 电商开店」面包屑 + 历史 + 套餐；右积分 + 升级 + 头像。顶栏全宽贴边，勿做成居中悬浮胶囊。
- **工作台会话态：** 侧栏历史 + 主对话 +（有生成结果后）右侧 Adam's Computer split；空态为提问门面 + 选品/Listing 胶囊。窄屏侧栏可收，Computer 不得压坏主对话可读性。
- **Computer：** 预览清单条目或主图/文案类成果，不只聊天气泡。生成成功后打开右侧预览。
- **导出：** Listing 可下载图片、复制文案并给成功反馈；选品若提供复制则行为一致且不额外扣分。
- **拉回语气：** 先接住意图，再温和说明近端拿手选品与上架素材，并给出入口按钮/等价引导；避免生硬拒绝或冷冰列表。用户可见文案避免内部黑话。
- **历史页：** 顶栏「历史」进入；列表与顶栏契约一致，支持按场景筛选。

## Cross-Story Dependencies

- Depends on Epic 1（登录 JWT、CreditLedger 预占/结算/余额不足）与 Epic 2（进入电商工作台、顶栏/场景壳已就位）。
- 3.1（绑场景 + SSE + GenerationRun）与 3.2（电商 pack）是 3.4/3.5/3.6 的前置；3.3 工作台壳承接流式事件与 Computer 投影。
- 3.7 导出依赖 3.6 成果与 MediaStore；3.8 重试复用 3.1/3.4/3.6 计费规则，历史依赖成果落库与 AD-15 筛选语义。
- 账户页用量/改密属 Epic 4，本 Epic 不实现 `/api/v1/account/**`。
