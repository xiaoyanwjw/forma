# Apify 淘宝搜品客户端 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 让 `search_sku` 可经配置 `ebus.sku-search.client=apify` 调用 Apify 淘宝搜索 Actor，映射为现有 `SkuSearchHit`；缺 Token / 失败 / 无可用 https 时回退 Mock。

**Architecture:** 在 `sku` 包新增 `SkuSearchProperties`、`ApifyTaobaoSkuSearchClient`（REST 调 Apify sync dataset）、`FallbackSkuSearchClient`（失败→Mock）；`EbusPiToolCatalogConfiguration` 按 `client` 装配 `SkuSearchPort`。不改 Tool JSON 形状。Skill 免责改为「配置的检索抽样」，保留字面量 `非实时平台全站行情`。

**Tech Stack:** Java 8 / Spring Boot 2.7、`HttpURLConnection` 或现有可用的简单 HTTP（仓库无 RestTemplate 先例则可新建薄 `ApifyHttpClient`）、Jackson、JUnit 5；不引入 Apify Maven SDK（YAGNI）。

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-29-apify-sku-search-client-design.md`（accepted）
- 配置键：**`ebus.sku-search.client`** = `mock` \| `apify`（默认 `mock`）；**禁止**使用 `mode`
- Actor 默认：`zen-studio/taobao-search-scraper`（API path 用 `zen-studio~taobao-search-scraper`）
- Token：仅 env `APIFY_TOKEN`；缺 Token / 超时 / http 错 / empty_hits → **本次 search 回退 Mock** + error 日志（无 Token 明文）
- Hit `platform` 常量：`taobao_apify`；`detailUrl` 非绝对 https 则丢弃该行
- `enrichWithDetails` / `fetchReviews` = false；`maxItems` ≤ 20
- 以 **`com.xmut.ebus.application.business.sku`** 为准；勿双注册 `marketplace` 包里的 Port
- CI 不打 Apify 真网；前端零直连
- 不实现 `client=tbk` / 详情 Actor / 1688

## File map

| Path | Responsibility |
|------|----------------|
| `.../sku/SkuSearchProperties.java` | `@ConfigurationProperties(prefix="ebus.sku-search")`：`client`、`apify.actorId`、`apify.timeoutMs`、`apify.token`（或 `@Value` 读 `APIFY_TOKEN`） |
| `.../sku/ApifyTaobaoSkuSearchClient.java` | 调 Apify；映射 dataset → `SkuSearchHit`；失败抛受检/运行时异常或返回空由 Fallback 处理 |
| `.../sku/ApifyDatasetMapper.java`（可选抽出） | 纯函数 map 单行 JSON → Optional hit |
| `.../sku/FallbackSkuSearchClient.java` | 委托 primary；catch/empty → Mock + 日志原因枚举 |
| `.../config/EbusPiToolCatalogConfiguration.java` | 按 properties 装配 Port |
| `.../sku/*Test.java` | 映射、Fallback、缺 Token |
| `APP-META/.../.env.example` | `client` + `APIFY_TOKEN` 占位（替换旧 `mode` 注释） |
| `scenes/ecommerce/ecommerce-picklist/SKILL.md` + `references/output.md` + mirrors | 免责与来源文案 |

## Spec → Task

| Spec | Task |
|------|------|
| Properties + `client` 名 | 1 |
| Mapper + Apify client | 2 |
| Fallback | 3 |
| Wire Spring bean | 4 |
| env.example | 4 |
| Skill copy + mirrors | 5 |
| 手工验收清单 | 6（人） |

---

### Task 1: `SkuSearchProperties` + 启用 ConfigurationProperties

**Files:**
- Create: `forma-application/src/main/java/com/xmut/ebus/application/business/sku/SkuSearchProperties.java`
- Modify: 在 application 或 starter 的 `@SpringBootApplication` / 已有 `@EnableConfigurationProperties` 处注册（查现有 pattern；若无则在 `EbusPiToolCatalogConfiguration` 上 `@EnableConfigurationProperties(SkuSearchProperties.class)`）
- Test: `forma-application/src/test/java/com/xmut/ebus/application/business/sku/SkuSearchPropertiesTest.java`（可选：用 `@ConfigurationPropertiesBinding` 或纯 setter 默认值单测）

**Interfaces:**
- Produces:
  ```java
  // prefix ebus.sku-search
  public class SkuSearchProperties {
    private String client = "mock"; // mock | apify
    private final Apify apify = new Apify();
    public static class Apify {
      private String actorId = "zen-studio/taobao-search-scraper";
      private long timeoutMs = 120_000L;
      private String token; // bind from APIFY_TOKEN via env: APIFY_TOKEN or ebus.sku-search.apify.token
    }
  }
  ```
- Token 绑定推荐：`apify.token` ← `${APIFY_TOKEN:}`（application.yml 或 `@Value` 注入到 properties）

- [ ] **Step 1: 写默认值断言测**

```java
@Test
void defaults_to_mock_client_and_default_actor() {
    SkuSearchProperties p = new SkuSearchProperties();
    assertEquals("mock", p.getClient());
    assertEquals("zen-studio/taobao-search-scraper", p.getApify().getActorId());
    assertEquals(120_000L, p.getApify().getTimeoutMs());
}
```

- [ ] **Step 2: Run 失败（类不存在）**

```bash
mvn -pl forma-application -am -Dtest=SkuSearchPropertiesTest -DfailIfNoTests=false test
```

- [ ] **Step 3: 实现 Properties + Enable**

- [ ] **Step 4: Run 通过**

- [ ] **Step 5: Commit**

```bash
git add forma-application/src/main/java/com/xmut/ebus/application/business/sku/SkuSearchProperties.java \
  forma-application/src/test/java/com/xmut/ebus/application/business/sku/SkuSearchPropertiesTest.java \
  # + EnableConfigurationProperties 改动文件
git commit -m "$(cat <<'EOF'
feat(sku): add ebus.sku-search.client configuration properties

EOF
)"
```

---

### Task 2: Apify dataset 映射 + HTTP client（可注入 Transport）

**Files:**
- Create: `.../sku/ApifyTaobaoHitMapper.java`（纯静态/实例 map）
- Create: `.../sku/ApifyActorTransport.java`（接口：`String postSyncDatasetItems(String actorIdSlash, String token, long timeoutMs, String jsonBody)`）
- Create: `.../sku/ApifyHttpUrlConnectionTransport.java`（实现）
- Create: `.../sku/ApifyTaobaoSkuSearchClient.java`
- Test: `ApifyTaobaoHitMapperTest.java`、`ApifyTaobaoSkuSearchClientTest.java`（Transport mock）

**Interfaces:**
- Consumes: `SkuSearchProperties`；`SkuSearchHit` 构造器
- Produces: `ApifyTaobaoSkuSearchClient implements SkuSearchPort`
- API（钉死）：
  - URL: `POST https://api.apify.com/v2/acts/{username~actorname}/run-sync-get-dataset-items?timeout={seconds}`
  - Header: `Authorization: Bearer {token}`，`Content-Type: application/json`
  - Body: `{"keyword":"...","maxItems":N,"enrichWithDetails":false,"fetchReviews":false}`
  - `username/actor` → path 中 `/` 换成 `~`
  - 响应：JSON **数组**（dataset items）或带 `error` 对象；非 2xx → 抛 `IllegalStateException` 或自定义 `ApifySkuSearchException`
- Mapper 规则（与 spec 一致）：
  - `platform` = `"taobao_apify"`
  - title = titleOriginal 非空 ? titleOriginal : title
  - price = price.toString()
  - category = 有名用名；否则 cat:+id；否则 `""`
  - detailUrl = url 且 startsWith https://；否则 skip
  - rawRef = itemId string

- [ ] **Step 1: 写 Mapper 失败测**

```java
@Test
void maps_https_row_and_skips_non_https() {
    ObjectMapper om = new ObjectMapper();
    ArrayNode arr = om.createArrayNode();
    ObjectNode ok = arr.addObject();
    ok.put("titleOriginal", "硅胶垫");
    ok.put("price", 29.9);
    ok.put("url", "https://item.taobao.com/item.htm?id=1");
    ok.put("itemId", "1");
    ObjectNode bad = arr.addObject();
    bad.put("title", "x");
    bad.put("url", "http://evil.example/1");
    List<SkuSearchHit> hits = ApifyTaobaoHitMapper.mapItems(arr);
    assertEquals(1, hits.size());
    assertEquals("taobao_apify", hits.get(0).getPlatform());
    assertEquals("硅胶垫", hits.get(0).getTitle());
    assertTrue(hits.get(0).getDetailUrl().startsWith("https://"));
}
```

- [ ] **Step 2: Run 确认 FAIL**

- [ ] **Step 3: 实现 Mapper**

- [ ] **Step 4: 写 Client 测（mock Transport 返回 JSON 数组字符串）**

```java
@Test
void search_maps_transport_payload() {
    ApifyActorTransport transport = (actorId, token, timeoutMs, body) ->
        "[{\"title\":\"a\",\"price\":1,\"url\":\"https://item.taobao.com/item.htm?id=9\",\"itemId\":\"9\"}]";
    SkuSearchProperties props = new SkuSearchProperties();
    props.setClient("apify");
    props.getApify().setToken("t");
    ApifyTaobaoSkuSearchClient client = new ApifyTaobaoSkuSearchClient(props, transport);
    List<SkuSearchHit> hits = client.search("香薰", "taobao_tbk", 10);
    assertEquals(1, hits.size());
    assertEquals("9", hits.get(0).getRawRef());
}

@Test
void search_propagates_transport_failure() {
    ApifyActorTransport transport = (a, t, ms, b) -> { throw new IllegalStateException("http_error"); };
    // expect exception — Fallback 在 Task 3 接
}
```

- [ ] **Step 5: 实现 Transport + Client**（`pageSize` clamp 1..20；空 query → empty list 不调网）

- [ ] **Step 6: Run 相关测 PASS**

```bash
mvn -pl forma-application -am -Dtest=ApifyTaobaoHitMapperTest,ApifyTaobaoSkuSearchClientTest test
```

- [ ] **Step 7: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(sku): add Apify taobao search client and hit mapper

EOF
)"
```

---

### Task 3: `FallbackSkuSearchClient`

**Files:**
- Create: `.../sku/FallbackSkuSearchClient.java`
- Test: `FallbackSkuSearchClientTest.java`

**Interfaces:**
- Consumes: `SkuSearchPort primary`（Apify）、`SkuSearchPort fallback`（Mock）
- Produces: `search` 在 primary 抛异常或返回 empty → 调 fallback；日志 reason：`missing_token` | `timeout` | `http_error` | `empty_hits`
- **缺 Token：** 可由装配层直接用 Mock，或 Fallback 在调用前检查 `props.getApify().getToken()` 空白 → 不调 primary，记 `missing_token` 后 Mock（推荐：**装配层** `client=apify && blank token` → 只注册 Fallback(Mock, Mock) 或直接 Mock，并打一次启动期 warn/error）

钉死装配行为（实现 Task 4 遵守）：

```text
client=mock → Mock
client=apify && blank token → Mock + error missing_token（启动或首次 search）
client=apify && token → Fallback(Apify, Mock)
```

- [ ] **Step 1: 写 Fallback 测**

```java
@Test
void falls_back_when_primary_throws() {
    SkuSearchPort primary = (q, p, n) -> { throw new IllegalStateException("boom"); };
    SkuSearchPort mock = new MockSkuSearchClient();
    FallbackSkuSearchClient fb = new FallbackSkuSearchClient(primary, mock);
    List<SkuSearchHit> hits = fb.search("香薰", "taobao_tbk", 5);
    assertFalse(hits.isEmpty());
    assertTrue(hits.get(0).getDetailUrl().contains("mock.tbk.local"));
}

@Test
void falls_back_when_primary_returns_empty() {
    SkuSearchPort primary = (q, p, n) -> Collections.emptyList();
    ...
}
```

- [ ] **Step 2: Run FAIL → 实现 → PASS**

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(sku): fall back to mock when Apify search fails

EOF
)"
```

---

### Task 4: 装配 `SkuSearchPort` + `.env.example`

**Files:**
- Modify: `EbusPiToolCatalogConfiguration.java` — 删除硬编码 `new MockSkuSearchClient()`；注入 `SkuSearchProperties` 构建 Port
- Modify: `APP-META/docker-config/environment/.env.example` — 替换 `mode` 注释为 `client` + `APIFY_TOKEN`
- 若 starter 有 `application.yml`，增加：
  ```yaml
  ebus:
    sku-search:
      client: ${EBUS_SKU_SEARCH_CLIENT:mock}
      apify:
        actor-id: zen-studio/taobao-search-scraper
        timeout-ms: 120000
        token: ${APIFY_TOKEN:}
  ```
- Test: 现有 `SearchSkuToolHandlerTest` 仍绿（构造时继续直接 `new MockSkuSearchClient()` 即可）

**Interfaces:**
- Produces: 唯一 `@Bean SkuSearchPort` 按上表装配

- [ ] **Step 1: 改 Configuration**

```java
@Bean
public SkuSearchPort skuSearchPort(SkuSearchProperties props) {
    MockSkuSearchClient mock = new MockSkuSearchClient();
    if (!"apify".equalsIgnoreCase(props.getClient())) {
        return mock;
    }
    String token = props.getApify().getToken();
    if (token == null || token.trim().isEmpty()) {
        // log error missing_token
        return mock;
    }
    ApifyTaobaoSkuSearchClient apify = new ApifyTaobaoSkuSearchClient(props, new ApifyHttpUrlConnectionTransport());
    return new FallbackSkuSearchClient(apify, mock);
}
```

- [ ] **Step 2: 更新 `.env.example`**

```bash
# SKU search client: mock | apify (default mock)
# EBUS_SKU_SEARCH_CLIENT=mock
# APIFY_TOKEN=
# ebus.sku-search.apify.actor-id=zen-studio/taobao-search-scraper
```

- [ ] **Step 3: 编译 + 跑 sku 相关测**

```bash
mvn -pl forma-application -am -Dtest=SkuSearchPropertiesTest,ApifyTaobaoHitMapperTest,ApifyTaobaoSkuSearchClientTest,FallbackSkuSearchClientTest,SearchSkuToolHandlerTest test
```

- [ ] **Step 4: Commit**

```bash
git commit -m "$(cat <<'EOF'
feat(sku): wire ebus.sku-search.client bean factory for apify

EOF
)"
```

---

### Task 5: Picklist Skill 文案 + mirrors

**Files:**
- Modify: `forma-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/SKILL.md`
- Modify: `.../references/output.md`（disclaimer 推荐句）
- Sync: application + pi-agent test `scenes/ecommerce/ecommerce-picklist/`

**文案钉死：**

- description：去掉「仅淘宝客/多多客推广池」；改为「经配置的商品检索（如 Mock / Apify 淘宝搜）产出…」
- Tool `search_sku` 表：说明链接来自工具；来源由服务端 `ebus.sku-search.client` 决定
- Boundaries：不宣称联盟官方推广池
- Disclaimer 推荐整句：`候选基于配置的商品检索抽样与助手排序，非实时平台全站行情。点击可打开商品页核对。`
- **必须保留字面量** `非实时平台全站行情`

- [ ] **Step 1: 改 starter 两文件**

- [ ] **Step 2: rsync mirrors + diff -qr**

```bash
rsync -a --delete \
  forma-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/ \
  forma-application/src/test/resources/scenes/ecommerce/ecommerce-picklist/
rsync -a --delete \
  forma-starter/src/main/resources/scenes/ecommerce/ecommerce-picklist/ \
  pi-agent/src/test/resources/scenes/ecommerce/ecommerce-picklist/
```

- [ ] **Step 3: Commit**

```bash
git commit -m "$(cat <<'EOF'
docs(skill): align picklist disclaimer with configurable sku search

EOF
)"
```

---

### Task 6: 手工冒烟（人）

- [ ] `client=mock`：选品仍出 mock 链  
- [ ] `EBUS_SKU_SEARCH_CLIENT=apify` + 真实 `APIFY_TOKEN`：选品 Computer 出现 `item.taobao.com`（或天猫）https；非 `mock.tbk.local`  
- [ ] 去掉 Token 仍能启动；选品回退 Mock；日志含 `missing_token`  
- [ ] 记录费用：Apify 按条计费，控制 `pageSize`  

---

## Plan self-review

1. Spec 覆盖：client 名、Actor、映射、Fallback、Skill、env、验收均有 Task。  
2. 无 TBD；API URL 与 `~` actor id 已钉死。  
3. 与现网 `SearchSkuToolHandler` / Mock 兼容；不改 settle。  

---

## Execution handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-29-apify-sku-search-client.md`.

**Two execution options:**

1. **Subagent-Driven (recommended)** — 每 Task 新 subagent + 审查  
2. **Inline Execution** — 本会话按 executing-plans 连续做  

Which approach?
