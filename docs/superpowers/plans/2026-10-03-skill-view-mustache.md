# Skill View Mustache Render Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `render_view` so skills fill Mustache templates from `artifact.json` into v2 `view.json`, and settle accepts `{"output":"view.json"}` with a sibling `artifact.json`.

**Architecture:** New Pi tool in `lippi-ai-ebus-pi-extension` loads `references/view.mustache` (or override path) for the active skill, optionally injects display helpers (handoff prompts / stripped titles), renders with jmustache, writes `{version:2,title,format,content}`. `GenerationOutputParser` treats a pointed-to v2 view file as the view and loads `artifact.json` beside it; keep reading legacy `final.json` envelopes.

**Tech Stack:** Java 8 / Spring Boot 2.7 / jmustache / existing `ToolHandler` + `ToolContext` (workspaceRoot, activeSkillId) / `SkillCatalog` + skill `promptRef` / `GenerationOutputParser` / classpath skill resources

**Spec:** `docs/superpowers/specs/2026-10-03-skill-view-mustache-design.md`

## Global Constraints

- Model writes **only** `artifact.json`; HTML/MD comes from Mustache  
- Tool name: **`render_view`**; defaults `artifact=artifact.json`, `out=view.json`, template=`references/view.mustache`  
- View output shape: `{ version: 2, title, format: html|markdown, content }`  
- Dialogue pointer: **`{"output":"view.json"}`** (also `plan/view.json` etc.)  
- Artifact path: **same directory** as the view file, named `artifact.json`  
- Short-term keep **`final.json`** envelope pointer working  
- Mustache **default HTML escape**; no `{{{…}}}` for handoff attrs  
- Helpers (displayTitle / handoffPrompt) are **in-memory only**, not written back to artifact  
- Business tools stay in `lippi-ai-ebus-pi-extension` (not pi-agent)  
- Tests: `mvn -pl lippi-ai-ebus-pi-extension -am test -Dtest=…` and `mvn -pl lippi-ai-ebus-application -am test -Dtest=…`

---

## File map

| Path | Responsibility |
|------|----------------|
| `lippi-ai-ebus-pi-extension/pom.xml` | Add `com.samskivert:jmustache` |
| `…/tool/view/MustacheViewRenderer.java` | Pure: Map/JsonNode + template string → content |
| `…/tool/view/ViewRenderHelpers.java` | Skill-specific in-memory enrichers |
| `…/tool/view/RenderViewToolHandler.java` | `render_view` ToolHandler |
| `…/resources/tools/view/render_view.tool.json` | Tool schema + handlerClass |
| `…/config/ViewToolsConfiguration.java` | Spring `@Bean` for handler |
| `…/scenes/**/references/view.mustache` (+ plan/exec) | Templates |
| `…/scenes/**/SKILL.md` + `output.md` | Agent flow without final.json / hand-written HTML |
| `lippi-ai-ebus-application/.../GenerationOutputParser.java` | Pointer → view file + sibling artifact |
| Test resource mirrors under `application` / `pi-agent` `src/test/resources/scenes` | Keep in sync |

---

### Task 1: Mustache renderer (unit)

**Files:**
- Modify: `lippi-ai-ebus-pi-extension/pom.xml` — add dependency  
- Create: `lippi-ai-ebus-pi-extension/src/main/java/com/xmut/ebus/extension/tool/view/MustacheViewRenderer.java`  
- Create: `lippi-ai-ebus-pi-extension/src/test/java/com/xmut/ebus/extension/tool/view/MustacheViewRendererTest.java`

**Interfaces:**
- Produces:

```java
public final class MustacheViewRenderer {
  /** @param template Mustache source; @param data root map (artifact-shaped) */
  public String render(String template, Map<String, Object> data);
}
```

- [ ] **Step 1: Add dependency**

```xml
<dependency>
  <groupId>com.samskivert</groupId>
  <artifactId>jmustache</artifactId>
  <version>1.15</version>
</dependency>
```

- [ ] **Step 2: Failing test**

```java
@Test
void escapesHtmlInAttributes() {
    MustacheViewRenderer r = new MustacheViewRenderer();
    String html = r.render(
        "<button data-adam-prompt=\"{{handoffPrompt}}\">x</button>",
        Collections.singletonMap("handoffPrompt", "说\"你好\""));
    assertFalse(html.contains("data-adam-prompt=\"说\"你好\"\""));
    assertTrue(html.contains("&quot;") || html.contains("&#34;"));
}

@Test
void loopsItems() {
    Map<String, Object> item = new LinkedHashMap<String, Object>();
    item.put("displayTitle", "拓展坞");
    Map<String, Object> root = new LinkedHashMap<String, Object>();
    root.put("items", Collections.singletonList(item));
    String html = new MustacheViewRenderer().render(
        "{{#items}}<li>{{displayTitle}}</li>{{/items}}", root);
    assertEquals("<li>拓展坞</li>", html);
}
```

- [ ] **Step 3: Run — expect FAIL**

```bash
mvn -pl lippi-ai-ebus-pi-extension -am test -Dtest=MustacheViewRendererTest -DfailIfNoTests=false
```

- [ ] **Step 4: Implement with jmustache `Mustache.compiler().escapeHTML(true).compile(template).execute(data)`**

- [ ] **Step 5: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(pi-extension): add MustacheViewRenderer for skill views

jmustache with HTML escaping for template-filled Computer content.
EOF
)"
```

---

### Task 2: Settle parser — `view.json` pointer + sibling artifact

**Files:**
- Modify: `lippi-ai-ebus-application/src/main/java/com/xmut/ebus/application/business/agent/support/GenerationOutputParser.java`  
- Modify: `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/application/business/agent/support/GenerationOutputParserTest.java`

**Interfaces:**
- When pointer file content is a **v2 view document** (`version==2` and has `format`+`content`), set `rawView` = that object; load sibling `artifact.json` as business payload (empty map if missing → keep current fail policy if persist requires artifact — prefer: missing sibling throws `IllegalArgumentException("artifact file missing: …")` for skill settles).  
- When pointer file is legacy `{view, artifact}` envelope, keep existing `parseEnvelope` behavior.  
- Sibling name: always `artifact.json` in the **parent directory of the view file** (so `plan/view.json` → `plan/artifact.json`).

- [ ] **Step 1: Failing tests**

```java
@Test
void outputPointer_viewJson_loadsSiblingArtifact() throws Exception {
    Path run = Files.createTempDirectory("parse-ws-");
    Files.write(run.resolve("artifact.json"),
        "{\"title\":\"选题\",\"items\":[{\"id\":\"tp-1\"}]}".getBytes(StandardCharsets.UTF_8));
    Files.write(run.resolve("view.json"),
        ("{\"version\":2,\"title\":\"选题\",\"format\":\"html\",\"content\":\"<p>x</p>\"}")
            .getBytes(StandardCharsets.UTF_8));
    ParsedGenerationOutput out = parser.parse("{\"output\":\"view.json\"}", run);
    assertEquals(2, ((Number) out.getRawView().get("version")).intValue());
    assertEquals("html", out.getRawView().get("format"));
    assertEquals("选题", out.getBusinessPayload().get("title"));
}

@Test
void outputPointer_planView_loadsPlanArtifact() throws Exception {
    Path plan = Files.createTempDirectory("parse-ws-").resolve("plan");
    Files.createDirectories(plan);
    Files.write(plan.resolve("artifact.json"), "{\"title\":\"策划\"}".getBytes(StandardCharsets.UTF_8));
    Files.write(plan.resolve("view.json"),
        "{\"version\":2,\"title\":\"策划\",\"format\":\"html\",\"content\":\"<p>p</p>\"}"
            .getBytes(StandardCharsets.UTF_8));
    ParsedGenerationOutput out = parser.parse("{\"output\":\"plan/view.json\"}", plan.getParent());
    assertEquals("策划", out.getBusinessPayload().get("title"));
}

@Test
void outputPointer_finalJson_stillWorks() throws Exception {
    // existing dual-track envelope test remains green
}
```

- [ ] **Step 2: Run — FAIL**

```bash
mvn -pl lippi-ai-ebus-application -am test -Dtest=GenerationOutputParserTest -DfailIfNoTests=false
```

- [ ] **Step 3: Implement in `tryResolveOutputPointer`**

After reading `fileText`:

```java
JsonNode fileRoot = objectMapper.readTree(fileText);
if (isViewV2Document(fileRoot)) {
    Map<String, Object> view = objectMapper.convertValue(fileRoot, MAP_TYPE);
    Path artifactFile = file.getParent().resolve("artifact.json");
    if (!Files.isRegularFile(artifactFile)) {
        throw new IllegalArgumentException("artifact file missing: " + artifactFile.getFileName());
    }
    Map<String, Object> artifact = objectMapper.readValue(
        Files.readAllBytes(artifactFile), MAP_TYPE);
    return new ParsedGenerationOutput(view, artifact);
}
return parseEnvelope(fileText, runWorkspaceRoot, false); // legacy final.json
```

```java
static boolean isViewV2Document(JsonNode n) {
    if (n == null || !n.isObject()) return false;
    if (n.path("version").asInt(0) != 2) return false;
    if (!n.path("format").isTextual()) return false;
    if (!n.path("content").isTextual()) return false;
    return true;
}
```

- [ ] **Step 4: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(agent): settle view.json pointer with sibling artifact

GenerationOutputParser loads v2 view files and colocated artifact.json
while keeping final.json envelopes.
EOF
)"
```

---

### Task 3: `render_view` tool handler

**Files:**
- Create: `…/tool/view/RenderViewToolHandler.java`  
- Create: `…/tool/view/ViewRenderHelpers.java` (stub: identity enrich for now, or skill-id switch with no-op default)  
- Create: `…/resources/tools/view/render_view.tool.json`  
- Create: `…/config/ViewToolsConfiguration.java`  
- Create: `…/tool/view/RenderViewToolHandlerTest.java`  
- Modify: catalog tests that enumerate tools if they assert exact sets

**Interfaces:**
- Consumes: `ToolContext.getWorkspaceRoot()`, `getActiveSkillId()`, `SkillCatalog.resolve(id)` → `Skill.getPromptRef()`  
- Template resolve: from `promptRef` like `classpath:scenes/xiaohongshu/xhs-topiclist/SKILL.md` → base dir `scenes/xiaohongshu/xhs-topiclist/` + relative `template` param (default `references/view.mustache`) via `ClassPathResource` / Spring `ResourceLoader`  
- Produces tool OK text e.g. `{"ok":true,"out":"view.json","bytes":123}`

Tool JSON:

```json
{
  "id": "render_view",
  "description": "用当前 skill 的 Mustache 模板把 artifact.json 渲染成 Computer view.json",
  "handlerClass": "com.xmut.ebus.extension.tool.view.RenderViewToolHandler",
  "schema": {
    "name": "render_view",
    "description": "读取 artifact，渲染 skill Mustache，写出 v2 view.json",
    "parameters": {
      "type": "object",
      "properties": {
        "artifact": { "type": "string", "description": "相对 run 根，默认 artifact.json" },
        "out": { "type": "string", "description": "相对 run 根，默认 view.json" },
        "template": { "type": "string", "description": "相对 skill 根，默认 references/view.mustache" },
        "format": { "type": "string", "description": "html 或 markdown，默认 html" }
      }
    }
  }
}
```

- [ ] **Step 1: Failing handler test** with temp workspace + classpath template fixture (put a tiny `view.mustache` under `src/test/resources/scenes/test/demo-skill/references/` and register a Skill with that promptRef, **or** inject a `TemplateLoader` seam for tests)

Minimal seam if SkillCatalog hard:

```java
interface SkillTemplateLoader {
  String load(String activeSkillId, String templateRelativePath);
}
```

Production impl uses SkillCatalog + ResourceLoader; test impl returns fixed template string.

- [ ] **Step 2: Run — FAIL**

```bash
mvn -pl lippi-ai-ebus-pi-extension -am test -Dtest=RenderViewToolHandlerTest -DfailIfNoTests=false
```

- [ ] **Step 3: Implement handler**

```java
// pseudo
Path ws = Paths.get(ctx.getWorkspaceRoot());
Path artifactPath = LocalFileSupport.resolveUnder(ws, artifactRel);
Path outPath = LocalFileSupport.resolveUnder(ws, outRel);
Map<String, Object> data = readJson(artifactPath);
data = ViewRenderHelpers.enrich(ctx.getActiveSkillId(), data);
String template = templateLoader.load(ctx.getActiveSkillId(), templateRel);
String content = renderer.render(template, data);
String title = data.get("title") instanceof String ? (String) data.get("title") : "draft";
String format = "markdown".equalsIgnoreCase(formatParam) ? "markdown" : "html";
Map<String, Object> view = ComputerDocument.v2-equivalent map;
writeJson(outPath, view);
return ToolResult.ok(...);
```

Require `activeSkillId` non-blank; fail clearly otherwise.

- [ ] **Step 4: Wire `@Bean` + ensure `tools/view/render_view.tool.json` is on ToolDefinitionJsonLoader pattern (`classpath*:tools/**/*.tool.json`)**

- [ ] **Step 5: PASS + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(pi-extension): add render_view tool for Mustache Computer views

Active skill template + artifact.json writes v2 view.json in the run workspace.
EOF
)"
```

---

### Task 4: ViewRenderHelpers — handoff prompts (topiclist + picklist)

**Files:**
- Modify: `ViewRenderHelpers.java`  
- Create: `ViewRenderHelpersTest.java`

**Interfaces:**
- `Map<String, Object> enrich(String skillId, Map<String, Object> artifact)`  
- For `xhs-topiclist`: each item gets `displayTitle` (strip `【优先发】`), `handoffPrompt` matching former FE `buildXhsNoteHandoffText`  
- For `ecommerce-picklist`: strip `【优先试】`, `handoffPrompt` matching former `buildListingHandoffText` (require https href + id or omit button fields / empty prompt so template can `{{#handoffPrompt}}`)  
- Deep-copy before mutate; never write helpers back to disk

Prompt templates (exact):

```text
请根据选题「{title}」（条目 {id}）写一篇小红书种草笔记，语气像真人分享。
角度：…
钩子：…
原笔记：https://…   # only if https
```

```text
请为商品「{title}」生成上架素材。
原链：{href}
来源选品条目：{id}
参考：…
痛点：…
角度：…
```

- [ ] **Step 1–4: TDD + commit**

```bash
git commit -m "$(cat <<'EOF'
feat(pi-extension): inject handoff helpers for list skill templates

In-memory displayTitle and handoffPrompt for topiclist and picklist.
EOF
)"
```

---

### Task 5: Templates + skill docs — topiclist & picklist

**Files:**
- Create: `…/xiaohongshu/xhs-topiclist/references/view.mustache`  
- Create: `…/ecommerce/ecommerce-picklist/references/view.mustache`  
- Modify: both `SKILL.md` + `references/output.md`  
- Sync test mirrors under `lippi-ai-ebus-application/src/test/resources/scenes/…` and `lippi-pi-agent/src/test/resources/scenes/…`

**SKILL flow rewrite:**
1. build artifact → `artifact.json`  
2. `render_view` (defaults)  
3. dialogue **only** `{"output":"view.json"}`  
4. `allowed-tools:` add `render_view`; remove obligation to write view HTML / final.json  

**Mustache sketch (topiclist):**

```mustache
<article class="markdown-body">
<h1>{{title}}</h1>
<p>{{disclaimer}}</p>
<ol>
{{#items}}
<li>
{{#displayTitle}}<p><strong>{{displayTitle}}</strong></p>{{/displayTitle}}
<p>视角：{{hook}} · 切入：{{angle}}</p>
<p>优先：{{whyFirst}} · 风险：{{risk}}</p>
{{#handoffPrompt}}
<p><button type="button" data-adam-action="handoff" data-adam-skill-id="xhs-note" data-adam-prompt="{{handoffPrompt}}">写成笔记</button></p>
{{/handoffPrompt}}
</li>
{{/items}}
</ol>
</article>
```

- [ ] **Step 1: Add templates + update docs**  
- [ ] **Step 2: Sync mirrors**  
- [ ] **Step 3: Optional integration test** — enrich fixture artifact + real mustache file → assert `data-adam-skill-id` present  

```bash
git commit -m "$(cat <<'EOF'
docs(skills): topiclist and picklist use render_view Mustache

Drop hand-written HTML and final.json from agent delivery steps.
EOF
)"
```

---

### Task 6: Helpers + templates — break, note, listing

**Files:**
- `ViewRenderHelpers` — `xhs-break` handoff prompt (former `buildXhsBreakNoteHandoffText`); note may need no handoff  
- `xhs-break/references/view.mustache`  
- `xhs-note/references/view.mustache` + `format: markdown` via tool arg or skill default map in handler (`xhs-note` → markdown)  
- `ecommerce-skulist/references/plan/view.mustache` + `exec/view.mustache` (exec includes `<img data-adam-media-role="hero" alt="主图占位" src="">`)  
- Update SKILL/output + mirrors  

**Listing agent calls:**

```text
render_view({ artifact: "plan/artifact.json", out: "plan/view.json", template: "references/plan/view.mustache" })
→ {"output":"plan/view.json"}

render_view({ artifact: "exec/artifact.json", out: "exec/view.json", template: "references/exec/view.mustache" })
→ {"output":"exec/view.json"}
```

- [ ] **Step 1–3: Implement + sync + commit**

```bash
git commit -m "$(cat <<'EOF'
docs(skills): migrate note, break, and listing to Mustache render_view

Plan/exec listing templates and markdown note format via render_view.
EOF
)"
```

---

### Task 7: Catalog / allow-lists / smoke

**Files:**
- All five skills’ `allowed-tools` include `render_view`  
- `EbusPiToolCatalogConfigurationTest` / `EbusPrimaryToolCatalogOverrideTest` — resolve `render_view`  
- Grep skill docs: no required `final.json` write step for migrated skills; pointer is `view.json`  
- Run:

```bash
mvn -pl lippi-ai-ebus-pi-extension,lippi-ai-ebus-application -am test -Dtest=MustacheViewRendererTest,RenderViewToolHandlerTest,ViewRenderHelpersTest,GenerationOutputParserTest,EbusPiToolCatalogConfigurationTest -DfailIfNoTests=false
rg 'final\.json' lippi-ai-ebus-pi-extension/src/main/resources/scenes --glob 'SKILL.md' || true
```

- [ ] **Step 1: Fix any leftover final.json requirements in migrated SKILL.md**  
- [ ] **Step 2: Tests green**  
- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
chore(skills): allow render_view and drop final.json delivery

Verify tool catalog registration and skill allow-lists.
EOF
)"
```

---

## Spec coverage self-check

| Spec item | Task |
|-----------|------|
| `render_view` tool | 3 |
| Mustache + HTML escape | 1 |
| artifact-only model input | 5–6 docs |
| view.json v2 write | 3 |
| Pointer `output: view.json` + sibling artifact | 2 |
| plan/view + plan/artifact | 2, 6 |
| Helpers handoffPrompt / displayTitle | 4, 6 |
| final.json short-term compat | 2 |
| topiclist/picklist first | 5 |
| note/break/listing | 6 |
| No settle-time-only render | 3 (writes disk before pointer) |

## Placeholder / consistency

- Tool id locked: `render_view`  
- Default paths locked: `artifact.json` / `view.json` / `references/view.mustache`  
- Parser sibling name locked: `artifact.json`  
- jmustache version locked in Task 1: `1.15`  

---
