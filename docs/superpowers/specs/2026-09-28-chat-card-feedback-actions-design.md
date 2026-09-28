# 工作台卡片反馈 + 历史抽屉回放 + 侧栏会话切换（设计）

**Date:** 2026-09-28  
**Status:** accepted  
**Decision:**  
- 卡片交互 = 方案 1（成功 STATUS 卡下重试/赞/踩；踩开底部抽屉）  
- 会话回放 = **R1**（`pi_session` 用户/助手气泡，不 1:1 还原 STATUS 过程卡）  
- 交付范围 = **整份一次做**：卡片反馈 + 历史页抽屉（对话+成果）+ 工作台侧栏切会话  

**Supersedes:** 同日较早的「仅卡片反馈」草稿（已合并进本文）。  

**Related:**  
- Story 3.8 `spec-3-8-重试-质量差-反馈与近-60-天历史.md`  
- Spine AD-6 Feedback / HistoryQuery；AD-7 重试新 Run；pi-agent-slim Session ACL（listRecent 须 application 传 userId）  

---

## 1. Problem

1. 重试/反馈入口在 Computer 顶栏，不符合「跟在 AI 成功卡下 + 赞/踩」心智。  
2. 历史页只能看成果预览，看不到那次对话。  
3. 工作台左侧「会话」仍是占位，无法切回过往会话继续聊。  

约束：前端 STATUS 过程卡主要在内存；库内 Session 消息 ≠ UI 过程卡。本设计采用 **R1**，接受回放为普通气泡。

---

## 2. Goals / Non-goals

### Goals

**A. 卡片反馈**  
1. 成功 STATUS 卡下：`重试`｜`点赞`｜`点踩`。  
2. 赞 →「质量好」立即落库；踩 → 底部抽屉可选短文 →「质量差」；均可改口覆盖；不扣分。  
3. Computer 顶栏结果操作条删除。  

**B. 历史页抽屉**  
4. 点列表条目 → 大抽屉，用 **Tab** 切换：「对话」（R1 会话气泡）｜「成果」（Computer 预览）；默认「成果」（或上次选中，实现自定默认「成果」）。  

**C. 工作台侧栏**  
5. 侧栏本人近 60 天会话列表；点选整页切到该 `sessionId`（对话 R1 + Computer 最近成果）；可继续发送/重试。  
6. 「新任务」清空并开新会话。  

### Non-goals

- STATUS 过程卡像素级回放 / UI 笔录表（R2 后置）。  
- 历史页反馈入口、历史分页、>60 天物理清理。  
- 跨场景侧栏混排运营后台。  

---

## 3. Product flows

### 3.1 卡片反馈

```text
成功 STATUS 卡
  ├─ 重试 → 现有 billed start（原提示词 + 同 session）
  ├─ 赞 → upsert Feedback(质量好)
  └─ 踩 → 抽屉 → upsert Feedback(质量差, 可选短文)
```

### 3.2 历史抽屉

```text
/history 点条目
  → 解析 artifact → run → sessionId
  → 抽屉 Tab「对话」：GET session messages（本人 ACL；无会话则空态）
  → 抽屉 Tab「成果」：GET history artifact detail（view 重签）
  → 同一抽屉内 Tab 切换，不关抽屉
```

### 3.3 侧栏切会话

```text
GET 本人会话列表（60d, scene 可滤）
  → 点选 sessionId
  → 工作台：加载 messages(R1) + 最近 picklist/sku 到 Computer
  → 绑定该 sessionId；可继续发 / 重试
```

---

## 4. Backend

### 4.1 Feedback

- 标签：`质量好` | `质量差`。  
- `(user_id, artifact_id)` **UNIQUE** + **upsert**。  
- 零积分；仅本人 picklist/sku。  
- `GET .../feedbacks?artifactId=`（或详情内嵌）供刷新后恢复赞踩高亮。  

### 4.2 SessionQuery（新建应用读路径）

- `GET /api/v1/sessions`：本人、近 60 天、可选 `sceneCode`；返回 sessionId、标题/摘要、updatedAt、sceneCode。  
- `GET /api/v1/sessions/{id}/messages`：本人校验后返回可展示的 user/assistant 文本气泡（过滤或折叠纯系统噪声，策略在实现计划写死）。  
- ACL：**application 显式传 userId**；禁止仅依赖 adapter 静默过滤。  
- 扩展 `listRecent` / 仓储：必须带 `userId`（或会话索引表）；对齐 Spine「FR12 本人由 application 传 userId」。  

### 4.3 History 与关联

- 历史列表仍以 `ebus_artifact`（picklist/sku、60d）为准。  
- 详情/抽屉：返回 `sessionId`（经 run 关联）；消息走 SessionQuery。  
- 无 session 时：「对话」Tab 空态；「成果」Tab 仍可用。  

---

## 5. Frontend

### 5.1 工作台

- 成功卡下操作条；踩抽屉；移除 Computer `result-actions`。  
- 侧栏：会话列表 + 当前高亮；点选切会话；新任务重置。  
- 切会话时中止进行中的本地 SSE 展示态，避免串流。  

### 5.2 历史页

- 列表点击开抽屉；抽屉内 Tab：「对话」｜「成果」（默认「成果」）。  
- 可保留列表选中态；空会话 / 加载失败人话提示。  

---

## 6. Acceptance（Given/When/Then）

1. Given 成功 STATUS 卡，when 查看卡下，then 有重试/赞/踩，且 Computer 顶栏无旧操作。  
2. Given 成功成果，when 赞或踩（抽屉确认），then 落库对应标签且积分不变；再改另一边则覆盖。  
3. Given 历史列表有成果，when 点开抽屉，then 可用 Tab 在「对话」（气泡或空态）与「成果」预览间切换。  
4. Given 侧栏有过往会话，when 点选，then 工作台切到该 session 的对话与最近成果，并可继续发送。  
5. Given 切到旧会话，when 一键重试且余额≥1，then 新 Run+新预占，旧成果保留。  

---

## 7. Risks / follow-ups

- `pi_session.user_id` 若历史行曾为空：列表 ACL 需补写策略或仅展示已绑定 user 的会话（实现计划选定，禁止静默漏数给他人）。  
- R1 回放观感弱于 STATUS 卡 → 产品文案可提示「过程日志仅当时可见」。  
- R2 UI 笔录后置，记入 deferred 若评审需要。  
