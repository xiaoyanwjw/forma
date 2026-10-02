# Session turns by runId — Implementation Plan

> **Superseded by** [`2026-10-02-session-turn-two-phase-query.md`](./2026-10-02-session-turn-two-phase-query.md)（真·两段式：先 run 再消息）。本文件保留作过渡实现（消息窗 + expand）记录。

> **For agentic workers:** Implement task-by-task. Steps use checkbox syntax.

**Goal:** Session history API returns run-clustered turns (plan+exec together); FE only renders the structure.

**Architecture:** Same `GET /sessions/{id}/messages` returns `Page<SessionTurnDTO>`. Server loads message windows, clusters by logical runId (`strip :suspend|:resume`), paginates by turn count. FE maps each turn to user+STATUS bubbles.

**Tech Stack:** Java 8 / Spring Boot 2.7, Vue3, existing `pi_session_entry.run_id`

## Global Constraints

- Logical runId = `run_id` with `:suspend` / `:resume` stripped
- HITL `{"optionId":...}` user rows stay in turn.messages for process, not as userPrompt
- Listing `plan/final.json` is not a displayable STATUS; `exec/final.json` / `final.json` / dumps are
- ACL unchanged (own session only)

---

### Task 1: SessionTurnDTO + assembler (TDD)

- [x] Add `SessionTurnDTO` (runId, at, userPrompt, messages)
- [x] Add `SessionTurnAssembler` with unit tests (picklist + listing HITL cluster)
- [x] Wire `SessionQueryService.getMessageList` to return `Page<SessionTurnDTO>` (window fetch + newest N turns)

### Task 2: Controller + FE types

- [x] Controller return type → `Page<SessionTurnDTO>`
- [x] FE `SessionTurn` type + `getSessionMessages` generic
- [x] `toReplayBubblesFromTurns` thin mapper; workspaces use turns

### Task 3: Verify

- [x] Backend SessionQueryServiceTest / assembler tests
- [x] FE sessionReplay + EcommerceWorkspacePlaceholder / XiaohongshuWorkspace tests
