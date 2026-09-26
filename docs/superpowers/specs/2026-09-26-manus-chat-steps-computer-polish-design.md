# 工作台：通用 Agent 步骤 / 消息气泡 + Computer 预览样式

日期：2026-09-26  
状态：draft（含 message 折叠；步骤来自 Agent emit）  
范围：电商工作台对话；`ComputerRenderer` 视觉  
前置：AD-4 SSE：`tool_started` / `tool_finished` / `message_delta`

## 问题

1. 对话缺少 Manus 式过程呈现（工具步骤 + 模型消息）。  
2. 完整 `message_delta` 会刷屏，需折叠。  
3. 步骤/消息须来自 **Agent emit**，非场景写死文案。  
4. Computer 清单层次偏平。

## 目标

1. **工具步骤**：`tool_*` → 通用 `chat-steps`（✓ / 进行中），无场景特判。  
2. **模型消息**：累积 `message_delta` 文本，在同一过程区以 **可折叠块**展示（对齐常见 Manus/Agent「Thinking / 过程」折叠交互）。  
3. **Computer**：list 视觉强化（协议不变）。

## 非目标

- 不写死选品步骤句。  
- 不扩 AD-4 事件名（本轮够用）。  
- 不改 ArtifactStore / skill / 后端投影字段。  
- 不做 Markdown 富渲染（纯文本即可）。

## 决策

### 工具步骤

| 项 | 选择 |
|----|------|
| 来源 | `tool_started` / `tool_finished` |
| 展示 | Manus `chat-steps`；完成打 ✓ |
| 文案 | payload 工具名/摘要（可做轻量显示名映射，非场景文案） |

### 模型消息（`message_delta`）

对齐常见 Agent 过程块（Manus 类产品；本仓 mock 无同款折叠）：

| 项 | 选择 |
|----|------|
| 累积 | 同一 run 内 delta 拼成一段 `streamText` |
| 容器 | 过程区「模型输出」折叠条（chevron + 短摘要） |
| 默认 | **超过阈值后默认折叠**；未超阈值可直接全文 |
| 阈值 | **120 字符**（约 2～3 行）；超出显示前 120 字 + `…`，点「展开」看全文，「收起」回折叠 |
| 流式中 | 折叠态下摘要可跟到**最新尾部**或固定头部 120 字（近端：**固定头部 120 字**，避免跳动）；展开态跟到底 |
| 结束后 | 仍可按阈值折叠；用户手动展开状态保留至该气泡 |

结果句（如「已完成选品…」）放在过程块**之外**，始终完整可见。

### Computer

序号 / 优先试徽章 / 四维 pill / 价格带对比；不改 blocks。

## 对话结构（单次 Agent 过程气泡）

```
[ chat-steps：tool 行… ]
[ 可折叠：message 累积文本 ]   ← 无 delta 则整块不渲染
[ 结果句 text ]                ← 业务成功/失败文案
```

无 tool、无超短 message 时：不造假步骤；可只有结果句。

## 验收

- [ ] tool 事件驱动 steps；FE 无选品专用步骤字符串  
- [ ] message_delta 可见；>120 字默认折叠，可展开/收起  
- [ ] ≤120 字不出现折叠控件  
- [ ] Computer 样式达标  
- [ ] Vitest 覆盖 steps + 折叠行为  

## 实现入口

- composable：收集 `tool_*` + 累积 `message_delta`  
- 工作台消息气泡：`chat-steps` + collapsible message  
- `ComputerRenderer.vue`  
- 视觉参考：`manus-chat.css` `.chat-steps`；折叠交互参考常见 Reasoning/Thinking disclosure（展开中流式、结束后可收起）
