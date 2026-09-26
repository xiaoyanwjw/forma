# 工作台：通用 Agent 步骤气泡 + Computer 预览样式

日期：2026-09-26  
状态：draft（按反馈修订：步骤来自 Agent emit，非场景文案）  
范围：电商工作台对话；`ComputerRenderer` 视觉  
前置：AD-4 SSE 已含 `tool_started` / `tool_finished` / `message_delta`

## 问题

1. 对话缺少 Manus 式「步骤清单」呈现。  
2. Computer 清单层次偏平。  
3. ~~步骤不应写死选品话术~~ → 必须跟 **Agent 发出的进度事件**，对任意技能通用。

## 目标

1. **通用步骤 UI**：凡 Agent 流式过程中 emit 的进度，在对话里收成带 ✓ 的 `chat-steps`（视觉对齐 Manus）。  
2. **数据源**：复用已有 AD-4 事件，**不**在前端为 ecommerce-picklist 特判文案。  
3. **Computer**：仅样式强化（协议不变）。

## 非目标

- 不新增场景专用步骤字符串表。  
- 本轮不强制新 SSE 事件名（若现有 tool 事件够用则不扩 AD-4）。  
- 不改 ArtifactStore / skill 正文 / 后端投影字段。

## 决策（步骤）

| 项 | 选择 |
|----|------|
| 视觉 | Manus `chat-steps`（✓ + 浅底块） |
| 文案来源 | **Agent emit**，非 FE 场景模板 |
| 近端映射 | `tool_started` → 一步「进行中」；`tool_finished` → 该步打 ✓。展示名取 payload 中的工具名/摘要（规范化显示，如 `read_skill` → 可读短名或原名） |
| 无 tool 事件时 | 不造假步骤；可仅保留结果句 / thinking |
| 与场景关系 | 选品 / 以后 sku / 空跑 同一套组件 |

可选增强（非必须）：若日后 Agent 发结构化 `message_delta` 含 `steps: string[]`，同一 UI 直接渲染。

## 对话行为

1. 用户发送 → Agent 气泡：thinking / 进行中。  
2. SSE 收到 `tool_*` → 同一气泡（或紧随进度区）**追加/更新**步骤行（通用列表）。  
3. 成功结束 → 步骤保留为 ✓ 清单 + 结果句（选品成功文案仍可场景相关，但**步骤行本身**只反映 emit）。  
4. 失败 → 已出现的步骤可保留；未完成项不标假 ✓。

## Computer 样式

同前：序号、优先试徽章、四维 pill、价格带对比；不改 blocks。

## 验收

- [ ] 步骤文案来自 SSE tool 事件（或约定结构化 delta），FE 无「解析品类与客单价」等写死选品句  
- [ ] 换技能只要 emit `tool_*`，同 UI 可展示  
- [ ] Computer 样式验收同前  
- [ ] Vitest：mock `tool_started`/`tool_finished` 出现 steps；无 tool 时无假步骤块  

## 实现入口

- `useAgentPicklistRun`（及通用 run composable）：收集 tool 事件为 `steps[]`  
- `EcommerceWorkspacePlaceholder.vue`：渲染 `chat-steps`  
- `ComputerRenderer.vue`：样式  
- 参考 mock：`manus-chat.css` `.chat-steps`
