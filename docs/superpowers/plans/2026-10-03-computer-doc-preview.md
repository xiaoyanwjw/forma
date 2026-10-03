# Computer Doc Preview Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Computer block-component preview with skill-authored `view` documents (`format: markdown | html`), GitHub README styling, sanitizer, and in-document handoff buttons.

**Architecture:** Backend `NormalizeViewProjector` accepts view **v2** (`title` + `format` + `content`) so settle/persist no longer rewrites unknown versions to empty drafts. FE parses v2 only, renders via `DocPreview` (marked → DOMPurify → `markdown-body` + `github-markdown-css`), and delegates clicks on `data-forma-action="handoff"`. Spec `itemHandoffs` / toolbar handoffs retire; Listing media mount/resign write/read `data-forma-media-object-id` inside HTML `content`.

**Tech Stack:** Vue 3 / Vitest / `marked` / `dompurify` / `github-markdown-css` / Java 8 Spring (`NormalizeViewProjector`, `ListingMediaMountSupport`, `HistoryViewResignSupport`) / pi-extension skill `references/output.md`

**Spec:** `docs/superpowers/specs/2026-10-03-computer-doc-preview-design.md`

## Global Constraints

- View protocol **v2 only** on FE: `{ version: 2, title, format: 'markdown'|'html', content: string }` — **no v1 blocks compatibility**
- Handoff buttons authored by skill: `data-forma-action="handoff"`, `data-forma-skill-id`, `data-forma-prompt`
- Click → `startSkillRun({ skillId, text: prompt, sceneCode, sessionId })` with attributes as-is
- Styles: GitHub README (`github-markdown-css` + `markdown-body`)
- Sanitize before `v-html`; allow handoff data attrs; strip `script` / event handlers / `javascript:`
- `final.json` envelope `{ view, artifact }` unchanged; keep `artifact` business fields stable
- Delete Spec `itemHandoffs` / `toolbarHandoffs` and FE handoff builders once skills embed prompts
- FE tests: `cd forma-web && npm test -- --run <paths>`
- BE tests: `mvn -pl forma-application -am test -Dtest=<Class>`

---

## File map

| Path | Responsibility |
|------|----------------|
| `forma-application/.../computer/NormalizeViewProjector.java` | Accept/sanitize v2; stop mapping non-v1 → empty draft |
| `forma-application/.../computer/ComputerDocument.java` | Support v2 map shape (`format`/`content`) |
| `forma-application/.../media/support/ListingMediaMountSupport.java` | Inject hero `<img data-forma-media-*>` into v2 `content` |
| `forma-application/.../history/support/HistoryViewResignSupport.java` | Re-sign `src` on imgs with `data-forma-media-object-id` |
| `forma-application/.../computer/NoSkillMarkdownProjector.java` | Emit v2 markdown (not v1 blocks) |
| `forma-web/src/types/business/computerView.ts` | Replace v1 types with `ComputerDocView` + `parseComputerDocView` |
| `forma-web/src/utils/computerDocHtml.ts` | `sanitizeComputerHtml` + `toPreviewHtml` |
| `forma-web/src/components/business/computer/DocPreview.vue` | GitView shell + sanitized body + handoff emit |
| `forma-web/src/views/business/scene/Workspace.vue` | Use DocPreview; remove item/toolbar handoff wiring |
| `forma-web/src/views/business/history/HistoryView.vue` | Use DocPreview |
| `forma-web/src/composables/agent/useAgentSkillRun.ts` | Parse v2 view |
| `forma-web/src/views/business/scene/{ecommerce,xiaohongshu}/spec.ts` | Drop handoffs |
| `forma-pi-extension/.../scenes/**/references/output.md` | Canonical v2 view examples (+ handoff buttons) |
| Delete / freeze | `ComputerRenderer.vue` block path, `gitDoc.ts` storyboard, `listingHandoff.ts` / `xhsNoteHandoff.ts` (if unused) |

---

### Task 1: Backend — NormalizeViewProjector accepts v2

**Files:**
- Modify: `forma-application/src/main/java/com/xmut/ebus/application/business/computer/ComputerDocument.java`
- Modify: `forma-application/src/main/java/com/xmut/ebus/application/business/computer/NormalizeViewProjector.java`
- Modify: `forma-application/src/main/java/com/xmut/ebus/application/business/computer/NoSkillMarkdownProjector.java`
- Test: `forma-application/src/test/java/com/xmut/ebus/application/business/computer/NormalizeViewProjectorTest.java`
- Test: `forma-application/src/test/java/com/xmut/ebus/application/business/computer/NoSkillMarkdownProjectorTest.java`

**Interfaces:**
- Consumes: raw view `Map` from skill `final.json`
- Produces: projected view map — either legacy v1 blocks **or** v2 `{version:2,title,format,content}` (pass-through sanitize). Invalid input → empty/null policy consistent with existing resolver (do **not** silently convert v2 → draft v1)

- [ ] **Step 1: Write failing tests for v2 normalize**

Add to `NormalizeViewProjectorTest.java`:

```java
@Test
public void project_keepsV2HtmlDocument() {
    Map<String, Object> raw = new LinkedHashMap<String, Object>();
    raw.put("version", Integer.valueOf(2));
    raw.put("title", "选题清单");
    raw.put("format", "html");
    raw.put("content", "<h1>Hi</h1><button data-forma-action=\"handoff\" data-forma-skill-id=\"xhs-note\" data-forma-prompt=\"写笔记\">写成笔记</button>");
    Map<String, Object> out = projector.project(ViewProjectContext.builder().rawView(raw).build());
    assertEquals(Integer.valueOf(2), out.get("version"));
    assertEquals("html", out.get("format"));
    assertEquals("选题清单", out.get("title"));
    assertTrue(String.valueOf(out.get("content")).contains("data-forma-action"));
    assertFalse(out.containsKey("blocks"));
}

@Test
public void project_rejectsBlankV2ContentAsEmptyMap() {
    Map<String, Object> raw = new LinkedHashMap<String, Object>();
    raw.put("version", Integer.valueOf(2));
    raw.put("title", "x");
    raw.put("format", "markdown");
    raw.put("content", "  ");
    Map<String, Object> out = projector.project(ViewProjectContext.builder().rawView(raw).build());
    assertTrue(out.isEmpty());
}
```

**Invalid v2 / unknown version policy (locked):** return `new LinkedHashMap<>()` (empty). Do **not** invent `{version:1,title:draft,blocks:[]}`. Update any agent tests that asserted the old draft fallback for `version!=1`.

Update `NoSkillMarkdownProjectorTest` to expect:

```json
{ "version": 2, "title": "...", "format": "markdown", "content": "..." }
```

- [ ] **Step 2: Run tests — expect FAIL**

```bash
mvn -pl forma-application -am test -Dtest=NormalizeViewProjectorTest,NoSkillMarkdownProjectorTest
```

Expected: FAIL (v2 still rewritten to draft v1 / no-skill still emits blocks)

- [ ] **Step 3: Implement v2 path**

`ComputerDocument` — add factory for v2:

```java
public static Map<String, Object> v2(String title, String format, String content) {
    Map<String, Object> doc = new LinkedHashMap<String, Object>();
    doc.put("version", Integer.valueOf(2));
    doc.put("title", title);
    doc.put("format", format);
    doc.put("content", content);
    return doc;
}
```

Keep existing v1 `toMap()` for block path used by tests that still need it during migration, **or** migrate all callers in this task’s follow-ups.

`NormalizeViewProjector.project`:

```java
if (version == 2) {
    String title = ...; // trim, default "draft"
    String format = raw.get("format") instanceof String ? ((String) raw.get("format")).trim().toLowerCase() : "";
    if (!"markdown".equals(format) && !"html".equals(format)) {
        return new LinkedHashMap<String, Object>();
    }
    String content = raw.get("content") instanceof String ? ((String) raw.get("content")) : "";
    if (!StringUtils.hasText(content.trim())) {
        return new LinkedHashMap<String, Object>();
    }
    // Do not strip tags here; FE sanitizes. Optionally strip null chars only.
    return ComputerDocument.v2(title, format, content);
}
if (version != 1) {
    return new LinkedHashMap<String, Object>();
}
// existing v1 block clean path unchanged until Task 9 deletes FE callers; BE may keep v1 clean for a short window
```

`NoSkillMarkdownProjector`: emit `ComputerDocument.v2(title, "markdown", bodyText)`.

Also update `ComputerViewResolver.diagnoseFailure` copy from “version / title / blocks” → “version / title / format / content”（或 “version=2 文档”）.

Fix `AgentApplicationServiceTest` (or any test) that expected unknown versions to become `{title:draft,blocks:[]}` — they should now expect resolve failure or empty map behavior consistent with resolver.

- [ ] **Step 4: Re-run tests — expect PASS**

```bash
mvn -pl forma-application -am test -Dtest=NormalizeViewProjectorTest,NoSkillMarkdownProjectorTest,ComputerViewResolverTest
```

- [ ] **Step 5: Commit**

```bash
git add forma-application/src/main/java/com/xmut/ebus/application/business/computer \
  forma-application/src/test/java/com/xmut/ebus/application/business/computer
git commit -m "$(cat <<'EOF'
feat(computer): accept view protocol v2 (format+content)

Normalize and no-skill projectors keep markdown/html documents instead of
rewriting unknown versions into empty v1 drafts.
EOF
)"
```

---

### Task 2: Backend — Listing media mount + history resign for v2 HTML

**Files:**
- Modify: `forma-application/src/main/java/com/xmut/ebus/application/business/media/support/ListingMediaMountSupport.java`
- Modify: `forma-application/src/test/java/com/xmut/ebus/application/business/media/support/ListingMediaMountSupportTest.java`
- Modify: `forma-application/src/main/java/com/xmut/ebus/application/business/history/support/HistoryViewResignSupport.java`
- Test: add/extend history resign tests if present; else create `HistoryViewResignSupportTest.java`

**Interfaces:**
- Consumes: projected v2 view map
- Produces: view whose `content` contains  
  `<img data-forma-media-object-id="..." data-forma-media-role="hero" src="https://...">`  
  (replace existing hero img if present; else append before `</article>` or at end of `content`)

- [ ] **Step 1: Failing tests**

```java
@Test
public void injectHeroMedia_patchesV2HtmlContent() {
    Map<String, Object> view = new LinkedHashMap<String, Object>();
    view.put("version", 2);
    view.put("title", "上架");
    view.put("format", "html");
    view.put("content", "<article class=\"markdown-body\"><h1>T</h1></article>");
    ListingMediaMountSupport.injectHeroMedia(view, "m-1", "https://cdn.example/a.png", new LinkedHashMap<String, Object>());
    String content = String.valueOf(view.get("content"));
    assertTrue(content.contains("data-forma-media-object-id=\"m-1\""));
    assertTrue(content.contains("data-forma-media-role=\"hero\""));
    assertTrue(content.contains("https://cdn.example/a.png"));
    assertFalse(view.containsKey("blocks"));
}

@Test
public void resignView_refreshesImgSrcByMediaObjectId() {
    Map<String, Object> view = new LinkedHashMap<String, Object>();
    view.put("version", 2);
    view.put("format", "html");
    view.put("title", "t");
    view.put("content", "<img data-forma-media-object-id=\"m-1\" src=\"https://old.example/x\">");
    // mock MediaStore to return fresh URL
    Map<String, Object> out = support.resignView(view, "user-1");
    assertTrue(String.valueOf(out.get("content")).contains("https://fresh.example/y"));
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
mvn -pl forma-application -am test -Dtest=ListingMediaMountSupportTest,HistoryViewResignSupportTest
```

- [ ] **Step 3: Implement**

In `injectHeroMedia`:

```java
Object version = view.get("version");
if (version instanceof Number && ((Number) version).intValue() == 2) {
    String content = view.get("content") instanceof String ? (String) view.get("content") : "";
    String img = "<img data-forma-media-object-id=\"" + mediaObjectId
        + "\" data-forma-media-role=\"hero\" alt=\"\" src=\"" + readUrl + "\">";
    // If content already has data-forma-media-role="hero", replace that <img ...> via simple regex
    // else insert before </article> if present, else append
    view.put("content", patched);
    view.remove("blocks");
    return;
}
// keep existing blocks path temporarily unused by new skills
```

In `HistoryViewResignSupport.resignView`: if `version==2` and `content` is String, find `data-forma-media-object-id="..."`, issue read URL, replace that img’s `src`. Keep v1 blocks loop for now or delete if no callers remain after skill cutover (prefer: support both until Task 8).

- [ ] **Step 4: Tests PASS + commit**

```bash
mvn -pl forma-application -am test -Dtest=ListingMediaMountSupportTest,HistoryViewResignSupportTest
git add forma-application/src/main/java/com/xmut/ebus/application/business/media \
  forma-application/src/main/java/com/xmut/ebus/application/business/history \
  forma-application/src/test/java/com/xmut/ebus/application/business
git commit -m "$(cat <<'EOF'
feat(computer): mount and resign media inside v2 HTML content

Listing placeholder images and history URL refresh target
data-forma-media-object-id imgs instead of media blocks.
EOF
)"
```

---

### Task 3: FE — `ComputerDocView` types + parser (v2 only)

**Files:**
- Modify: `forma-web/src/types/business/computerView.ts` (replace or add parallel exports; prefer **replace** public API with v2)
- Modify: `forma-web/src/types/business/agent.ts` — `GenerationArtifactPayload.view: ComputerDocView`
- Create: `forma-web/src/types/business/computerView.test.ts` (or extend existing parse tests; delete old parseComputerDocument tests)

**Interfaces:**
- Produces:

```ts
export type ComputerDocFormat = 'markdown' | 'html'

export interface ComputerDocView {
  version: 2
  title: string
  format: ComputerDocFormat
  content: string
}

export function parseComputerDocView(raw: unknown): ComputerDocView | null
```

- [ ] **Step 1: Failing tests**

```ts
import { describe, expect, it } from 'vitest'
import { parseComputerDocView } from './computerView'

describe('parseComputerDocView', () => {
  it('parses html v2', () => {
    expect(
      parseComputerDocView({
        version: 2,
        title: '选题',
        format: 'html',
        content: '<p>a</p>',
      }),
    ).toEqual({
      version: 2,
      title: '选题',
      format: 'html',
      content: '<p>a</p>',
    })
  })

  it('rejects v1 blocks', () => {
    expect(
      parseComputerDocView({ version: 1, title: 'x', blocks: [] }),
    ).toBeNull()
  })

  it('rejects bad format', () => {
    expect(
      parseComputerDocView({ version: 2, title: 'x', format: 'pdf', content: 'a' }),
    ).toBeNull()
  })
})
```

- [ ] **Step 2: Run — FAIL**

```bash
cd forma-web && npm test -- --run src/types/business/computerView.test.ts
```

- [ ] **Step 3: Implement parser; remove/stop exporting `parseComputerDocument` + block types used only by old renderer** (if other files still import them, leave stubs until Task 6–8 — prefer updating imports in same commit only where needed for typecheck of this module’s tests)

```ts
export function parseComputerDocView(raw: unknown): ComputerDocView | null {
  if (!isRecord(raw)) return null
  if (raw.version !== 2) return null
  if (typeof raw.title !== 'string' || !raw.title.trim()) return null
  if (raw.format !== 'markdown' && raw.format !== 'html') return null
  if (typeof raw.content !== 'string' || !raw.content.trim()) return null
  return {
    version: 2,
    title: raw.title.trim(),
    format: raw.format,
    content: raw.content,
  }
}
```

- [ ] **Step 4: PASS + commit**

```bash
cd forma-web && npm test -- --run src/types/business/computerView.test.ts
git add forma-web/src/types/business/computerView.ts \
  forma-web/src/types/business/computerView.test.ts \
  forma-web/src/types/business/agent.ts
git commit -m "$(cat <<'EOF'
feat(web): parse Computer view protocol v2 only

Drop FE reliance on blocks; artifact_ready carries format+content docs.
EOF
)"
```

---

### Task 4: FE — sanitize + `toPreviewHtml`

**Files:**
- Modify: `forma-web/package.json` — add `dompurify`, `github-markdown-css`, `@types/dompurify` (dev)
- Create: `forma-web/src/utils/computerDocHtml.ts`
- Create: `forma-web/src/utils/computerDocHtml.test.ts`

**Interfaces:**
- Produces:

```ts
export function sanitizeComputerHtml(dirty: string): string
export function toPreviewHtml(format: ComputerDocFormat, content: string): string
```

- [ ] **Step 1: Install deps**

```bash
cd forma-web && npm install dompurify github-markdown-css && npm install -D @types/dompurify
```

- [ ] **Step 2: Failing tests**

```ts
import { describe, expect, it } from 'vitest'
import { sanitizeComputerHtml, toPreviewHtml } from './computerDocHtml'

it('keeps handoff data attributes', () => {
  const html = sanitizeComputerHtml(
    '<button type="button" data-forma-action="handoff" data-forma-skill-id="xhs-note" data-forma-prompt="请写笔记">写成笔记</button>',
  )
  expect(html).toContain('data-forma-action="handoff"')
  expect(html).toContain('data-forma-skill-id="xhs-note"')
  expect(html).toContain('data-forma-prompt="请写笔记"')
})

it('strips script and onclick', () => {
  const html = sanitizeComputerHtml('<p onclick="alert(1)">x</p><script>alert(2)</script>')
  expect(html).not.toMatch(/script/i)
  expect(html).not.toMatch(/onclick/i)
})

it('renders markdown then sanitizes', () => {
  const html = toPreviewHtml('markdown', 'Hello **x**')
  expect(html).toMatch(/<strong>x<\/strong>/)
})
```

- [ ] **Step 3: Run — FAIL**

```bash
cd forma-web && npm test -- --run src/utils/computerDocHtml.test.ts
```

- [ ] **Step 4: Implement**

```ts
import DOMPurify from 'dompurify'
import { marked } from 'marked'
import type { ComputerDocFormat } from '@/types/business/computerView'

const PURIFY = {
  USE_PROFILES: { html: true },
  ADD_TAGS: ['button'],
  ADD_ATTR: [
    'data-forma-action',
    'data-forma-skill-id',
    'data-forma-prompt',
    'data-forma-media-object-id',
    'data-forma-media-role',
    'type',
  ],
  ALLOWED_URI_REGEXP: /^(?:(?:https?|mailto):|[^a-z]|[a-z+.\-]+(?:[^a-z+.\-:]|$))/i,
}

export function sanitizeComputerHtml(dirty: string): string {
  return DOMPurify.sanitize(dirty, PURIFY)
}

export function toPreviewHtml(format: ComputerDocFormat, content: string): string {
  const raw =
    format === 'markdown'
      ? String(marked.parse(content, { async: false })).trim()
      : content
  return sanitizeComputerHtml(raw)
}
```

Tune allowlists if tests show over-stripping of tables/headings (prefer starting from DOMPurify HTML profile + ADD_*).

- [ ] **Step 5: PASS + commit**

```bash
cd forma-web && npm test -- --run src/utils/computerDocHtml.test.ts
git add forma-web/package.json forma-web/package-lock.json \
  forma-web/src/utils/computerDocHtml.ts \
  forma-web/src/utils/computerDocHtml.test.ts
git commit -m "$(cat <<'EOF'
feat(web): sanitize Computer md/html for DocPreview

Use marked + DOMPurify with allowlisted Adam handoff/media data attrs.
EOF
)"
```

---

### Task 5: FE — `DocPreview.vue`

**Files:**
- Create: `forma-web/src/components/business/computer/DocPreview.vue`
- Create: `forma-web/src/components/business/computer/DocPreview.test.ts`
- Reuse: `GitView.vue` as chrome

**Interfaces:**
- Consumes: `document: ComputerDocView`, optional `fileName?: string`
- Produces emit: `handoff: [{ skillId: string; prompt: string }]`

- [ ] **Step 1: Failing component tests**

```ts
it('renders sanitized html inside git-view', async () => {
  // mount DocPreview with format html content '<h1>Hi</h1>'
  expect(root.querySelector('[data-testid="git-view"]')).toBeTruthy()
  expect(root.querySelector('.markdown-body')?.innerHTML).toContain('Hi')
})

it('emits handoff from data-adam button click', async () => {
  // content with handoff button; click; expect emit payload
})

it('does not emit when required attrs missing', async () => {
  // button without data-forma-prompt → no emit
})
```

- [ ] **Step 2: Run — FAIL**

```bash
cd forma-web && npm test -- --run src/components/business/computer/DocPreview.test.ts
```

- [ ] **Step 3: Implement**

```vue
<script setup lang="ts">
import { computed } from 'vue'
import type { ComputerDocView } from '@/types/business/computerView'
import { toPreviewHtml } from '@/utils/computerDocHtml'
import GitView from './GitView.vue'
import 'github-markdown-css/github-markdown-light.css'

const props = defineProps<{ document: ComputerDocView; fileName?: string }>()
const emit = defineEmits<{ handoff: [{ skillId: string; prompt: string }] }>()

const html = computed(() => toPreviewHtml(props.document.format, props.document.content))
const name = computed(() => (props.fileName || '').trim() || `${props.document.title || 'doc'}.md`)

function onClick(e: MouseEvent) {
  const t = e.target
  if (!(t instanceof Element)) return
  const btn = t.closest('[data-forma-action="handoff"]') as HTMLElement | null
  if (!btn) return
  e.preventDefault()
  const skillId = btn.getAttribute('data-forma-skill-id')?.trim() || ''
  const prompt = btn.getAttribute('data-forma-prompt') || ''
  if (!skillId || !prompt.trim()) return
  emit('handoff', { skillId, prompt })
}
</script>

<template>
  <GitView :file-name="name">
    <div
      class="markdown-body"
      data-testid="doc-preview-body"
      @click="onClick"
      v-html="html"
    />
  </GitView>
</template>
```

- [ ] **Step 4: PASS + commit**

```bash
cd forma-web && npm test -- --run src/components/business/computer/DocPreview.test.ts
git add forma-web/src/components/business/computer/DocPreview.vue \
  forma-web/src/components/business/computer/DocPreview.test.ts
git commit -m "$(cat <<'EOF'
feat(web): add DocPreview for Computer md/html documents

GitHub README chrome with delegated handoff button clicks.
EOF
)"
```

---

### Task 6: Wire Workspace + skill run + History; remove Spec handoffs

**Files:**
- Modify: `forma-web/src/composables/agent/useAgentSkillRun.ts` — `parseComputerDocView`
- Modify: `forma-web/src/views/business/scene/Workspace.vue`
- Modify: `forma-web/src/views/business/history/HistoryView.vue`
- Modify: `forma-web/src/views/business/scene/workspace/types.ts` — delete handoff types
- Modify: `forma-web/src/views/business/scene/ecommerce/spec.ts`
- Modify: `forma-web/src/views/business/scene/xiaohongshu/spec.ts`
- Modify: `forma-web/src/views/business/scene/workspace/registry.test.ts`
- Modify tests: `EcommerceWorkspace.test.ts`, `XiaohongshuWorkspace.test.ts`, `HistoryView.test.ts` — replace `item-action-btn` / `break-note-handoff` expectations with DocPreview handoff clicks; SSE fixtures use v2 views

**Interfaces:**
- Workspace `@handoff` handler:

```ts
async function onDocHandoff(payload: { skillId: string; prompt: string }) {
  const text = payload.prompt.trim()
  const skillId = payload.skillId.trim()
  if (!text || !skillId || sessionBusy.value) return
  // same as former onItemAction start path: user bubble + startSkillRun({ text, skillId, sceneCode, sessionId })
}
```

- Remove: `activeItemHandoff`, `itemActionLabel`, `onItemAction`, `breakToolbar`, `onBreakToolbar`, `XHS_BREAK_TOOLBAR`, `buildXhsBreakToolbarText`
- `inferPaneFromView(view)`: title-only heuristics (no `blocks`):

```ts
function inferPaneFromView(view: ComputerDocView | undefined): string | null {
  if (!view || !spec.value) return null
  const title = view.title || ''
  if (spec.value.sceneCode === 'ecommerce') {
    if (/上架|listing/i.test(title)) return 'listing'
    if (/选品|清单/i.test(title)) return 'picks'
    return null
  }
  if (/拆解|爆文/.test(title)) return 'break'
  if (/笔记/.test(title)) return 'note'
  if (/选题/.test(title)) return 'topiclist'
  return null
}
```

- `successText(pane, _unused)`: drop `blockCount` branching; always use pane success copy (or `content.trim().length > 0`)

- [ ] **Step 1: Update one failing workspace test first (TDD anchor)**

In `XiaohongshuWorkspace.test.ts`, change topiclist artifact fixture to v2 HTML with handoff button; assert click posts `xhs-note` with prompt from attribute. Remove assertions on `.item-action-btn`.

- [ ] **Step 2: Run targeted test — FAIL**

```bash
cd forma-web && npm test -- --run src/views/business/scene/XiaohongshuWorkspace.test.ts
```

- [ ] **Step 3: Implement Workspace/History/spec/run wiring**

Template swap:

```vue
<DocPreview
  v-if="activeComputerDoc"
  :document="activeComputerDoc"
  :file-name="computerFileName"
  @handoff="onDocHandoff"
/>
```

- [ ] **Step 4: Fix remaining FE tests to v2 fixtures; run suites**

```bash
cd forma-web && npm test -- --run \
  src/views/business/scene/EcommerceWorkspace.test.ts \
  src/views/business/scene/XiaohongshuWorkspace.test.ts \
  src/views/business/scene/Workspace.test.ts \
  src/views/business/history/HistoryView.test.ts \
  src/views/business/scene/workspace/registry.test.ts
```

Helper for fixtures:

```ts
function docView(partial: { title: string; format?: 'html' | 'markdown'; content: string }) {
  return {
    version: 2 as const,
    title: partial.title,
    format: partial.format ?? 'html',
    content: partial.content,
  }
}
```

- [ ] **Step 5: Commit**

```bash
git add forma-web/src/views/business/scene forma-web/src/views/business/history \
  forma-web/src/composables/agent/useAgentSkillRun.ts
git commit -m "$(cat <<'EOF'
feat(web): render Computer via DocPreview and in-doc handoffs

Retire Spec item/toolbar handoffs; skill-run parses view v2 only.
EOF
)"
```

---

### Task 7: Skill templates — list skills with handoff buttons (html)

**Files (canonical):**
- Modify: `forma-pi-extension/src/main/resources/scenes/ecommerce/ecommerce-picklist/references/output.md`
- Modify: `forma-pi-extension/src/main/resources/scenes/xiaohongshu/xhs-topiclist/references/output.md`
- Modify matching `SKILL.md` checklists that say `view.version = 1`
- Mirror copies under `forma-application/src/test/resources/scenes/**` and `pi-agent/src/test/resources/scenes/**` if present (keep in sync)

**Interfaces:**
- `view.json` example becomes v2 HTML. Each list item includes a button. Prompt text must match former FE builders:

Picklist button prompt pattern (from `buildListingHandoffText`):

```text
请为商品「{title}」生成上架素材。
原链：{https href}
来源选品条目：{id}
参考：{niche?}
痛点：{pain?}
角度：{angle?}
```

Topiclist button prompt pattern (from `buildXhsNoteHandoffText`):

```text
请根据选题「{title}」（条目 {id}）写一篇小红书种草笔记，语气像真人分享。
角度：{angle?}
钩子：{hook?}
原笔记：{https?}
```

Example `view` snippet for topiclist:

```json
{
  "version": 2,
  "title": "Mac Mini 桌搭 · 居家办公种草选题清单",
  "format": "html",
  "content": "<article class=\"markdown-body\"><h1>…</h1><ol><li><p><strong>标题</strong></p><p>视角：…</p><p><button type=\"button\" data-forma-action=\"handoff\" data-forma-skill-id=\"xhs-note\" data-forma-prompt=\"请根据选题「…」（条目 tp-1）写一篇小红书种草笔记，语气像真人分享。\">写成笔记</button></p></li></ol></article>"
}
```

**Escaping:** In JSON examples, escape quotes inside `data-forma-prompt`. Instruct the model to HTML-escape attribute values (`&quot;` etc.) when writing real `view.json`.

- [ ] **Step 1: Rewrite picklist + topiclist `output.md` / SKILL checklists to v2**
- [ ] **Step 2: Sync test resource copies**
- [ ] **Step 3: Commit**

```bash
git add forma-pi-extension/src/main/resources/scenes \
  forma-application/src/test/resources/scenes \
  pi-agent/src/test/resources/scenes
git commit -m "$(cat <<'EOF'
docs(skills): emit Computer view v2 HTML for picklist and topiclist

Embed handoff buttons with full prompts; drop blocks examples.
EOF
)"
```

---

### Task 8: Skill templates — note / break / listing (markdown + html mix)

**Files:**
- `.../xiaohongshu/xhs-note/references/output.md` — prefer **`format: markdown`** (acceptance: at least one markdown path)
- `.../xiaohongshu/xhs-break/references/output.md` — HTML including 「按骨架写笔记」 button with prompt from former `buildXhsBreakNoteHandoffText`
- `.../ecommerce/ecommerce-skulist/references/output.md` — plan + exec views as HTML documents (storyboard sections as headings); include placeholder hero img hook:

```html
<img data-forma-media-role="hero" alt="主图占位" src="">
```

(so Task 2 mount can patch it)

- Update SKILL.md version checklists to v2
- Sync test resource trees

- [ ] **Step 1: Rewrite three skills’ output contracts + examples**
- [ ] **Step 2: Grep leftover `view.version = 1` / `"blocks"` in those skill folders — must be zero for active contracts**

```bash
rg 'view\.version` = `1`|"blocks"' forma-pi-extension/src/main/resources/scenes -g '*.md'
```

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs(skills): migrate note, break, and listing views to v2 docs

Listing storyboard becomes HTML; note uses markdown; break embeds note handoff.
EOF
)"
```

---

### Task 9: Delete dead FE block renderer + handoff utils

**Files:**
- Delete: `ComputerRenderer.vue`, `ComputerRenderer.test.ts` (if fully unused)
- Delete or gut: `gitDoc.ts`, `gitDoc.test.ts` storyboard helpers
- Delete: `listingHandoff.ts`, `listingHandoff.test.ts`, `xhsNoteHandoff.ts`, `xhsNoteHandoff.test.ts` if no imports remain
- Remove obsolete CSS for `.item-action-btn` if only used by old renderer
- Update `sessionReplay.ts` artifact-dump detector if it keys on `"blocks"` — also accept `"format"` / `"content"`

- [ ] **Step 1: `rg` for remaining imports of deleted symbols; fix**
- [ ] **Step 2: Full FE related tests + typecheck**

```bash
cd forma-web && npm test -- --run src/components/business/computer src/views/business/scene src/views/business/history src/utils/computerDocHtml.test.ts src/types/business/computerView.test.ts
cd forma-web && npm run type-check
```

- [ ] **Step 3: Backend suite for computer/media/history**

```bash
mvn -pl forma-application -am test -Dtest=NormalizeViewProjectorTest,NoSkillMarkdownProjectorTest,ComputerViewResolverTest,ListingMediaMountSupportTest,HistoryViewResignSupportTest
```

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
refactor(web): remove Computer blocks renderer and Spec handoff helpers

DocPreview + skill-authored HTML are the only Computer preview path.
EOF
)"
```

---

## Spec coverage self-check

| Spec requirement | Task |
|------------------|------|
| view v2 `format`+`content` | 1, 3, 7, 8 |
| md \| html平权 | 4, 5, 8 (note=markdown) |
| GitHub README 皮 | 5 |
| DOMPurify + handoff attrs | 4, 5 |
| Skill-authored handoff buttons + full prompt | 7, 8 |
| FE click → startSkillRun | 5, 6 |
| Remove itemHandoffs / toolbarHandoffs | 6, 9 |
| All artifacts including listing | 2, 8 |
| No v1 FE compat | 3, 6, 9 |
| artifact envelope unchanged | Global / skill tasks |
| Listing media still works | 2, 8 |

## Placeholder / consistency notes

- Attribute names locked: `data-forma-action`, `data-forma-skill-id`, `data-forma-prompt`, `data-forma-media-object-id`, `data-forma-media-role`
- Parser name locked: `parseComputerDocView` / type `ComputerDocView`
- Component name locked: `DocPreview.vue`
- Backend must land **before** skills emit v2 in environments that settle through `NormalizeViewProjector` (Task 1–2 before relying on Task 7–8 in shared envs)

---
