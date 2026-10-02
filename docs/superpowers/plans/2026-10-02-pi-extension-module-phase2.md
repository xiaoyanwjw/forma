# Phase 2: `lippi-ai-ebus-pi-extension` 物理拆分 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把业务 Tool（SKU/XHS：json + Handler + Port 管线）与业务 Skill 资源迁入新模块 `lippi-ai-ebus-pi-extension`，经 Boot 2.7 `spring.factories` 自动装配；`starter` 依赖 extension 后即可注册业务 tools 并扫到业务 skills。

**Architecture:** 新建扁平 Maven 模块（AD-13）。业务代码包根 `com.xmut.ebus.extension`。AutoConfiguration 只经 `META-INF/spring.factories` 加载；`EbusApplication` 排除对该包的 component-scan，避免与 auto-config 双注册。依赖：`starter → extension → pi-agent (+ common + okhttp)`；`extension ↛ application`；`pi-agent ↛ extension`。计费 / persist / SSE / `SceneCapabilityPackLoader` 仍留 application。

**Tech Stack:** Java 8 / Spring Boot 2.7、`spring.factories` EnableAutoConfiguration、现有 `*.tool.json` 扫描（Phase 1 已落地）

## Global Constraints

- Spec: `docs/superpowers/specs/2026-10-02-pi-tool-skill-dynamic-injection-design.md` §5–§6（accepted）— **本计划仅 Phase 2**
- Artifact 名钉死：`lippi-ai-ebus-pi-extension`
- 配置类名钉死：`SkuToolsConfiguration`、`XhsToolsConfiguration`（可再包一层 `*AutoConfiguration` `@Import`，但工厂必须最终启用这两类）
- Pi 内置 **不迁**：`BaseToolsConfiguration` + `tools/base/*.tool.json` 留在 `lippi-pi-agent`
- 迁入：Sku/Xhs Handler + Port/Searcher/Properties/Apify 客户端、`tools/sku|xhs/*.tool.json`、生产 `scenes/ecommerce/**` 与 `scenes/xiaohongshu/**`
- 不迁：CreditLedger、SSE、`SceneCapabilityPackLoader`、`ArtifactPersistPlugin`、domain
- 依赖方向：`starter → extension → pi-agent`；`extension ↛ application`；`pi-agent ↛ extension`；`pi-agent ↛ Apify/Credit`（Apify 只在 extension）
- Rerank：只依赖 `pi-ai` 的 `ModelProvider` / `ModelCatalog` 端口；useCase 名仍由 properties 驱动
- Schema 真源仍为 `*.tool.json`；迁包后必须改 `handlerClass` FQCN
- Boot 2.7：用 `META-INF/spring.factories`（不用 `AutoConfiguration.imports`）
- Java 8：无 `var` / `List.of`
- 属性前缀不变：`SkuSearchProperties` / `XhsNote*Properties` 的 `@ConfigurationProperties` prefix 保持现网值

## File map（目标态）

| Path | Responsibility |
|------|----------------|
| `lippi-ai-ebus-pi-extension/pom.xml` | 模块 POM：依赖 `lippi-pi-agent`、`lippi-ai-ebus-common`、`okhttp`、`spring-boot-autoconfigure` |
| `…/src/main/java/com/xmut/ebus/extension/config/SkuToolsConfiguration.java` | 自 application 迁入；包名改 extension |
| `…/config/XhsToolsConfiguration.java` | 同上 |
| `…/config/EbusModelCatalogAutoConfiguration.java` | 承接原 `PiToolCatalogConfiguration` 的 `@Primary ModelCatalog` overlay |
| `…/tool/sku/**`、`…/tool/xhs/**` | Handler / Port / Apify / Searcher（自 `application.business.agent.tool.*` 迁入） |
| `…/resources/tools/sku/*.tool.json`、`tools/xhs/*.tool.json` | schema；`handlerClass` → `com.xmut.ebus.extension.tool…` |
| `…/resources/scenes/{ecommerce,xiaohongshu}/**` | 自 starter 迁入的生产 Skill 资源 |
| `…/resources/META-INF/spring.factories` | `EnableAutoConfiguration=SkuToolsConfiguration,XhsToolsConfiguration,EbusModelCatalogAutoConfiguration` |
| `lippi-ai-ebus-starter/pom.xml` | 增加对 extension 的依赖 |
| `lippi-ai-ebus-starter/.../EbusApplication.java` | 排除 `com.xmut.ebus.extension` 的 component-scan |
| `pom.xml`（parent） | `<modules>` + `dependencyManagement` 增加 extension |
| `lippi-ai-ebus-application/.../PiToolCatalogConfiguration.java` | **删除**（逻辑进 extension） |
| `lippi-ai-ebus-application/.../SkuToolsConfiguration.java` 等 | **删除**（迁走后无残留） |

## Spec → Task

| Spec § | Task |
|--------|------|
| 5.1 建模块 + spring.factories 骨架 + starter 依赖 + 防双注册 | 1 |
| 5.2 迁 Sku（java + json + 测） | 2 |
| 5.2 迁 Xhs（java + json + 测） | 3 |
| 5.2/5.3 ModelCatalog overlay 迁 extension；清 application | 4 |
| 5.2 迁 scenes；5.4 验收回归 | 5 |

**Phase 1** 已完成，本 plan 不重做 loader/BaseTools。

---

### Task 1: 脚手架模块 + AutoConfiguration 接线 + 防双注册

**Files:**
- Create: `lippi-ai-ebus-pi-extension/pom.xml`
- Create: `lippi-ai-ebus-pi-extension/src/main/resources/META-INF/spring.factories`（先空类占位或仅占位 Configuration）
- Create: `lippi-ai-ebus-pi-extension/src/main/java/com/xmut/ebus/extension/config/ExtensionMarkerConfiguration.java`（临时空 `@Configuration`，Task 2/3 删或替换）
- Create: `lippi-ai-ebus-pi-extension/src/test/java/com/xmut/ebus/extension/ExtensionAutoConfigurationSmokeTest.java`
- Modify: root `pom.xml` — `<module>lippi-ai-ebus-pi-extension</module>` + `dependencyManagement` 条目
- Modify: `lippi-ai-ebus-starter/pom.xml` — 依赖 `lippi-ai-ebus-pi-extension`
- Modify: `lippi-ai-ebus-starter/src/main/java/com/xmut/ebus/EbusApplication.java` — 排除 extension 包扫描

**Interfaces:**
- Produces: artifact `com.lippi:lippi-ai-ebus-pi-extension:1.0.0-SNAPSHOT`
- Produces: `spring.factories` 键 `org.springframework.boot.autoconfigure.EnableAutoConfiguration`
- Produces: `EbusApplication` 不再 component-scan `com.xmut.ebus.extension.**`（仅 auto-config 加载）
- Consumes: Phase 1 已有 `PiAutoConfiguration` / `ToolCatalog` 合并路径（不动）

- [ ] **Step 1: 写失败测 — extension 在 starter 测试 classpath 上可被 AutoConfiguration 加载**

```java
@SpringBootTest(classes = EbusApplication.class)
class ExtensionAutoConfigurationSmokeTest {
    @Autowired
    private ApplicationContext ctx;

    @Test
    void loads_extension_marker_via_spring_factories() {
        assertNotNull(ctx.getBean(ExtensionMarkerConfiguration.class));
    }
}
```

（若全量 `EbusApplication` 过重：改用 `ApplicationContextRunner` + `AutoConfigurations.of` 加载 factories 中的类，并断言 bean 存在。）

推荐用 `ApplicationContextRunner`（与现网 `SearchSkuToolHandlerTest` 风格一致）：

```java
@Test
void marker_loads_from_spring_factories() {
    new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ExtensionMarkerConfiguration.class))
            .run(ctx -> assertThat(ctx).hasSingleBean(ExtensionMarkerConfiguration.class));
}
```

真正验证 factories 时：

```java
@Test
void spring_factories_lists_extension_configs() throws IOException {
    Enumeration<URL> urls = ClassLoader.getSystemResources("META-INF/spring.factories");
    Properties all = new Properties();
    while (urls.hasMoreElements()) {
        try (InputStream in = urls.nextElement().openStream()) {
            all.load(in);
        }
    }
    String value = all.getProperty("org.springframework.boot.autoconfigure.EnableAutoConfiguration");
    assertTrue(value != null && value.contains("com.xmut.ebus.extension.config.ExtensionMarkerConfiguration"));
}
```

- [ ] **Step 2: Run — expect FAIL（模块/资源不存在）**

```bash
mvn -pl lippi-ai-ebus-pi-extension -am test
```

Expected: module missing or test compile fail.

- [ ] **Step 3: 实现模块 POM + parent 接线**

`lippi-ai-ebus-pi-extension/pom.xml` 最小依赖：

```xml
<artifactId>lippi-ai-ebus-pi-extension</artifactId>
<dependencies>
  <dependency><groupId>com.lippi</groupId><artifactId>lippi-pi-agent</artifactId></dependency>
  <dependency><groupId>com.lippi</groupId><artifactId>lippi-ai-ebus-common</artifactId></dependency>
  <dependency><groupId>com.squareup.okhttp3</groupId><artifactId>okhttp</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-autoconfigure</artifactId></dependency>
  <dependency><groupId>org.springframework.boot</groupId><artifactId>spring-boot-starter-test</artifactId><scope>test</scope></dependency>
</dependencies>
```

**禁止**依赖 `lippi-ai-ebus-application`。

Parent `pom.xml`：在 `lippi-pi-agent` 之后、`lippi-ai-ebus-domain` 之前插入 `<module>lippi-ai-ebus-pi-extension</module>`；`dependencyManagement` 增加同版本条目。

`starter/pom.xml` 增加：

```xml
<dependency>
  <groupId>com.lippi</groupId>
  <artifactId>lippi-ai-ebus-pi-extension</artifactId>
</dependency>
```

- [ ] **Step 4: `spring.factories` + Marker + 排除扫描**

`META-INF/spring.factories`:

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.xmut.ebus.extension.config.ExtensionMarkerConfiguration
```

`EbusApplication` 改为显式 `@ComponentScan`（保留原 scan 范围并排除 extension）：

```java
@SpringBootApplication
@ComponentScan(
        basePackages = "com.xmut.ebus",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.xmut\\.ebus\\.extension\\..*"))
public class EbusApplication { ... }
```

- [ ] **Step 5: Run PASS + Commit**

```bash
mvn -pl lippi-ai-ebus-pi-extension,lippi-ai-ebus-starter -am -Dtest=ExtensionAutoConfigurationSmokeTest test
git add lippi-ai-ebus-pi-extension pom.xml lippi-ai-ebus-starter
git commit -m "chore(extension): scaffold lippi-ai-ebus-pi-extension with spring.factories"
```

---

### Task 2: 迁入 SKU 工具栈

**Files:**
- Move（包名 `com.xmut.ebus.application.business.agent.tool.sku` → `com.xmut.ebus.extension.tool.sku`）全部 SKU 主代码：
  - `SearchSkuToolHandler`, `SkuSearcher`, `SkuSearchPort`, `SkuSearchProperties`, `SkuReranker`, `ModelSkuReranker`, `SkuCandidate`, `SkuSearchHit`
  - `MockSkuSearchClient`, `FallbackSkuSearchClient`, `ApifyTaobaoSkuSearchClient`, `ApifyTaobaoHitMapper`
  - `ApifyOkHttpTransport`, `ApifyActorTransport`（XHS 仍依赖这两类 — 先迁到 `extension.tool.sku`，Task 3 改 import）
- Move: `SkuToolsConfiguration` → `com.xmut.ebus.extension.config.SkuToolsConfiguration`
- Move: `tools/sku/search_sku.tool.json` → extension resources；改 `handlerClass`
- Move tests（application 下 sku 相关 `*Test`）→ `lippi-ai-ebus-pi-extension/src/test/java/...`
- Modify: `spring.factories` — 加入 `SkuToolsConfiguration`；可保留 Marker 至 Task 5 再删
- Delete: application 内对应源文件 / 资源 / 测试

**Interfaces:**
- Produces: `handlerClass=com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler`
- Produces: `@ConfigurationProperties` prefix **不变**（打开现网 `SkuSearchProperties`，原样保留 prefix 字符串）
- Consumes: `ModelProvider`（`ObjectProvider`）来自 pi-ai auto-config；缺省时 identity reranker（现网行为）

- [ ] **Step 1: 改 json `handlerClass` 并写测断言**

`search_sku.tool.json`：

```json
"handlerClass": "com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler"
```

测试（可放在 extension）：

```java
@Test
void search_sku_json_handlerClass_points_at_extension_package() throws Exception {
    List<ToolDefinition> defs = ToolDefinitionJsonLoader.load(new PathMatchingResourcePatternResolver());
    ToolDefinition d = defs.stream().filter(t -> "search_sku".equals(t.getId())).findFirst().get();
    assertEquals("com.xmut.ebus.extension.tool.sku.SearchSkuToolHandler", d.getHandlerClass());
}
```

- [ ] **Step 2: Run — expect FAIL（仍是旧 FQCN 或资源仍在 application）**

```bash
mvn -pl lippi-ai-ebus-pi-extension -am -Dtest=SearchSkuToolJsonTest test
```

- [ ] **Step 3: `git mv` / 改 package / 更新 Configuration import / factories**

`SkuToolsConfiguration` 仅改 package + import；Bean 方法体保持不变。

`spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.xmut.ebus.extension.config.ExtensionMarkerConfiguration,\
com.xmut.ebus.extension.config.SkuToolsConfiguration
```

临时：application 内若仍有 `XhsToolsConfiguration` 引用 `ApifyOkHttpTransport`，改为 import `com.xmut.ebus.extension.tool.sku.ApifyOkHttpTransport`，并让 **application 临时 test/compile 依赖 extension**（仅过渡；Task 3 结束后 application 主代码不再依赖 extension）。  
**更干净做法（推荐本 Task 采用）：** Task 2 同步把 `ApifyOkHttpTransport`/`ApifyActorTransport` 迁走后，**立即**改 application 里 XHS 客户端的 import 指向 extension，并为 `lippi-ai-ebus-application` 增加对 `lippi-ai-ebus-pi-extension` 的 **临时** compile 依赖；Task 3 迁完 XHS 后 **删除** application→extension 依赖（验收：application 主源码无 `extension` import）。

- [ ] **Step 4: Run SKU 相关测试**

```bash
mvn -pl lippi-ai-ebus-pi-extension -am -DfailIfNoTests=false \
  -Dtest=SkuSearcherTest,SearchSkuToolHandlerTest,FallbackSkuSearchClientTest,ApifyTaobaoSkuSearchClientTest,ApifyTaobaoHitMapperTest,SkuSearchPropertiesTest,ModelSkuRerankerTest,SearchSkuToolJsonTest test
```

Expected: BUILD SUCCESS.

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(extension): move SKU tools and search_sku.tool.json into pi-extension"
```

---

### Task 3: 迁入 XHS 工具栈

**Files:**
- Move: `com.xmut.ebus.application.business.agent.tool.xhs.**` → `com.xmut.ebus.extension.tool.xhs.**`
- Move: `XhsToolsConfiguration` → `com.xmut.ebus.extension.config.XhsToolsConfiguration`
- Move: `tools/xhs/*.tool.json`；更新两处 `handlerClass`
- Move: 全部 xhs `*Test` → extension
- Modify: `spring.factories` 加入 `XhsToolsConfiguration`
- Delete: application 内 xhs 源码/资源/测试
- Modify: `lippi-ai-ebus-application/pom.xml` — **移除** Task 2 临时的 extension 依赖（迁完后 application 主代码不应再 import extension）
- Modify: 仍留在 application 的测试（若有）改为不引用 Sku/Xhs 类型，或迁到 extension/starter

**Interfaces:**
- Produces:  
  - `com.xmut.ebus.extension.tool.xhs.SearchXhsNoteToolHandler`  
  - `com.xmut.ebus.extension.tool.xhs.FetchXhsNoteToolHandler`
- Consumes: `com.xmut.ebus.extension.tool.sku.ApifyOkHttpTransport` / `ApifyActorTransport`

- [ ] **Step 1: 更新 json + 写测**

```json
"handlerClass": "com.xmut.ebus.extension.tool.xhs.SearchXhsNoteToolHandler"
```
```json
"handlerClass": "com.xmut.ebus.extension.tool.xhs.FetchXhsNoteToolHandler"
```

```java
@Test
void xhs_tool_json_handlerClasses_are_extension_fqcns() { /* load + assert both ids */ }
```

- [ ] **Step 2: Run expect FAIL**

```bash
mvn -pl lippi-ai-ebus-pi-extension -am -Dtest=XhsToolJsonTest test
```

- [ ] **Step 3: 迁代码、改 factories、删 application→extension 依赖**

验证 application 主源码：

```bash
rg "com\\.xmut\\.ebus\\.extension" lippi-ai-ebus-application/src/main || echo "clean"
```

Expected: `clean`（无匹配）。

- [ ] **Step 4: Run XHS + 跨模块冒烟**

```bash
mvn -pl lippi-ai-ebus-pi-extension -am -DfailIfNoTests=false \
  -Dtest=XhsNoteSearcherTest,SearchXhsNoteToolHandlerTest,FetchXhsNoteToolHandlerTest,ApifyXhsNoteSearchClientTest,ApifyXhsNoteFetchClientTest,ModelXhsNoteRerankerTest,XhsToolJsonTest test
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(extension): move XHS tools and tool.json into pi-extension"
```

---

### Task 4: ModelCatalog overlay 迁入；清理 application 配置残留

**Files:**
- Create: `lippi-ai-ebus-pi-extension/.../config/EbusModelCatalogAutoConfiguration.java`  
  （逻辑从 `PiToolCatalogConfiguration` 剪切：`@Primary @Bean ModelCatalog modelCatalog(...)` + `overlayRerankUseCases`）
- Modify: `spring.factories` 增加该类；可删除 `ExtensionMarkerConfiguration`（若仍存在）
- Delete: `lippi-ai-ebus-application/.../PiToolCatalogConfiguration.java`
- Move/adapt: `EbusPiToolCatalogConfigurationTest`、`EbusPrimaryToolCatalogOverrideTest` → extension 或 starter  
  - Override 测：断言 **唯一** `ToolCatalog` + 八个生产 id 仍 resolve（handlers 来自 extension auto-config + pi Base）
- Delete: application 内已空的 `config` 测试若无其它用途

**Interfaces:**
- Produces: `@Primary ModelCatalog` 仍覆盖 `ebus.sku.rerank` / `ebus.xhs.rerank`（默认 useCase 字符串不变）
- Consumes: `SkuSearchProperties`、`XhsNoteSearchProperties`（已在 extension）

- [ ] **Step 1: 写测 — extension 上下文提供 rerank useCase**

```java
@Test
void modelCatalog_resolves_sku_and_xhs_rerank_use_cases() {
    new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    EbusModelCatalogAutoConfiguration.class,
                    SkuToolsConfiguration.class,
                    XhsToolsConfiguration.class))
            .run(ctx -> {
                ModelCatalog catalog = ctx.getBean(ModelCatalog.class);
                assertNotNull(catalog.resolve("ebus.sku.rerank"));
                assertNotNull(catalog.resolve("ebus.xhs.rerank"));
            });
}
```

- [ ] **Step 2: Run expect FAIL**

```bash
mvn -pl lippi-ai-ebus-pi-extension -Dtest=EbusModelCatalogAutoConfigurationTest test
```

- [ ] **Step 3: 剪切实现；删 application `PiToolCatalogConfiguration`；更新 factories**

最终 `spring.factories`：

```properties
org.springframework.boot.autoconfigure.EnableAutoConfiguration=\
com.xmut.ebus.extension.config.SkuToolsConfiguration,\
com.xmut.ebus.extension.config.XhsToolsConfiguration,\
com.xmut.ebus.extension.config.EbusModelCatalogAutoConfiguration
```

- [ ] **Step 4: Run**

```bash
mvn -pl lippi-ai-ebus-pi-extension,lippi-ai-ebus-starter -am -DfailIfNoTests=false \
  -Dtest=EbusModelCatalogAutoConfigurationTest,EbusPrimaryToolCatalogOverrideTest,EbusPiToolCatalogConfigurationTest test
```

（测试类以迁入后的实际类名为准。）

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(extension): move ModelCatalog rerank overlay; drop application PiToolCatalogConfiguration"
```

---

### Task 5: 迁生产 scenes + Phase 2 验收回归

**Files:**
- Move: `lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/**` → `lippi-ai-ebus-pi-extension/src/main/resources/scenes/ecommerce/**`
- Move: `lippi-ai-ebus-starter/src/main/resources/scenes/xiaohongshu/**` → `…/scenes/xiaohongshu/**`
- Keep: `application` / `pi-agent` 的 **test** `scenes/**` 镜像（可不迁；仅作单测夹具）
- Modify: `AGENTS.md` Boundaries / structure 一行：业务 Skill/Tool 在 `lippi-ai-ebus-pi-extension`
- Optional: Spine AD-11 图补 `starter → extension`（若改 Spine，单独 commit 说明；**Ask first** 若团队视 Spine 为需审批 — 本仓 AGENTS 写「Ask first」改依赖方向：本任务 **只改 AGENTS 一句 + 代码依赖已落地**；Spine 补丁作为可选 Step）

**Interfaces:**
- Produces: classpath 上仍有 `scenes/ecommerce/*/SKILL.md` 与 `scenes/xiaohongshu/*/SKILL.md`（模块从 starter 换到 extension）
- Consumes: 现有 `Skills.DEFAULT_PATTERN = classpath*:scenes/*/*/SKILL.md`

- [ ] **Step 1: 迁 scenes 后写/跑装包测**

```bash
# 确认 starter 生产 resources 不再含 scenes
test ! -d lippi-ai-ebus-starter/src/main/resources/scenes && echo "starter scenes gone"
test -f lippi-ai-ebus-pi-extension/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md && echo "extension has picklist"
```

- [ ] **Step 2: 验收命令（Spec §5.4）**

```bash
mvn -pl lippi-ai-ebus-starter -am -DfailIfNoTests=false \
  -Dtest=SceneCapabilityPackBootstrapTest,SkillsTest,ToolCatalogTest,XhsNoteSearcherTest,EbusPrimaryToolCatalogOverrideTest,SkuSearcherTest,SearchXhsNoteToolHandlerTest,FetchXhsNoteToolHandlerTest test
```

Expected: BUILD SUCCESS.

人工核对清单（写入报告即可）：

1. application **无** `SkuToolsConfiguration` / `XhsToolsConfiguration` / `tools/sku|xhs`  
2. `spring.factories` 仅列 extension 三类 Configuration  
3. 八个生产 tool id 均可 `resolve` 且 handler 非 null  
4. `rg "EnableAutoConfiguration" -g 'spring.factories'` 含 extension  
5. `mvn -pl lippi-ai-ebus-pi-extension dependency:tree` **不含** `lippi-ai-ebus-application`

- [ ] **Step 3: 文档钉**

`AGENTS.md` Project structure 表增加一行：

```text
| `lippi-ai-ebus-pi-extension/` | 业务 Pi 扩展：SKU/XHS tools + scenes 资源（spring.factories） |
```

Boundaries **Never** 保持「前端直连大模型…」；可选在 Always 加：业务 tool/skill 资源进 extension，不进 pi-agent。

- [ ] **Step 4: Commit**

```bash
git commit -m "feat(extension): move ecommerce/xhs scenes; document pi-extension module"
```

- [ ] **Step 5: 最终自检（无新代码则不必空提交）**

```bash
mvn -pl lippi-ai-ebus-starter -am -DskipTests compile
rg "PiToolCatalogConfiguration|SkuToolsConfiguration|XhsToolsConfiguration" lippi-ai-ebus-application/src/main || echo "application clean of tool configs"
```

---

## Self-review

| Spec Phase 2 要求 | Task |
|-------------------|------|
| Artifact `lippi-ai-ebus-pi-extension` | 1 |
| `spring.factories` AutoConfiguration | 1–4 |
| `starter` 依赖 extension | 1 |
| 迁 Sku/Xhs configs + handlers + json | 2–3 |
| Base 工具不迁 | （约束；不迁） |
| 迁 scenes（可选但本 plan **做**） | 5 |
| 不计费/SSE/SceneCapabilityPackLoader | （约束；不迁） |
| 依赖方向 pi-agent ↛ extension；无 Apify in pi-agent | 1–3 验收 |
| 去掉 application 内 Sku/Xhs 后仍注册 | 5 |
| 装包与工具回归 | 5 |

| 风险 | 计划应对 |
|------|----------|
| component-scan 与 factories 双注册 | Task 1 `excludeFilters` |
| application↔extension 循环依赖 | extension 禁止依赖 application；Task 3 去掉临时依赖 |
| handlerClass 改包后旧 json | Task 2/3 强制改 FQCN + 测 |
| ModelCatalog `@Primary` 丢失 | Task 4 迁 overlay |

无占位符：各 Task 含具体路径、命令、json/FQCN。

---

## Execution Handoff

Plan saved to `docs/superpowers/plans/2026-10-02-pi-extension-module-phase2.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每 Task 新开子代理  
2. **Inline Execution** — 本会话连续做  

Which approach?
