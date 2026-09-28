# Listing 策划 Markdown view — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use executing-plans (or implement tasks in order with TDD where applicable).

**Goal:** 策划阶段 Computer 只渲染一篇 `markdown`；artifact 短字段与执行稿不变。  
**Spec:** `docs/superpowers/specs/2026-09-28-listing-plan-markdown-view-design.md`

## File map

| File | Change |
|------|--------|
| `lippi-ai-ebus-starter/.../ecommerce-skulist/SKILL.md` | 策划 view → 单 markdown |
| `lippi-ai-ebus-starter/.../references/output.md` | mapping + 示例 |
| `lippi-ai-ebus-application/src/test/resources/scenes/.../SKILL.md` + `output.md` | 同步 |
| `lippi-pi-agent/src/test/resources/scenes/.../SKILL.md` + `output.md` | 同步 |
| `docs/superpowers/specs/2026-09-27-listing-storyboard-hitl-design.md` §4.2 | 指向新规约 |
| `docs/superpowers/specs/2026-09-28-listing-plan-markdown-view-design.md` | status → accepted |
| `ComputerRenderer.test.ts` 策划用例（若有） | 改为单 markdown |
| `AgentApplicationServiceTest` VALID_PLAN_JSON* | view 改为单 markdown（仍过门闩） |

## Tasks

### Task 1: Skill + output（starter 真源）
- 改 SKILL 步骤 5：单 markdown + 固定小标题
- 改 output.md 策划 view 表与示例 JSON

### Task 2: 同步 test mirrors
- application / pi-agent test resources 与 starter 对齐

### Task 3: Spec + 测试 fixture
- 父规约 §4.2；design status accepted
- 更新 VALID_PLAN_JSON / ComputerRenderer 策划测例

### Task 4: Verify
- `npm run test -- ComputerRenderer`（或项目既有 vitest 命令）
- `mvn -pl lippi-ai-ebus-application -Dtest=AgentApplicationServiceTest#listing_* test`（抽样）
