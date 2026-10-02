# Unified Workspace Design

**Date:** 2026-10-03  
**Status:** accepted  
**Decision:** 单一 `Workspace.vue` + `SceneWorkspaceSpec`；电商/小红书共用壳；Run 统一为 skill run（含 HITL）

**Related:**  
- [`2026-10-03-capsule-skill-selection-design.md`](./2026-10-03-capsule-skill-selection-design.md)（发送侧胶囊选中；本设计的子决策）  
- EcommerceWorkspace / XiaohongshuWorkspace（迁移源）  
- `useAgentSkillRun` / `streamAgentRun`

---

## Goal

两个场景工作台合并为**一个** Workspace 壳：会话、聊天、胶囊、Computer、反馈、计费 SSE 共用；场景差异收敛进 **SceneWorkspaceSpec**（配置 + 少量 handoff 函数），而不是两份近千行复制页。

## Non-goals

- 本阶段不把 handoff 文案模板、`priorityBadgeLabel` 强制上收服务端（spec 可硬编码，接口预留）  
- 不改 SSE 事件名、积分账本、`ComputerBlock` 协议  
- 不在本设计内实现第三场景（但 spec 应可扩展）

---

## Decisions locked

| 项 | 选择 |
|----|------|
| 形态 | **方案 1**：一个 `Workspace.vue` + 场景描述表 |
| 路由 | `/scenes/:sceneCode`；旧电商/小红书路径重定向到同一组件 |
| Run | 统一 `useAgentSkillRun`（`skillId` 可选） |
| HITL | **A**：并进通用 skill run；listing 为第一使用者 |
| Computer 槽命名 | **`pane`**（不用 `kind*`） |
| 胶囊发送 | 遵循 capsule-skill-selection design |
| 演示预览 | 删除（历史债） |
| 槽位 | 高亮 + 发送拦截统一为 `「…」` |

---

## Shared vs scene-specific

### 全场景共用（壳）

- AppHeader / 侧栏会话 / 聊天 / 快捷栏 / Computer 分栏 / `workspaceSession.css`
- 会话列表、切换、加载更多、回放、新任务
- 胶囊选中态（`selectedSkillId`）、空选可发、示例填入/取消保留文案
- `useAgentSkillRun`（含 `human_input_required` + resume）
- HITL UI（确认 / 补充）— 任意 skill 触发均可挂载
- ComputerRenderer / GitView、feedback、console expand
- `「…」` 槽位高亮与发送前校验

### 场景描述表（Spec）

```ts
type SceneWorkspaceSpec = {
  sceneCode: string
  breadcrumb: string
  /** 会话切换时并行拉取的制品类型 */
  artifactTypes: string[]
  /** skillId → Computer 槽位 id（右侧打开哪路成果） */
  paneBySkillId: Record<string, string>
  /** 后端 artifactType → Computer 槽位 id */
  paneByArtifactType: Record<string, string>
  /** list 行内手递；无则不出按钮 */
  itemHandoffs?: Array<{
    whenPane: string
    actionLabel: string
    targetSkillId: string
    buildText: (item: ComputerListItem, index: number) => string | null
  }>
  /** Computer 顶栏等额外动作（如拆解→笔记） */
  toolbarHandoffs?: Array<{
    whenPane: string
    actionLabel: string
    targetSkillId: string
    buildText: () => string | null
  }>
  /** 协议 badge=priority 的展示文案；缺省「优先试」 */
  priorityBadgeLabel?: string
}
```

**`pane*` 含义：** 本地 Computer 成果槽 id（如 `picks` / `listing` / `topiclist` / `note` / `break`），不是 skillId，也不是意图猜测。

示例（示意）：

| sceneCode | pane 例 | artifactTypes 例 |
|-----------|---------|------------------|
| ecommerce | picks ← ecommerce-picklist；listing ← ecommerce-skulist | picklist, sku |
| xiaohongshu | topiclist / note / break ← 对应 xhs-* | xhs_topiclist, xhs_note, xhs_break |

---

## Run / HITL / 发送

### Skill run

- `startSkillRun({ text, skillId?, sceneCode, sceneId?, sessionId? })`
- `skillId` 省略时请求体不带该字段（服务端按现网约定处理）
- `human_input_required` → `pendingHuman`；`resumeSkillRun({ optionId, freeText? })`
- 电商 listing 切到此路径后，`useAgentPicklistRun` / `useAgentListingRun` 退出主路径（可删或仅测试过渡）

### 发送

1. 空文案 / busy → return  
2. 若仍含未改完的 `「…」` 槽 → 本地提示，不发  
3. user 入队；清空输入；**保留** `selectedSkillId`  
4. 有选中 → 带 `skillId`，`activePane = paneBySkillId[skillId]`  
5. 无选中 → 不带 `skillId`；thinking「正在处理…」；等 `artifact_ready` + `paneByArtifactType` 再开面板  
6. **无**「未识别意图 → 前端拦回话」

### 手递

命中 `itemHandoffs` / `toolbarHandoffs` → `buildText` + 强制 `targetSkillId` 开跑；不改胶囊选中态。

---

## Routing & cleanup

- 新增：`Workspace.vue`（或 `views/business/scene/Workspace.vue`）  
- Router：`/scenes/:sceneCode` → Workspace；校验 spec 表是否包含该 code，未知则 404/回画廊  
- 兼容：原 `/scenes/ecommerce`、`/scenes/xiaohongshu` 指向同一组件即可（已是 sceneCode path）  
- 删除：`EcommerceWorkspace.vue` / `XiaohongshuWorkspace.vue`（迁移完成后）、演示预览按钮与 DEMO Computer 假数据路径  
- `workspaceKinds.ts`：收敛进各场景 spec 模块（或 `scenes/ecommerce/spec.ts`），去掉发送用字符意图（与 capsule design 一致）

---

## Migration order

1. **引擎**：skill run + HITL；电商 listing 切换  
2. **胶囊选中**：可在旧页先落地（见 capsule design）  
3. **Spec 表**：`paneBy*` / handoffs / artifactTypes  
4. **单页 Workspace** + 测迁移；删双大文件与演示债  
5. **槽位**：统一 `「…」`；删 `【】` 场景正则  

---

## Testing

- 按 `sceneCode` mount 统一 Workspace，迁移电商/小红书行为测  
- Listing HITL 确认/补充仍绿（通用 resume）  
- 胶囊选中 / 空选无 `skillId`  
- 手递：选品→上架、选题/拆解→笔记  
- 会话切换并行拉 `artifactTypes`  
- 路由两场景同组件  
- 无演示预览  

---

## Relation to capsule design

胶囊选中是**发送子决策**（谁带 `skillId`）。本文件是**壳合并总决策**（一个页面 + spec + 通用 run/HITL）。实现计划可分 PR：先引擎/胶囊，再 Workspace 合并。

---

## Approval

§1–§3（含 `pane*` 命名、HITL=A、方案 1）已在会话确认。
