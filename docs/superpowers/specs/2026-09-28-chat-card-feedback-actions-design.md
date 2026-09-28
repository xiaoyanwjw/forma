# 对话流 AI 卡片下：重试 / 点赞 / 点踩（设计）

**Date:** 2026-09-28  
**Status:** accepted  
**Decision:** 方案 1 — 操作条挂在选品/Listing **成功** STATUS 卡片下方；点踩开底部抽屉；点赞/点踩落库且可改口覆盖；Computer 顶栏结果操作全部移除。  
**Related:**  
- Story 3.8 / `sdd/implementation-artifacts/spec-3-8-重试-质量差-反馈与近-60-天历史.md`  
- Spine AD-6 Feedback；AD-7 重试 = 新 GenerationRun + 新预占  

---

## 1. Problem

3.8 已落地「重试 + 质量差反馈」，但入口在 Computer 顶栏（重试 / 质量差 / 短文框），与对话流成功卡片脱节，也不符合「赞 / 踩」心智。需要把操作挪到 AI 成功卡下方，并支持点赞落库与点踩抽屉。

---

## 2. Goals / Non-goals

### Goals

1. 成功 STATUS 卡下方出现：`重试`｜`点赞`｜`点踩`。  
2. 点赞 → 立即提交标签「质量好」，不弹抽屉，不扣分。  
3. 点踩 → 底部抽屉（可选短文 ≤512）→ 提交「质量差」，不扣分。  
4. 同一 `(userId, artifactId)` 可改口：后一次 **覆盖** 前一次（upsert）。  
5. Computer 顶栏结果操作条 **整段删除**。  
6. 重试语义不变：一键复用上次提示词 + 同 session，新计费 Run。

### Non-goals

- 失败/进行中卡片下挂操作条。  
- 历史页反馈入口（仍按 3.8：仅工作台）。  
- 运营反馈看板。  
- 点赞再要短文抽屉。  
- 改积分账本 / resume-HITL 语义。

---

## 3. Product flow

```text
选品/Listing 成功 → STATUS 卡底部操作条
  ├─ 重试 → 现有 billed start（新 Run + 新 hold）
  ├─ 点赞 → POST feedback tag=质量好（无短文）→ 高亮赞
  └─ 点踩 → 底部抽屉（可选短文）→ 确认 → tag=质量差 → 高亮踩

再点另一边 → upsert 同一成果反馈行 → UI 切到最新评价
```

---

## 4. Backend

- 允许标签：`质量好` | `质量差`（仅此二者）。  
- `submit`：**upsert** by `(user_id, artifact_id)` —— 有则更新 `tag` / `comment_text` / 时间；无则插入。  
- 建议唯一约束：`UNIQUE (user_id, artifact_id)`（替换「可无限插」的近端行为）。  
- 本人 + picklist/sku；零 CreditLedger。  
- 可选：`GET` 当前用户对该 artifact 的反馈，供刷新后恢复高亮（无则仅会话内记忆亦可，优先能恢复）。

---

## 5. Frontend

- 从 Computer `result-actions` 移除重试/质量差/短文。  
- 在对话流成功 STATUS 卡片模板下渲染操作条（跟随该卡）。  
- 点踩抽屉：遮罩 + 底部面板（短文 + 取消/提交）；提交成功关闭并轻提示。  
- 赞/踩互斥高亮；覆盖后切换高亮。  
- 测：操作条位置、赞无抽屉、踩开抽屉、覆盖、Computer 无旧入口、重试仍走计费流。

---

## 6. Acceptance（Given/When/Then）

1. Given 选品或 Listing 成功 STATUS 卡，when 查看卡片下方，then 可见重试、点赞、点踩，且 Computer 顶栏无同等操作。  
2. Given 成功成果，when 点赞，then 落库「质量好」、无抽屉、积分不变。  
3. Given 成功成果，when 点踩并在抽屉确认（可空短文），then 落库「质量差」、积分不变。  
4. Given 已赞，when 再踩并确认，then 同一成果反馈变为「质量差」（覆盖）。  
5. Given 成功成果，when 点重试且余额≥1，then 新 Run + 新预占路径，旧成果仍在。

---

## 7. Out of scope reminders

- 历史分页 / 清理 Job / 历史页反馈（已 defer 或 3.8 Never）。  
- 失败卡一键重试（可用新一轮发送代替）。
