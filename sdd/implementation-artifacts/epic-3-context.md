# Epic 3 Context: 电商场景内选品、Listing 与历史

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

在已绑定「电商开店」场景的会话里，用户能生成带可卖理由的选品清单与可上架的 Listing 套装，经积分预占/结算计费；生成后可在工作台预览、导出，可重试与提交质量反馈，并可回看近 60 天本人历史。超范围提问允许短聊后友好拉回选品/Listing，且不扣生成积分。近端不做品类模板选择器，统一国内通用默认风格。本 Epic 把场景壳里的「真正干活」能力做通，让积分与成果可交付对齐。

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

- 选品成功：约 8–12 条候选，每条含可卖理由；可读、无明显违规胡编。Listing 成功：主图方案 + 详情文案 + 展示说明，文案可改后上架。
- 计费：选品清单 1 分、Listing 套装 1 分；仅可用成果持久化后结算；不足不可开始；失败/取消不扣或退回预占；禁止仅因 SSE 流结束扣分。积分全站共用，不按场景拆账。
- 未登录不得启动计费生成；失败给人话原因（额度不足、服务繁忙、输入不完整等）。
- 可导出：下载 Listing 图片、复制文案；选品若提供复制则不额外扣分。重试再扣分；「质量差」简短反馈本身不扣生成分。
- 本人近 60 天选品/Listing 历史；默认全场景列表，可按场景筛。
- 超范围请求：短暂友好说明后拉回近端能力并给入口；不假装交付未支持任务；须有轮次/策略上限，避免无界高成本闲聊。
- 每条 GenerationRun 须可统计模型用量/成本。用户可见文案禁止内部黑话。
- 近端范围：无品类模板选择 UI；生成用单一通用默认风格（多品类模板后置）。

## Technical Decisions

- 创建计费会话 / GenerationRun 必须带 `sceneId` 或 `sceneCode`；缺失拒绝。历史查询默认全球（本人），支持按场景筛选。
- 积分仅经 CreditLedger：检查 → 预占 1 → 可用成果落库后结算，否则释放；Agent 工具不得写积分。
- 计费生成走 SSE：浏览器用 fetch + ReadableStream + JWT，不用原生 EventSource。闭合事件名：`run_started` | `message_delta` | `tool_started` | `tool_finished` | `human_input_required` | `artifact_ready` | `run_failed` | `run_settled`；`human_input_required` 挂起期间不结算。SSE 失败用 `run_failed`，不静默断流。
- 模型只经后端 pi-ai；业务入口 `AgentSession`。提示词/skill/tool 正文在仓库代码资源，按 `sceneCode` 加载注入；禁止浏览器下发或接受系统提示词/tool 定义。电商包须覆盖选品与 Listing 固定路径。
- 选品成果必含 `templateId`（近端可用默认模板身份）；挂到当前 GenerationRun。Listing 主图真相为 `mediaObjectId`（经 MediaStore/OSS），不另造第二套 URL；MySQL 不存图片大字段。Listing 可带可选 `picklistItemId`。
- 每次计费生成（含重试）= 新 GenerationRun + 新预占；可复用同一聊天会话，不得复用旧 hold。成功成果独立保留，重试不覆盖。应用层编排「落库 → 结算」，再推 `artifact_ready` / `run_settled`。
- 对外业务 ID 用 UUID 字符串（biz_id）；REST/SSE 均需 JWT（除公开入口外）。

## UX & Interaction Patterns

- 工作台空态（Epic 2）：居中「我能为你做什么？」+ 选品/上架胶囊 + 提问壳。进入会话态后：侧栏历史 + 主对话 +（有成果后）右侧 Adam's Computer split；顶栏面包屑「场景 / 电商开店」。
- 生成成功后 Computer 预览清单条目或主图/文案类成果，不只聊天气泡。胶囊触发送品/上架流；超范围拉回用友好气泡 + 能力入口按钮。
- 历史页：近 60 天列表；顶栏与全站一致（左场景/历史/套餐，右积分/升级/头像）。窄屏侧栏可收，Computer 不破坏主对话可读性。

## Cross-Story Dependencies

- 依赖 Epic 1：登录 JWT、CreditLedger 预占/结算/额度不足提示。依赖 Epic 2：进入电商工作台空态、场景元数据与顶栏契约；本 Epic 落实会话创建时绑定场景。
- 本 Epic 内：3.1/3.2 为计费会话与能力包底座；3.3 承接会话态预览壳；3.4/3.6 两条生成路径；3.5 与计费路径解耦（不扣生成分）；3.7 依赖 Listing 成果可见；3.8 依赖成功成果与账本规则，并提供历史回看。
