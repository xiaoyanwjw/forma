# Generic ArtifactStore Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `ebus_picklist` / `ebus_picklist_item` with one `ebus_artifact` table (JSON payload); keep picklist validation in application layer; `artifact_type` is `picklist` | `sku`.

**Architecture:** Domain `Artifact` + `ArtifactRepository` own physical persistence. `PicklistApplicationService.persistUsable` validates then saves `ArtifactType.PICKLIST` with Jackson payload. Agent settle / SSE / Computer view stay unchanged aside from wiring `sceneCode` into persist.

**Tech Stack:** Java 8, Spring Boot 2.7, MyBatis, MySQL JSON / H2 CLOB, Jackson `ObjectMapper`.

**Spec:** `docs/superpowers/specs/2026-09-26-generic-artifact-store-design.md`

## Global Constraints

- Table `ebus_artifact` only; delete picklist tables; **no data migration** (dev rebuild)
- `artifact_type` values: `picklist` | `sku` (sku write not in this plan)
- `GenerationRun.artifactRef` = `artifact.biz_id`
- Picklist shape validation stays in application (8–12 items, disclaimer contains `非实时`, field max lens)
- H2: `payload_json` as `CLOB`; MySQL: `JSON`
- Do not persist Computer `view`; do not implement history UI or sku persist
- Spine AD-6 must gain ArtifactStore; Picklist/Listing become logical shape owners

## File Map

| Path | Role |
|------|------|
| `APP-META/bootstrap/sql/010_ebus_artifact.sql` | Replace former `010_ebus_picklist.sql` |
| `lippi-ai-ebus-starter/src/test/resources/schema-h2.sql` | H2 DDL for artifact |
| `domain/.../artifact/model/Artifact.java` | Aggregate |
| `domain/.../artifact/model/ArtifactType.java` | Enum `PICKLIST("picklist")`, `SKU("sku")` |
| `domain/.../artifact/repository/ArtifactRepository.java` | Port |
| `infrastructure/.../po/ArtifactPO.java` | MyBatis PO |
| `infrastructure/.../mapper/ArtifactMapper.java` + `.xml` | insert / selectByBizId / selectByRunId |
| `infrastructure/.../repository/business/artifact/ArtifactRepositoryImpl.java` | Adapter |
| `application/.../picklist/service/PicklistApplicationService.java` | Validate → build Artifact → save |
| `application/.../picklist/command/PersistPicklistCommand.java` | Add `sceneCode` |
| `application/.../agent/service/AgentApplicationService.java` | Pass `sceneCode` into persist command |
| Delete | Picklist/PicklistItem domain, PicklistMapper*, PicklistPO*, PicklistRepository*, `010_ebus_picklist.sql` |
| Spine + design spec status | Docs |

---

### Task 1: SQL + H2 schema

**Files:**
- Create: `APP-META/bootstrap/sql/010_ebus_artifact.sql`
- Delete: `APP-META/bootstrap/sql/010_ebus_picklist.sql`
- Modify: `lippi-ai-ebus-starter/src/test/resources/schema-h2.sql` (replace picklist tables)

**Interfaces:**
- Produces: MySQL + H2 DDL matching spec columns

- [ ] **Step 1: Write MySQL DDL**

```sql
-- ArtifactStore: generic billed artifacts (picklist | sku)
CREATE TABLE IF NOT EXISTS ebus_artifact (
    id              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '库内自增主键（禁止对外暴露）',
    biz_id          VARCHAR(36)  NOT NULL COMMENT '业务成果 ID（UUID = GenerationRun.artifactRef）',
    user_id         VARCHAR(36)  NOT NULL COMMENT '用户业务 ID（UUID）',
    run_id          VARCHAR(36)  NOT NULL COMMENT 'GenerationRun 业务 ID（UUID）',
    artifact_type   VARCHAR(32)  NOT NULL COMMENT 'picklist | sku',
    scene_code      VARCHAR(64)  NOT NULL COMMENT '场景 code，如 ecommerce',
    template_id     VARCHAR(64)  NULL COMMENT '选品模板；sku 可空',
    title           VARCHAR(256) NOT NULL COMMENT '列表摘要',
    payload_json    JSON         NOT NULL COMMENT '类型化载荷',
    created_at      DATETIME(3)  NOT NULL COMMENT '创建时间',
    updated_at      DATETIME(3)  NOT NULL COMMENT '更新时间',
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_artifact_biz (biz_id),
    UNIQUE KEY uk_ebus_artifact_run (run_id),
    KEY idx_ebus_artifact_user_time (user_id, created_at),
    KEY idx_ebus_artifact_user_type (user_id, artifact_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Adam 通用成果 ArtifactStore';
```

- [ ] **Step 2: Replace H2 tables**

Remove `ebus_picklist` / `ebus_picklist_item`. Add:

```sql
CREATE TABLE IF NOT EXISTS ebus_artifact (
    id              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    biz_id          VARCHAR(36)  NOT NULL,
    user_id         VARCHAR(36)  NOT NULL,
    run_id          VARCHAR(36)  NOT NULL,
    artifact_type   VARCHAR(32)  NOT NULL,
    scene_code      VARCHAR(64)  NOT NULL,
    template_id     VARCHAR(64)  NULL,
    title           VARCHAR(256) NOT NULL,
    payload_json    CLOB         NOT NULL,
    created_at      TIMESTAMP    NOT NULL,
    updated_at      TIMESTAMP    NOT NULL,
    CONSTRAINT uk_ebus_artifact_biz UNIQUE (biz_id),
    CONSTRAINT uk_ebus_artifact_run UNIQUE (run_id)
);
CREATE INDEX IF NOT EXISTS idx_ebus_artifact_user_time ON ebus_artifact (user_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ebus_artifact_user_type ON ebus_artifact (user_id, artifact_type);
```

- [ ] **Step 3: Delete old SQL file** `010_ebus_picklist.sql`

- [ ] **Step 4: Commit**

```bash
git add APP-META/bootstrap/sql/010_ebus_artifact.sql \
  APP-META/bootstrap/sql/010_ebus_picklist.sql \
  lippi-ai-ebus-starter/src/test/resources/schema-h2.sql
git commit -m "chore(db): replace picklist tables with ebus_artifact"
```

---

### Task 2: Domain Artifact + repository port

**Files:**
- Create: `lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/artifact/model/ArtifactType.java`
- Create: `lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/artifact/model/Artifact.java`
- Create: `lippi-ai-ebus-domain/src/main/java/com/xmut/ebus/domain/business/artifact/repository/ArtifactRepository.java`
- Test: `lippi-ai-ebus-domain/src/test/java/com/xmut/ebus/domain/business/artifact/model/ArtifactTypeTest.java` (or application-module unit if domain has no test module — prefer create under `lippi-ai-ebus-application/.../ArtifactTypeTest` only if domain lacks tests; check: use `lippi-ai-ebus-domain` test if exists, else put enum assert in Task 3)

**Interfaces:**
- Produces: `ArtifactType.fromCode(String)`, `Artifact.create(...)`, `ArtifactRepository.save/findById/findByRunId`

- [ ] **Step 1: Write failing enum test** (place in `lippi-ai-ebus-application/src/test/java/com/xmut/ebus/domain/artifact/ArtifactTypeTest.java` if domain has no test sources — first check `lippi-ai-ebus-domain/src/test`; if missing, create domain test dir)

```java
@Test
void codes_are_picklist_and_sku() {
    assertEquals("picklist", ArtifactType.PICKLIST.getCode());
    assertEquals("sku", ArtifactType.SKU.getCode());
    assertEquals(ArtifactType.PICKLIST, ArtifactType.fromCode("picklist"));
    assertEquals(ArtifactType.SKU, ArtifactType.fromCode("SKU")); // trim + ignoreCase
}
```

- [ ] **Step 2: Run — expect FAIL** (class missing)

Run: `mvn -pl lippi-ai-ebus-domain -am test -Dtest=ArtifactTypeTest -DfailIfNoTests=false`  
(If test lives in application module, `-pl lippi-ai-ebus-application`)

- [ ] **Step 3: Implement types + aggregate + port**

```java
public enum ArtifactType {
    PICKLIST("picklist"),
    SKU("sku");
    private final String code;
    // getCode(); fromCode(String) throws IllegalArgumentException if unknown
}

public class Artifact {
    private String id;           // biz_id
    private String userId;
    private String runId;
    private ArtifactType type;
    private String sceneCode;
    private String templateId;   // nullable
    private String title;
    private String payloadJson;  // raw JSON string
    private Instant createdAt;
    private Instant updatedAt;

    public static Artifact create(String id, String userId, String runId,
                                  ArtifactType type, String sceneCode,
                                  String templateId, String title,
                                  String payloadJson, Instant now) { /* setters */ }
    // getters/setters as other domain models
}

public interface ArtifactRepository {
    void save(Artifact artifact);
    Optional<Artifact> findById(String id);
    Optional<Artifact> findByRunId(String runId);
}
```

- [ ] **Step 4: Run test — PASS**

- [ ] **Step 5: Commit**

```bash
git commit -m "feat(artifact): add Artifact domain and repository port"
```

---

### Task 3: MyBatis ArtifactRepositoryImpl

**Files:**
- Create: `…/mybatis/po/ArtifactPO.java`
- Create: `…/mybatis/mapper/ArtifactMapper.java`
- Create: `…/resources/mybatis/mapper/ArtifactMapper.xml`
- Create: `…/repository/business/artifact/ArtifactRepositoryImpl.java`
- Delete: PicklistMapper.java/xml, PicklistPO, PicklistItemPO, PicklistRepositoryImpl, domain Picklist/PicklistItem/PicklistRepository
- Test: rewrite `lippi-ai-ebus-starter/src/test/java/com/xmut/ebus/PicklistRepositoryIntegrationTest.java` → `ArtifactRepositoryIntegrationTest.java`

**Interfaces:**
- Consumes: `ArtifactRepository`
- Produces: working save/find round-trip on H2

- [ ] **Step 1: Write failing integration test**

```java
@SpringBootTest
@ActiveProfiles("test")
class ArtifactRepositoryIntegrationTest {
    @Autowired ArtifactRepository artifactRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clean() {
        jdbcTemplate.update("DELETE FROM ebus_artifact");
    }

    @Test
    void saveAndFindByIdRoundTrip() {
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        String id = UUID.randomUUID().toString();
        String runId = UUID.randomUUID().toString();
        String payload = "{\"disclaimer\":\"基于通用知识推断，非实时平台数据\",\"items\":[]}";
        Artifact artifact = Artifact.create(
                id, "user-1", runId, ArtifactType.PICKLIST, "ecommerce",
                "domestic-generic-default", "选品清单", payload, now);
        artifactRepository.save(artifact);

        Optional<Artifact> loaded = artifactRepository.findById(id);
        assertTrue(loaded.isPresent());
        assertEquals(ArtifactType.PICKLIST, loaded.get().getType());
        assertEquals("ecommerce", loaded.get().getSceneCode());
        assertEquals(payload, loaded.get().getPayloadJson());
        assertEquals(id, artifactRepository.findByRunId(runId).get().getId());
    }
}
```

- [ ] **Step 2: Run — expect FAIL** (missing bean / table)

Run: `mvn -pl lippi-ai-ebus-starter -am test -Dtest=ArtifactRepositoryIntegrationTest -DfailIfNoTests=false`

- [ ] **Step 3: Implement Mapper + Impl** (mirror PicklistMapper style)

```xml
<insert id="insert" useGeneratedKeys="true" keyProperty="id" keyColumn="id">
  INSERT INTO ebus_artifact
    (biz_id, user_id, run_id, artifact_type, scene_code, template_id, title, payload_json, created_at, updated_at)
  VALUES
    (#{bizId}, #{userId}, #{runId}, #{artifactType}, #{sceneCode}, #{templateId}, #{title}, #{payloadJson}, #{createdAt}, #{updatedAt})
</insert>
```

PO field `artifactType` stores code string (`picklist`). Impl maps `ArtifactType.fromCode` / `getCode()`.

- [ ] **Step 4: Delete old picklist persistence + domain entity files** listed in File Map

- [ ] **Step 5: Run integration test — PASS**; fix any Spring wiring

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(artifact): persist ebus_artifact via MyBatis"
```

---

### Task 4: PicklistApplicationService → ArtifactStore

**Files:**
- Modify: `PersistPicklistCommand.java` — add `sceneCode`
- Modify: `PicklistArtifactParser.java` — leave as-is (no scene); Agent sets scene
- Modify: `PicklistApplicationService.java` — inject `ArtifactRepository` + `ObjectMapper`; remove `PicklistRepository`
- Modify: `PicklistArtifactDTO.java` — remove `PicklistItem.from` dependency on domain; keep constructors
- Modify: `AgentApplicationService.java` — after parse, rebuild command with `.sceneCode(context.getSceneCode())` or set on builder before persist
- Modify: `PicklistApplicationServiceTest.java` — mock `ArtifactRepository`; capture `Artifact`
- Keep: Parser / Projector / DTO / defaults constants

**Interfaces:**
- Consumes: `ArtifactRepository.save`
- Produces: same `PicklistArtifactDTO` (`picklistId` = artifact biz id)

- [ ] **Step 1: Update unit test to expect Artifact save**

```java
@Mock ArtifactRepository artifactRepository;
// service = new PicklistApplicationService(artifactRepository, objectMapper, clock);

@Test
void persistUsableSavesPicklistArtifact() throws Exception {
    PersistPicklistCommand cmd = PersistPicklistCommand.builder()
            .userId("u1").runId("r1").sceneCode("ecommerce")
            .templateId("domestic-generic-default")
            .disclaimer("基于通用知识推断，非实时平台数据")
            .items(items(8)).build();
    PicklistArtifactDTO dto = service.persistUsable(cmd);
    assertEquals(8, dto.getItems().size());
    ArgumentCaptor<Artifact> captor = ArgumentCaptor.forClass(Artifact.class);
    verify(artifactRepository).save(captor.capture());
    Artifact saved = captor.getValue();
    assertEquals(ArtifactType.PICKLIST, saved.getType());
    assertEquals("ecommerce", saved.getSceneCode());
    assertEquals("选品清单", saved.getTitle());
    assertTrue(saved.getPayloadJson().contains("\"items\""));
    assertEquals(saved.getId(), dto.getPicklistId());
}
```

- [ ] **Step 2: Run — FAIL** (API mismatch)

Run: `mvn -pl lippi-ai-ebus-application -am test -Dtest=PicklistApplicationServiceTest -DfailIfNoTests=false`

- [ ] **Step 3: Implement persistUsable**

After field validation (same as today):

```java
String artifactId = UUID.randomUUID().toString();
String sceneCode = StringUtils.requireHasText(command.getSceneCode(), "场景不能为空");
// build List<PicklistItemDTO> dtoItems from PersistPicklistItemCommand (no domain PicklistItem)
Map<String, Object> payload = new LinkedHashMap<String, Object>();
payload.put("disclaimer", disclaimer);
if (assumptions != null) payload.put("assumptions", assumptions);
payload.put("items", /* list of maps with camelCase keys */);
String json = objectMapper.writeValueAsString(payload);
Artifact artifact = Artifact.create(
        artifactId, userId, runId, ArtifactType.PICKLIST, sceneCode,
        templateId, "选品清单", json, now);
artifactRepository.save(artifact);
return new PicklistArtifactDTO(artifactId, runId, templateId, disclaimer, assumptions, dtoItems);
```

On Jackson failure: wrap as `BusinessException(PARAM_INVALID, MSG_UNUSABLE)`.

- [ ] **Step 4: Wire sceneCode in AgentApplicationService**

```java
PersistPicklistCommand parsed = picklistArtifactParser.parse(...);
PersistPicklistCommand persistCommand = PersistPicklistCommand.builder()
        .userId(parsed.getUserId())
        .username(parsed.getUsername())
        .runId(parsed.getRunId())
        .sceneCode(context.getSceneCode())
        .templateId(parsed.getTemplateId())
        .disclaimer(parsed.getDisclaimer())
        .assumptions(parsed.getAssumptions())
        .items(parsed.getItems())
        .build();
```

(Or add `toBuilder` / wither — prefer explicit rebuild.)

- [ ] **Step 5: Run**

```bash
mvn -pl lippi-ai-ebus-application -am test -Dtest=PicklistApplicationServiceTest,AgentApplicationServiceTest,PicklistViewProjectorTest,PicklistArtifactParserTest -DfailIfNoTests=false
mvn -pl lippi-ai-ebus-starter -am test -Dtest=ArtifactRepositoryIntegrationTest,AgentPicklistRunIntegrationTest -DfailIfNoTests=false
```

Expected: SUCCESS

- [ ] **Step 6: Commit**

```bash
git commit -m "feat(picklist): persist usable artifacts via ArtifactStore"
```

---

### Task 5: Spine + spec status + verification

**Files:**
- Modify: `sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md` (AD-6 / AD-7 / ER if needed)
- Modify: `docs/superpowers/specs/2026-09-26-generic-artifact-store-design.md` status → `implemented`
- Optional note in `sdd/implementation-artifacts/deferred-work.md` if local compose needs `down -v`

- [ ] **Step 1: Patch AD-6 ownership table**

Add row:

| ArtifactStore | 唯一物理写入 `ebus_artifact`（及同族存储） |

Change PicklistArtifact / ListingArtifact lines to: define usable shape + validation for `picklist` / `sku`; **do not** own dedicated tables.

AD-7: 「持久化」= write ArtifactStore.

ER diagram: `GenerationRun ||--o| Artifact` instead of Picklist/ListingPack tables if present.

- [ ] **Step 2: Full verification**

```bash
mvn -pl lippi-ai-ebus-starter -am test
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git commit -m "docs(spine): ArtifactStore owns ebus_artifact persistence"
```

---

## Spec coverage self-check

| Spec 要求 | Task |
|-----------|------|
| `ebus_artifact` DDL + drop picklist | 1 |
| Domain Artifact / type picklist\|sku | 2 |
| MyBatis + delete Picklist repo | 3 |
| Picklist validate → JSON → save | 4 |
| scene_code on row | 4 (`PersistPicklistCommand.sceneCode`) |
| Agent settle / artifactRef unchanged | 4 (DTO id still artifactRef) |
| Spine AD-6/7 | 5 |
| No sku write / no history UI / no view persist | Global Constraints |
| Dev rebuild, no data migration | Global Constraints |

## Placeholder scan

No TBD /「类似 Task N」; commands and key code included.

## Local MySQL note (operator)

After merge, refresh local compose volume if old tables exist:

```bash
docker compose -f APP-META/docker-config/docker-compose.yml down -v
# then bootstrap / up — 010_ebus_artifact.sql applies on fresh volume
```
