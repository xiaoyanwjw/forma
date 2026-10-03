# Capsule Skill Selection Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace text-intent matching with explicit quick-bar `selectedSkillId`; selected sends include `skillId`, unselected omit it.

**Architecture:** Each Workspace owns `selectedSkillId`. Capsule click toggles selection and fills example prompt. Send path maps skill→kind when selected; otherwise streams without skillId. Delete `isPicklistIntent` / `isListingIntent` / `detectXhsKind` from send path.

**Tech Stack:** Vue 3 / Vitest / existing `streamAgentRun` / `useAgentSkillRun`

## Global Constraints

- Empty selection may send without `skillId` (spec B)
- Select fills `examplePrompt`; deselect keeps prompt text
- Keep selection after send; clear on new task
- Do not change handoff explicit skill paths
- Do not merge Ecommerce/Xiaohongshu workspaces

---

### Task 1: workspaceKinds skill→kind maps; delete text intent

**Files:**
- Modify: `forma-web/src/views/business/scene/ecommerce/workspaceKinds.ts`
- Modify: `forma-web/src/views/business/scene/xiaohongshu/workspaceKinds.ts`
- Create/update colocated tests if present; else cover via workspace tests

**Interfaces:**
- Produces: `ecommerceKindFromSkillId(skillId?: string | null): EcommerceComputerKind`
- Produces: `xhsKindFromSkillId(skillId?: string | null): XhsComputerKind` (or invert `XHS_SKILL_BY_KIND`)
- Removes: `isListingIntent`, `isPicklistIntent`, `detectXhsKind`

- [x] **Step 1:** Add skill→kind helpers; delete text-intent functions
- [x] **Step 2:** Fix any imports broken by deletions
- [x] **Step 3:** Unit-smoke via existing/new small tests if needed

---

### Task 2: `useAgentSkillRun` optional skillId

**Files:**
- Modify: `forma-web/src/composables/agent/useAgentSkillRun.ts`
- Modify: tests if any require skillId

- [x] **Step 1:** Make `skillId` optional; omit from stream when empty; remove「缺少技能」guard
- [x] **Step 2:** Run related vitest

---

### Task 3: EcommerceWorkspace capsule selection + send

**Files:**
- Modify: `EcommerceWorkspace.vue`
- Modify: `EcommerceWorkspace.test.ts`

- [x] **Step 1:** Add `selectedSkillId`; replace `fillCapsulePrompt` with toggle select
- [x] **Step 2:** Wire pill `active` / `aria-pressed`
- [x] **Step 3:** Rewrite `sendFromSession` per spec (no text intent; map skill→kind; empty → generic run without skillId)
- [x] **Step 4:** Clear `selectedSkillId` on new task
- [x] **Step 5:** Update tests (toggle, skillId present/absent, no barring message)

---

### Task 4: XiaohongshuWorkspace capsule selection + send

**Files:**
- Modify: `XiaohongshuWorkspace.vue`
- Modify: `XiaohongshuWorkspace.test.ts`

- [x] Same pattern as Task 3 for XHS kinds / skillIds

---

### Task 5: Verify

- [x] `npm test -- --run src/views/business/scene/EcommerceWorkspace.test.ts src/views/business/scene/XiaohongshuWorkspace.test.ts src/composables/agent/`
- [x] Grep: no `isPicklistIntent|isListingIntent|detectXhsKind` in `src/`
