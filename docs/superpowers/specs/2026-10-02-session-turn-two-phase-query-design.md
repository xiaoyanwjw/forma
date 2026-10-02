# Session 回放：按逻辑 run 两段式查询

**Date:** 2026-10-02  
**Status:** accepted  
**Decision:**  
- 对外仍 `GET /api/v1/sessions/{id}/messages` → `Page<SessionTurnDTO>`  
- 内部分两段：① 按时间（`tipSeq=MAX(seq)`）分页逻辑 runId → ② 按 runId 列表拉消息 → ③ 组装 turns  
- 逻辑 runId = `SUBSTRING_INDEX(run_id, ':', 1)`（去掉 `:suspend` / `:resume`）  
- 无 `run_id` 的消息不进回放  
- 压缩：查询一律 `seq > compact_anchor_seq`，锚点前当不存在；不为压缩另做特殊规则  

**Related:**  
- 过渡实现（消息窗 + 内存聚类，待替换）：`docs/superpowers/plans/2026-10-02-session-turn-run-clustering.md`  
- Compact 协议 AD-S7：`compact_anchor_seq` 唯一真相，不删 entry  

---

## 1. Problem

按**消息行**分页时，同一次 listing 的策划（`runId:suspend`）与执行（`runId:resume`）可能被拆到两页，前端无法拼完整一轮。

分页单位应是「一次任务 / 一个逻辑 run」，不是每一条 tool 行。

## 2. Goals / Non-goals

**Goals**

1. 后端两段式查库，一次 HTTP 返回已聚合的 `SessionTurnDTO` 页。  
2. 同逻辑 run 的 suspend/resume 消息始终同页同 turn。  
3. 游标稳定、可向前翻更早回合。  
4. 尊重 `compact_anchor_seq` 投影（与 `load` / 现有消息查询一致）。  

**Non-goals**

- 不回放无 `run_id` 的历史行（近端约定写路径必带 runId）。  
- 不为「跨锚点半截 run」做补全或提示。  
- 不改积分 / SSE / 写路径 append。  
- 不让浏览器打两枪（两段只在服务端内部）。  

## 3. Architecture

```text
Controller（薄）
  → SessionQueryService.getMessageList(userId, sessionId, nextToken, limit)
       ACL + 读 compact_anchor（经 repository）
       ① getLogicalRunIds(sessionId, nextToken, limit)
       ② getMessagesByLogicalRunIds(sessionId, runIds)
       ③ SessionTurnAssembler.assemble → Page<SessionTurnDTO>
  → PiSessionQueryRepository
       ← PiSessionQueryRepositoryImpl（实现；调 PiSessionEntryMapper / PiSessionMapper）
         （由现有 `PiSessionQuerySupport` 重命名）
```

前端继续：`Page<SessionTurn>` + `toReplayBubblesFromTurns`；`nextToken` 语义变为 **run tipSeq**（调用形状不变）。

## 4. Pagination

- **分页单位：** 逻辑 run（`SUBSTRING_INDEX(run_id, ':', 1)`）  
- **排序锚：** `tipSeq = MAX(seq)`（该逻辑 run 在可见区内）  
- **页内顺序：** turns 按 tipSeq **升序**（新的在后，对齐聊天）  
- **nextToken：** 本页最老 run 的 `tipSeq`；无更早则空  
- **下一页：** `tipSeq < nextToken`，再取最新 N 个  

例：`limit=2`，runs tipSeq 80 / 40 / 20 → 第一页 `[80,40]` `nextToken=40` → 第二页 `[20]`。

## 5. SQL

逻辑 run 表达式（业务 `run_id` 主体不含 `:`；仅 HITL 后缀 `:suspend` / `:resume`）：

```sql
SUBSTRING_INDEX(run_id, ':', 1)
```

### 5.1 ① 分页逻辑 run

```sql
SELECT logical_run_id, tip_seq
FROM (
  SELECT
    SUBSTRING_INDEX(run_id, ':', 1) AS logical_run_id,
    MAX(seq) AS tip_seq
  FROM pi_session_entry
  WHERE session_id = #{sessionId}
    AND seq > #{compactAnchorSeq}
    AND run_id IS NOT NULL
    AND run_id <> ''
  GROUP BY SUBSTRING_INDEX(run_id, ':', 1)
) t
WHERE (#{nextToken} IS NULL OR tip_seq < #{nextToken})
ORDER BY tip_seq DESC
LIMIT #{limitPlusOne};
```

`limitPlusOne = limit + 1`：多取 1 条判断 `hasMore`；对外只返回 `limit` 条，并设 `nextToken`。

### 5.2 ② 按 run 拉消息

```sql
SELECT id, biz_id, session_id, seq, entry_type, parent_id, run_id, payload, created_at
FROM pi_session_entry
WHERE session_id = #{sessionId}
  AND seq > #{compactAnchorSeq}
  AND run_id IS NOT NULL
  AND run_id <> ''
  AND SUBSTRING_INDEX(run_id, ':', 1) IN
    ( /* 本页 logical_run_id 列表 */ )
ORDER BY seq ASC;
```

## 6. Repository / Service

**`PiSessionQueryRepository` 新增（或替换回放路径）：**

| 方法 | 作用 |
|------|------|
| `getLogicalRunIds(sessionId, nextToken, limit)` | ①；内部读 anchor；返回 `{logicalRunId, tipSeq}[]` + hasMore |
| `getMessagesByLogicalRunIds(sessionId, logicalRunIds)` | ②；同样尊重 anchor |

**`SessionQueryService`：** 去掉「先拉消息窗再 expandOldestRun」补丁；改为 ①→②→Assembler。

**旧 `getMessageList`（按消息 seq 分页）：** 回放路径停用；无其它调用方可删或标 `@Deprecated`。

**Assembler：** 保留；输入已是完整 run 消息时，主要负责 DTO 映射、`userPrompt`（跳过 HITL option）、页内排序。逻辑 strip 与 SQL 一致（防御性仍可 strip）。

## 7. Compaction

- ①② 均带 `seq > compact_anchor_seq`。  
- 锚点前 entry 仍在库，回放不可见。  
- 不单独处理跨锚点半截、不强制排除 `compact-*` 摘要 run（若摘要在锚点后且有 runId，会作为一轮出现；可接受）。  

## 8. API contract（不变外形）

```text
GET /api/v1/sessions/{sessionId}/messages?nextToken=&limit=
→ ApiResponse<Page<SessionTurnDTO>>

SessionTurnDTO:
  runId      // 逻辑 runId
  at
  userPrompt // 首条非 HITL 用户文案
  messages[] // 本 run 全部回放行（含 tool / option 回执）
```

- `limit`：回合数，默认 20，最大 50。  
- `nextToken`：上一页最老 tipSeq 的十进制字符串。  
- ACL：仅本人 session（与现网一致）。  

## 9. Test plan

| 场景 | 期望 |
|------|------|
| listing `:suspend` + `:resume` 同逻辑 id | ① 一个 tipSeq；② 消息齐全；一个 turn |
| 多 run 分页 | limit=1 时 nextToken=该 tipSeq；下一页为更早 run |
| `seq ≤ anchor` 的 run | ① 不出 |
| 无 run_id 行 | ①② 均忽略 |
| 空 session / 非法 token | 空页或空页（与现 parse 行为对齐） |
| 非本人 session | FORBIDDEN |
| Assembler HITL option | 不进 userPrompt，仍在 messages |

## 10. Implementation notes

1. Mapper XML 两段 SQL；`IN` 列表空则 ② 直接返回空。  
2. MySQL `SUBSTRING_INDEX`：约定 run 主体永不含 `:`。  
3. `PiSessionQuerySupport` 重命名为 `PiSessionQueryRepositoryImpl`（实现 `PiSessionQueryRepository`）。  
4. 替换现 `SessionQueryService` 回放实现后，删掉 `expandOldestRun` 等补丁逻辑。  
5. FE 已按 turns 消费；回归 workspace / history / sessionReplay 测试即可。  
