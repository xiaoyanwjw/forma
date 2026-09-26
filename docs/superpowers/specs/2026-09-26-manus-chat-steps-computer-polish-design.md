# 工作台：Manus 式步骤气泡 + Computer 预览样式

日期：2026-09-26  
状态：approved（人已确认方案 1）  
范围：电商工作台对话成功气泡；`ComputerRenderer` 选品/通用 list 视觉  
前置：Computer view 协议已落地；选品 SSE 可成功结算

## 问题

选品成功后对话只有一句结果，缺少 Manus 原型里的「中间步骤」感；Computer 清单可读但层次偏平（四维/徽章/行文不够清晰）。

## 目标

1. **对话（方案 A）**：选品**成功**后，Agent 气泡先展示带 ✓ 的步骤清单，再跟结果句（对齐 UX mock `chat-steps`）。
2. **Computer**：在不改 block 协议的前提下，强化 list 条目视觉（序号、优先试徽章、价格带、四维 pill）。
3. 进行中仍为「正在生成…」；失败不加假 ✓。

## 非目标

- 不接真实 SSE `tool_*` / `message_delta` 步骤流（产品叙事步骤，非审计日志）。
- 不新增 Computer block 类型；不改后端 `PicklistViewProjector` 字段。
- 本轮不做 Listing 专用步骤文案（可后复用同一气泡结构）。
- 积分 meta「已扣 1 积分」：**可选**；近端若前端无可靠余额差则省略，避免假数据。

## 决策

| 项 | 选择 |
|----|------|
| 步骤形态 | A：跑完后 ✓ 清单（非 live SSE） |
| 范围 | 方案 1：对话 steps + ComputerRenderer CSS/结构微改 |
| 步骤文案（选品） | ① 解析品类与客单价 ② 生成 N 个候选并附理由（N=实际条数） |
| 结果句 | 已完成选品。右侧 Adam's Computer 可预览候选清单。 |

## 对话数据形状

扩展消息模型（仅前端）：

```ts
interface ChatMessage {
  id: string
  role: 'user' | 'agent'
  text: string
  steps?: string[]   // 有则渲染 chat-steps
}
```

成功替换 thinking 气泡时填 `steps`；失败/拉回只设 `text`。

样式对齐 mock：`.msg .chat-steps`（浅底、圆角、✓ 绿色）。

## Computer 样式要点

- `.pick-list .n`：更稳的序号栏
- `.priority-tag`：更明显的「优先试」
- `.r`：首行价格带可用略深字重（若 line 以「价格带：」开头则加 class，或全部略提对比）
- `.dims span`：小 pill（边框/浅底），勿挤成一长串灰字
- 头栏 `.status` 保持 mute

## 验收

- [ ] 选品成功气泡含 ≥2 条 ✓ 步骤 + 结果句；失败无步骤块
- [ ] Computer 清单四维为 pill；优先试徽章可见
- [ ] 相关 Vitest 更新并绿
- [ ] 不改 SSE / ArtifactStore / skill

## 实现入口

- `EcommerceWorkspacePlaceholder.vue` + session CSS / scoped
- `ComputerRenderer.vue` (+ test 快照/类名断言)
- 参考：`sdd/.../mockups/manus-chat.css`（`.chat-steps`）、`scene-ecommerce.html`（`stepsHtml`）
