# Epic 2 Context: 对话生成选品清单（含澄清）

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

让已登录用户在 Agent 里选品类模板、点「选品清单」，流式看到约 8–12 条带可卖理由的候选；信息不足时以结构化选项澄清并 `resume` 续跑同一计费 run；成功才扣 1 积分，失败或取消挂起则释放预占不扣。落地 GenerationRun + 闭合 SSE（含 `human_input_required`）、Pi `AgentSession`、MySQL Session/图 Checkpoint、`ask_human`、CatalogTemplate，以及中间对话壳与右侧清单预览——打通「不知道卖啥 → 澄清 → 拿到候选清单」主旅程，并为 Listing（Epic 3）与成本计量留下可接线形状。

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

- 选模板（及必要简单输入）后生成选品清单；成功约 8–12 条候选，每条至少一句可读可卖理由；清单必带 `templateId`。
- 全部已上线品类模板对免费/Pro/Plus 均可用，不按套餐解锁；未上线对用户不可见。
- 积分：不足不能开始并人话提示升级或等重置；成功产出可用清单后扣 1；失败无可用成果则释放预占、不扣。
- 信息不足时可 `ask_human`（问题 + 结构化选项，可选自由文本）；作答前生成挂起并保持预占；作答后同一 hold/run 继续，直至产出或再次询问；用户取消挂起 → 释放预占、不扣分。
- 禁止仅因 SSE 流结束或发出 `human_input_required` 而结算；禁止用新 `prompt` 冒充续跑（无 checkpoint 崩溃降级除外）。
- 选品理由须可读、无明显违规胡编；失败给人话原因；SSE 失败用 `run_failed`，不静默断流。
- 每次计费生成须能汇总模型成本（runId、用途 picklist/listing、模型标识、token/调用量或桩占位、估算成本或原始用量）；成本不替代账本，前端用户不必看见。
- 生产 Session transcript 与图挂起态均落 MySQL、跨实例一致；不与积分账本混表；Session ≠ Graph Checkpoint。
- 本 Epic 不做 Listing 出图/导出、重试覆盖策略、近 60 天历史只读（Epic 3/4）；快捷栏可留「生成上架素材」入口形状，实现后置。本阶段不拆 StateGraph、不删 Skill/斜杠、WRITE 审批默认关。

## Technical Decisions

- 计费生成走 SSE；浏览器 **fetch + ReadableStream + JWT**，不用原生 EventSource。闭合事件名仅：`run_started` | `message_delta` | `tool_started` | `tool_finished` | `human_input_required` | `artifact_ready` | `run_failed` | `run_settled`。
- `GenerationRun` 关联 holdId + sessionId + artifact 引用；每次计费 = 新 Run + 新预占；可复用同一 `AgentSession`，不得复用旧 hold；HITL 多次 ask/resume 仍是一次预占。
- 结算顺序：可用成果持久化 → application 调 CreditLedger 结算 → 再发 `artifact_ready` / `run_settled`。
- 领域分家：AgentRuntime（会话/Run/SSE/`AgentSession`）、CatalogTemplate（模板唯一写者，稳定 UUID `templateId`）、PicklistArtifact（清单与理由）；CreditLedger 仍是唯一写余额者；Agent 工具与前端不得直改积分；成果模块不依赖账本实现，由 application 编排「落库 → 结算」。
- 模型只经 `lippi-pi-ai`；业务入口仅 `AgentSession`（不注入 DefaultAgent/GraphExecutor）；本地可用模型桩，端口形状不变。
- Pi slim：保留 `agent ⇄ tools` StateGraph；生产 SessionStore = MySQL（`pi_session` / `pi_session_entry`，1 行 = 1 Message；compact 仅 `compact_anchor_seq`；`append` 同 session+runId 整批幂等）；生产 Checkpointer = MySQL（`pi_graph_checkpoint`，按 runId）；适配器在 ebus-infrastructure `@Primary`，pi-agent 不依赖 MyBatis；DDL 仅 `APP-META/bootstrap`；禁静默 Sqlite（MissingBean → InMemorySessionStore；无 fail-start 选项）。
- `ask_human` ≠ WRITE 审批：入参至少 `question` + `options[]`，可选 `allowFreeText`；ToolNode HITL 挂起；SSE `human_input_required` 含 runId/sessionId/toolCallId/question/options；`AgentSession.resume` 注入 tool result 后重跑 tools 继续 loop；终态 SUCCESS/FAILED/CANCELLED 删该 run CP，SUSPENDED 保留；建议 `confirmRequestId` 幂等。
- Prompt 保持三槽；注入键仅 `SystemPromptInput` allowlist，禁止新增 contribution/maps SPI；节点/application 不另拼 system。`listRecent` 不在适配器内隐式按 user 过滤，ACL 由 application 显式处理。
- Redis CP 非 Adam 默认；前端断线后可用 runId 查挂起态再订阅；答案走 resume 控制通道。

## UX & Interaction Patterns

- IA：中间会话为主入口；输入框上方快捷栏（选品清单 / 生成上架素材）；右侧预览展示清单；左侧会话历史 + 套餐入口。
- 工具页为工作板与列表，非三列同款圆角卡片墙；主按钮实心店章红 `#E11D48`、小圆角 2–4px。
- 色与字：冷荧光纸色 + 真黑字 + 店章红唯一大胆色；Space Grotesk（标题/数字）+ Noto Sans SC（正文）；禁奶油陶土/紫霓虹/薄荷绿空壳风。
- 行为对齐 `mockups/app.html`：点快捷栏即可驱动右侧预览；品牌展示名 Adam。
- `human_input_required`：会话区展示问题与可点选项（价目/清单气质，非三列孪生卡）；支持可选自由文本；提交后恢复流式生成；可取消挂起。

## Cross-Story Dependencies

- 依赖 Epic 1：JWT 鉴权 + CreditLedger 预占/结算/释放接口形状已可用。
- 增量顺序 **先重构再功能**：2.6（装配/噪音/Prompt 冻结）→ 2.7（Session MySQL）→ 2.8（图 CP/resume）→ 2.9（ask_human + UI）；2.6 不实现 MysqlSessionStore/MysqlCheckpointer。
- 功能主线：2.1（Run/SSE）与 2.2（模板）可并行；2.3（壳/预览）可与后端并行；2.4 依赖 2.1–2.3；2.5 挂在 Run 结束路径，桩模式也要写可识别计量；2.9 还依赖 2.1–2.4 与 2.8。
- Epic 3 复用同一 SSE/Run/预占结算、Agent 壳右侧预览与 HITL 管道；Epic 4 重试 = 新 Run+新预占且不覆盖旧成果，历史只读聚合成功记录（Session 不作历史唯一源之外的替代真相）。
