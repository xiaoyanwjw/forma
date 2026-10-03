# Computer GitView Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify Computer preview under `GitView` (GitHub README chrome + typography); migrate listing storyboard projection to `gitDoc.ts` (`StoryboardDoc` / `toStoryboardDoc` / `hasStoryboardDoc`); delete `ListingReadmePreview`.

**Architecture:** `ComputerRenderer` always wraps content in `GitView`. Blocks map to README elements inside the canvas. When `hasStoryboardDoc(document)`, renderer uses `toStoryboardDoc` for storyboard + detail copy instead of a separate listing skin component.

**Tech Stack:** Vue 3 / Vite / Vitest / existing `ComputerBlock` protocol / `MarkdownView`

## Global Constraints

- No `Listing*` component or method names in new code
- Do not change SSE / skill / `ComputerBlock` wire protocol
- Do not stringify all blocks to markdown (keep inline action buttons)
- File name: `fileName` prop overrides; else slug(`document.title`) + `.md`
- Section heading contract stays Chinese (`详情标题` etc.); rename constant to `STORYBOARD_SECTION_HEADINGS`
- Spec: `docs/superpowers/specs/2026-10-03-computer-gitview-design.md`

---

### Task 1: `gitDoc.ts` rename + API

**Files:**
- Create: `forma-web/src/components/business/computer/gitDoc.ts`
- Create: `forma-web/src/components/business/computer/gitDoc.test.ts` (move/adapt from `listingPreview.test.ts`)
- Delete: `listingPreview.ts`, `listingPreview.test.ts`
- Modify: any imports of `listingPreview` / `ListingPreviewContent` / `projectListingPreviewContent` / `isListingReadmeDocument` / `listingSectionClass`

**Interfaces:**
- Produces:
  - `StoryboardDoc` type
  - `toStoryboardDoc(blocks: ComputerBlock[]): StoryboardDoc`
  - `hasStoryboardDoc(doc): boolean`
  - `STORYBOARD_SECTION_HEADINGS`
  - `storyboardSectionClass(heading, tone?)` (was `listingSectionClass`)
  - `buildStoryboardBeats` / `parseFramePromptEntries` (unchanged behavior)

- [ ] **Step 1:** Copy `listingPreview.ts` → `gitDoc.ts`; rename exports as above; keep behavior identical
- [ ] **Step 2:** Update `listingPreview.test.ts` → `gitDoc.test.ts` with new names
- [ ] **Step 3:** Fix all imports; delete old files
- [ ] **Step 4:** Run `npm test -- --run src/components/business/computer/gitDoc.test.ts`

---

### Task 2: `GitView.vue` shell

**Files:**
- Create: `forma-web/src/components/business/computer/GitView.vue`
- Create: `forma-web/src/components/business/computer/GitView.test.ts`

**Interfaces:**
- Produces props: `fileName: string`, optional `meta?: string`
- Default slot for body
- Root class: `.git-view`; body: `.git-md`; filebar: `.git-filebar`

- [ ] **Step 1:** Write failing test: mounts with `fileName="picklist.md"` and slot content; asserts `.git-view`, `.git-filebar`, `.git-md`
- [ ] **Step 2:** Implement `GitView` from current `ListingReadmePreview` `.gh-*` styles (rename to `.git-*`)
- [ ] **Step 3:** Tests green

---

### Task 3: Wire `ComputerRenderer` → always `GitView`

**Files:**
- Modify: `ComputerRenderer.vue`
- Modify: `ComputerRenderer.test.ts`
- Modify: ecommerce / xhs / History call sites if adding optional `fileName`
- Delete: `ListingReadmePreview.vue`

**Interfaces:**
- Consumes: `GitView`, `toStoryboardDoc`, `hasStoryboardDoc`, `storyboardSectionClass`
- Props add: `fileName?: string`
- Remove: `presentation?: 'blocks' | 'readme'` (or ignore if present for one release — prefer remove and update call sites)
- Resolve display name: `props.fileName ?? slug(document.title) + '.md'`

- [ ] **Step 1:** Update tests to expect `.git-view` for picklist, markdown plan, and storyboard docs; remove `.gh-readme` / `ListingReadmePreview` / `presentation` assertions
- [ ] **Step 2:** Wrap all renderer output in `<GitView :file-name="resolvedFileName">`
- [ ] **Step 3:** If `hasStoryboardDoc`: render storyboard section from `toStoryboardDoc` (h2 主图分镜 / ol / pre / 详情文案) inside GitView; skip duplicate raw media/section for those fields when in storyboard mode
- [ ] **Step 4:** Else: map blocks to git-md elements (markdown, list with actions, section via `storyboardSectionClass`→generic section classes, media as light figure, note as mute/blockquote)
- [ ] **Step 5:** Remove `ListingReadmePreview` import/usage; delete file
- [ ] **Step 6:** Update ecommerce `computerPresentation` / `isListingReadmeDocument` usages → `hasStoryboardDoc` or drop presentation prop
- [ ] **Step 7:** Run computer + workspace + history tests

---

### Task 4: Scene optional `fileName` + cleanup

**Files:**
- Modify: `EcommerceWorkspace.vue` — pass `fileName` for picks/listing when known
- Modify: `XiaohongshuWorkspace.vue` — pass `fileName` for topic/note/break
- Modify: `HistoryView.vue` — optional omit (slug default)
- Modify: tests asserting platform / presentation leftovers

Suggested names (non-blocking if title slug is fine):
- picks → `picklist.md`
- listing storyboard doc → keep slug from title or `listing.md`
- topiclist → `topiclist.md`
- note → `note.md`
- break → `break.md`

- [ ] **Step 1:** Add computed `computerFileName` per scene
- [ ] **Step 2:** Pass to `ComputerRenderer`
- [ ] **Step 3:** Full related vitest green

---

### Task 5: Verify

- [ ] `npm test -- --run src/components/business/computer/ src/views/business/scene/EcommerceWorkspace.test.ts src/views/business/scene/XiaohongshuWorkspace.test.ts src/views/business/history/HistoryView.test.ts`
- [ ] Grep: no `ListingReadmePreview`, `listingPreview`, `ListingPreviewContent`, `projectListingPreviewContent`, `isListingReadmeDocument`, `.gh-readme` in `src/`
