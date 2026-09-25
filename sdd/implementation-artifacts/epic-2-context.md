# Epic 2 Context: 对话生成选品清单

<!-- Compiled from planning artifacts. Edit freely. Regenerate with compile-epic-context if planning docs change. -->

## Goal

让已登录用户在 Agent 里选品类模板、点「选品清单」，流式看到约 8–12 条带可卖理由的候选；成功才扣 1 积分，失败释放预占不扣。落地 GenerationRun + 闭合 SSE、Pi `AgentSession`、CatalogTemplate，以及中间对话壳与右侧清单预览，打通「不知道卖啥 → 拿到候选清单」主旅程，并为 Listing（Epic 3）与成本计量留下可接线形状。

## Stories

- Story 2.1: GenerationRun 与 SSE 事件骨架
- Story 2.2: 品类模板（三档通用）
- Story 2.3: Agent 对话壳与右侧预览
- Story 2.4: 生成选品清单并结算 1 积分
- Story 2.5: 单次生成模型成本可统计

## Requirements & Constraints

- 选模板（及必要简单输入）后生成选品清单；成功约 8–12 条候选，每条至少一句可读可卖理由；清单必带 `templateId`。
- 全部已上线品类模板对免费/Pro/Plus 均可用，不按套餐解锁；未上线对用户不可见；新品类由运营上新。
- 积分：不足不能开始并人话提示升级或等重置；成功产出可用清单后扣 1；失败且无可用成果则释放预占、不扣。
- 选品理由须可读、无明显违规胡编；失败给额度不足/服务繁忙/输入不完整等人话原因；SSE 失败用 `run_failed`，不静默断流。
- 每次计费生成（含后续 Listing）须能汇总模型成本（runId、用途 picklist/listing、模型标识、token/调用量或桩占位、估算成本或原始用量）；成本不替代账本，前端用户不必看见。
- 本 Epic 不做 Listing 出图/导出、重试覆盖策略落地、近 60 天历史只读查询（分别属 Epic 3/4）；快捷栏可留「生成上架素材」入口形状，实现后置。

## Technical Decisions

- 计费生成走 SSE；浏览器 **fetch + ReadableStream + JWT**，不用原生 EventSource。闭合事件名仅：`run_started` | `message_delta` | `tool_started` | `tool_finished` | `artifact_ready` | `run_failed` | `run_settled`。
- `GenerationRun` 关联 holdId + sessionId + artifact 引用；每次计费 = 新 Run + 新预占；可复用同一 `AgentSession`，不得复用旧 hold。
- 结算顺序：可用成果持久化 → application 调 CreditLedger 结算 → 再发 `artifact_ready` / `run_settled`；**禁止**仅因流结束扣分。
- 领域分家：AgentRuntime（会话/Run/SSE/`AgentSession`）、CatalogTemplate（模板唯一写者，稳定 UUID `templateId`）、PicklistArtifact（清单与理由）；CreditLedger 仍是唯一写余额者；Agent 工具与前端不得直改积分。
- 模型只经 `lippi-pi-ai`；业务入口 `AgentSession`（`lippi-pi-agent`）；本地可用模型桩，端口形状不变。
- 前端不持模型密钥、不直连大模型、不改账本；REST/SSE 均需 JWT（除公开页）；业务 ID 为 UUID 字符串。
- 成果模块不依赖账本实现细节；由 application 编排「落库 → 结算」。

## UX & Interaction Patterns

- IA：中间会话为主入口；输入框上方快捷栏（选品清单 / 生成上架素材）；右侧预览展示清单；左侧会话历史 + 套餐入口。
- 工具页为工作板与列表，非三列同款圆角卡片墙；主按钮实心店章红 `#E11D48`、小圆角 2–4px。
- 色与字：冷荧光纸色 + 真黑字 + 店章红唯一大胆色；Space Grotesk（标题/数字）+ Noto Sans SC（正文）；禁奶油陶土/紫霓虹/薄荷绿空壳风。
- 行为对齐 `mockups/app.html`：点快捷栏即可驱动右侧预览；品牌展示名 Adam。

## Cross-Story Dependencies

- 依赖 Epic 1：JWT 鉴权 + CreditLedger 预占/结算/释放接口形状已可用。
- 建议顺序：2.1（Run/SSE）与 2.2（模板）可并行；2.3（壳/预览）可与后端并行；2.4 依赖 2.1–2.3；2.5 挂在 Run 结束路径，桩模式也要写可识别计量。
- Epic 3 复用同一 SSE/Run/预占结算与 Agent 壳右侧预览；Epic 4 重试 = 新 Run+新预占且不覆盖旧成果，历史只读聚合成功记录。
