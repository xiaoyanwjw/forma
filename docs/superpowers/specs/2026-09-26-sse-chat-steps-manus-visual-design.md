# 工作台：SSE 过程区视觉对齐 Manus 计划条

日期：2026-09-26  
状态：approved  
范围：电商工作台对话气泡内的 `chat-steps` +「工作过程」disclosure  
前置：已有 AD-4 SSE（`tool_*` / `message_delta`）与中文工具名 / JSON 过滤

## 问题

当前过程区虽已接上真实 SSE，但视觉像「技术日志」：灰边框盒子、右侧「进行中」文案、工作过程与步骤条不像同一套组件。用户期望的是 **Manus / 本仓 mock 计划条** 那种 checklist 观感——**不增加假计划步骤**。

## 目标

1. **内容仍只来自 SSE**：有几条 tool 事件就几行；无工具则无步骤条。  
2. **样式对齐 Manus 计划条**（参考 `manus-chat.css` `.msg .chat-steps`）：chip 底、✓ / 进行中圆点、人话标签。  
3. **「工作过程」**与步骤条同属一块过程区视觉语言（同一 chip 体系 + chevron disclosure）。  
4. 继续隐藏交付 JSON；结果句仍在过程区外。

## 非目标

- 不引入前端「通用过程骨架」或写死选品计划句。  
- 不扩 AD-4 事件名；不改 ArtifactStore / skill / Computer 渐进渲染（Computer 另开一轮）。  
- 不做真浏览器 / 终端 Computer。

## 决策

### 步骤条（`tool_started` / `tool_finished`）

| 项 | 选择 |
|----|------|
| 数据 | 现有 `progressSteps`（label 仍为工具 id；展示用 `toolDisplayLabel`） |
| 容器 | 圆角 chip 底（`--chip` 或等价 `#f5f5f5`），**弱化/去掉**硬描边「卡片」感 |
| 行布局 | 左：✓（完成）或呼吸圆点/省略点（未完成）；右：中文工具名 |
| 去掉 | 行尾「进行中」文字标签 |
| 顺序 | SSE 到达顺序；完成打勾后行仍保留 |

### 工作过程（`message_delta`）

| 项 | 选择 |
|----|------|
| 标题 | 「工作过程」+ chevron（展开/收起），默认仍按 120 字折叠规则 |
| 正文 | `processStreamText` 后的人话；无有效人话可不渲染块或显示短占位 |
| 容器 | 与步骤条同一 chip 色系，垂直间距紧凑，读起来像同一过程区 |

### 对话结构（不变）

```
[ chat-steps：SSE tool 行… ]     ← 视觉像计划 checklist
[ 工作过程 disclosure ]          ← 同套视觉；无 delta 则省略
[ 结果句 text ]                  ← 始终完整在外
```

## 验收

- [ ] 无假步骤；步骤行数 = 本轮 tool 事件数  
- [ ] 步骤条视觉接近 mock `.chat-steps`（chip、✓、无行尾「进行中」）  
- [ ] 「工作过程」为 chevron disclosure，与步骤条同 chip 体系  
- [ ] JSON 仍不进过程正文；结果句在外  
- [ ] Vitest：步骤中文名 + 折叠行为不回归  

## 实现入口

- `ecommerceWorkspaceSession.css`（及气泡模板微调）  
- `EcommerceWorkspacePlaceholder.vue`（步骤行 / disclosure 结构）  
- 既有 `agentProgress` helpers（逻辑基本不动）  
- 视觉参考：`sdd/.../mockups/manus-chat.css` `.msg .chat-steps`
