# Capsule Skill Selection Design

**Date:** 2026-10-03  
**Status:** accepted  
**Decision:** 快捷栏胶囊改为显式选中态；发送时带 `selectedSkillId`，取消字符意图匹配

**Related:**  
- Ecommerce / Xiaohongshu Workspace session shells  
- `workspaceKinds.ts`（电商 / 小红书）  
- `useAgentSkillRun` / `streamAgentRun`

---

## Goal

用户通过快捷栏**选中能力**表达意图，而不是靠输入框文案做正则猜测。

- 点选胶囊 → 选中态 + 填入示例 prompt  
- 再点同一胶囊 → 取消选中，输入框文案保留  
- 选中后发送 → 请求带 `skillId`  
- 未选中发送 → 请求不带 `skillId`，交给服务端

## Non-goals

- 不合并 EcommerceWorkspace / XiaohongshuWorkspace（另议）  
- 不改 SSE / 积分 / ComputerBlock 协议  
- 不改行内手递（做上架素材 / 写成笔记等）的显式 skill 路径  
- 不要求后端在本次必须支持「无 skillId」的新路由语义（API 字段本就可空；服务端行为保持其现网约定）

---

## Decisions locked

| 项 | 选择 |
|----|------|
| 空选发送 | **B**：允许发送，不带 `skillId` |
| 选中填示例 | **A**：选中时写入 `examplePrompt` |
| 取消选中文案 | **A**：输入框原样保留 |
| 实现形态 | **方案 1**：各 Workspace 本地 `selectedSkillId` |
| 发送后选中态 | 保持选中；「新任务」清空 |
| 字符意图 | 从发送路径删除 |

---

## Interaction

每个 Workspace：

```ts
const selectedSkillId = ref<string | null>(null)
```

快捷栏胶囊（单选）：

1. 点未选中的 → `selectedSkillId = skill.skillId`，`sessionPrompt = skill.examplePrompt || ''`  
2. 再点同一颗 → `selectedSkillId = null`，**不改** `sessionPrompt`  
3. 点另一颗 → 切到新 id 并换填其示例  

UI：pill `active` + `aria-pressed`。

发送成功后保持选中；`newTask`（及同等重置）时 `selectedSkillId = null`。

行内手递不读写 `selectedSkillId`。

---

## Send path

`sendFromSession`：

1. 空文案 / busy → return  
2. 模板占位 `【】` 校验保留（本地提示，不发请求）  
3. 入队 user 消息，清空输入框（**不清** `selectedSkillId`）  
4. **不再**调用 `isPicklistIntent` / `isListingIntent` / `detectXhsKind`  
5. 有选中 → 用 `selectedSkillId` 跑计费；无选中 → `streamAgentRun` **省略** `skillId`  
6. 删除「未识别意图 → 前端拦回话」分支（空选也发往服务端）

### Kind 推导（FE 本地）

仅用于 thinking 文案 / 是否提前打开 Computer：

| skillId | kind |
|---------|------|
| `ecommerce-picklist` | `picks` |
| `ecommerce-skulist` | `listing` |
| `xhs-topiclist` | `topiclist` |
| `xhs-note` | `note` |
| `xhs-break` | `break` |

无选中：thinking =「正在处理…」；Computer 等 `artifact_ready` / `artifactType`（沿用 `*KindFromArtifactType`）。

电商选中 picklist/listing 时仍可走现有 `startPicklistRun` / `startListingRun`（其内部已写死对应 skillId）；也可统一改为传入 `skillId` 的通用 run——实现时二选一，行为一致即可。

### `useAgentSkillRun`

允许 `skillId` 可选；缺省时不再前端报「缺少技能」，与 `streamAgentRun` 对齐。

---

## `workspaceKinds.ts` 收敛

**删除（发送用字符匹配）：**

- 电商：`isListingIntent` / `isPicklistIntent`  
- 小红书：`detectXhsKind`

**保留：**

- Computer kind 类型  
- `*KindFromArtifactType`  
- 电商：`previewEcommerceKindFromStatus`（STATUS 卡片点开 Computer 的启发式；若后续也要去掉可另开）  
- 小红书：thinking / success / empty 文案 helper、`XHS_SKILL_BY_KIND`  
- 新增：`skillId → kind` 映射（或把现有 map 反过来用）

---

## Testing

- 点 A 选中 + 示例；再点 A 取消、文案保留；点 B 切换并换示例  
- 选中后发送：body 含对应 `skillId`  
- 空选发送：body **无** `skillId`；无前端「请用上方胶囊」拦回话  
- `【】` 占位仍本地拦截  
- 新任务清空选中；发送成功后选中仍在  
- 手递用例不变（显式 skill）  
- 改写依赖文本意图匹配的旧测试

---

## Out of scope note: merging Workspaces

EcommerceWorkspace 与 XiaohongshuWorkspace **本次不合并**。差异见实现前调研：电商双 run + Listing HITL、演示入口与选品→上架手递；小红书单 `useAgentSkillRun`、三 skill、选题/拆解→笔记手递。壳层已有部分共享组件；若要做统一 `Workspace.vue`，应另开设计（配置驱动 skill 表 + HITL/手递插件），不阻塞本变更。

---

## Approval

交互 §1–§3 已在会话中确认；本文档为落地依据。
