# Epic 2 Context: 对话生成选品清单（含澄清）

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

让已登录用户在 Agent 里选品类模板、发起「选品清单」，流式拿到约 8–12 条带可读可卖理由的候选；信息不足时用结构化选项澄清并 `resume` 续跑同一计费 run；成功落库后扣 1 积分，失败或取消不扣。本史诗同时落地 GenerationRun+SSE、MySQL Session/图 Checkpoint、Pi 运行时精简装配、品类模板与 Agent 壳+右侧预览——是选品主路径与后续 Listing（Epic 3）共用的生成底座。

## Stories

- Story 2.1: GenerationRun 与 SSE 事件骨架
- Story 2.2: 品类模板（三档通用）
- Story 2.3: Agent 对话壳与右侧预览
- Story 2.4: 生成选品清单并结算 1 积分
- Story 2.5: 单次生成模型成本可统计
- Story 2.6: Pi 运行时默认装配与噪音清理（重构先行）
- Story 2.7: MySQL SessionStore（pi_session / pi_session_entry）
- Story 2.8: MySQL 图 Checkpoint 与 resume 续跑端口
- Story 2.9: ask_human 澄清与选项 UI（含取消）

## Requirements & Constraints

- **选品成果：** 约 8–12 条候选，每条含可读可卖理由；清单必含 `templateId`；成功才扣 1 分。
- **模板：** 全部已上线品类模板对免费/Pro/Plus 均可用，不按套餐解锁；未上线不对用户可见。
- **澄清（HITL）：** 信息不足时 `ask_human`（问题 + 结构化选项，可允许自由文本）挂起生成；用户作答后同一计费 run 继续，直至产出或再次询问；取消挂起则释放预占、不扣分。
- **计费：** 积分不足不能开始；失败且无可用成果释放预占；禁止因 SSE 流结束或 `human_input_required` 结算；挂起期间保持预占。
- **成本（NFR2）：** 每次 GenerationRun 须留下可汇总的模型用量/估算成本（选品与后续 Listing 共用约定）；不替代账本，用户端不必展示。
- **失败体验：** 额度不足/服务繁忙等用人话说明。
- **客户端边界：** 浏览器不持有模型密钥、不直连大模型、不改积分。

## Technical Decisions

- **门面：** 业务只注入 `AgentSession`（subscribe / prompt / cancel / resume）；模型只经 `lippi-pi-ai`；可用模型桩，端口形状不变。
- **GenerationRun：** 每次计费生成 = 新 Run + 新预占；关联 holdId、sessionId、artifact 引用；成果由 PicklistArtifact 写入后由 application 结算，再发 `artifact_ready` / `run_settled`。
- **SSE：** 闭合事件名仅限 `run_started` | `message_delta` | `tool_started` | `tool_finished` | `human_input_required` | `artifact_ready` | `run_failed` | `run_settled`；客户端用 fetch + ReadableStream + JWT，不用 EventSource。
- **所有权：** CatalogTemplate 唯一写模板；PicklistArtifact 写清单；AgentRuntime 编排会话/Run/SSE；CreditLedger 唯一改余额；成果与积分不在 Tool 内写。
- **Pi slim（先重构再功能）：** 本阶段不拆 StateGraph（保留 agent⇄tools）；WRITE 审批默认关；Skill/斜杠不动；Prompt 三槽 + `SystemPromptInput` allowlist，禁止新增 contribution SPI；生产禁止静默 Sqlite（MissingBean → 失败启动或显式 InMemory）。
- **Session：** 生产 `@Primary` MysqlSessionStore；表 `pi_session` / `pi_session_entry`（DDL 仅 APP-META/bootstrap）；1 行 = 1 Message；compact 仅 `compact_anchor_seq`；`append` 同 `(sessionId, runId)` 整批幂等；`listRecent` 不在适配器内隐式按 user 过滤。
- **Checkpoint：** 表 `pi_graph_checkpoint`，与 Session 分表；HITL 挂起落盘；`resume` 注入 tool result 续跑；终态 SUCCESS/FAILED/CANCELLED 删 CP，SUSPENDED 保留；Redis CP 非默认；禁止用新 `prompt` 冒充续跑（无 CP 崩溃降级除外）。
- **ask_human：** ≠ WRITE 审批；SSE `human_input_required` payload 含 runId/sessionId/toolCallId/question/options；同一 hold 可跨越多次 ask/resume。
- **ID：** 业务主键 UUID 字符串。适配器在 ebus-infrastructure；pi-agent 不依赖 MyBatis。

## UX & Interaction Patterns

- **IA：** 中间会话为主入口；输入框上方快捷栏（选品清单 / 生成上架素材）；右侧预览清单；左侧会话历史 + 套餐入口。行为对齐 `mockups/app.html`（点快捷栏可驱动预览）。
- **视觉：** 工作板与列表，非三列同款圆角卡片墙；主按钮实心店章红（#E11D48）、小圆角 2–4px；冷荧光纸色 + 真黑字；字体 Space Grotesk + Noto Sans SC。
- **澄清 UI：** `human_input_required` 时在会话区展示问题与可点选项（价目/清单气质，非三列孪生卡）；支持可选自由文本；提交后恢复流式；可取消挂起。

## Cross-Story Dependencies

- **增量顺序（重构先行）：** 2.6 → 2.7 → 2.8 → 2.9；2.9 依赖 2.8 的挂起/resume 管道，并复用 2.1–2.4 选品路径。
- **功能链：** 2.1（Run/SSE）+ 2.2（模板）+ 2.3（壳）→ 2.4（生成并结算）；2.5 挂在 Run 结束写成本，与 2.4/后续 Listing 共用。
- **跨史诗：** 依赖 Epic 1 的 JWT、CreditLedger 预占/结算、套餐展示；为 Epic 3 Listing 与 Epic 4 重试/历史提供同一 Run/SSE/Session 约定（本史诗不交付 Listing/重试/60 天历史查询）。
