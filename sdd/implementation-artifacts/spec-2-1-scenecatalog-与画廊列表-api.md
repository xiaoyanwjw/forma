---
title: '2.1 SceneCatalog 与画廊列表 API'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '899224d6cc065ddf72f2bc68cbebe60fa885797f'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-2-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-24/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 首页场景画廊没有后端真相：没有 SceneCatalog，前端只能写死卡片；后续加场景会拆画廊范式，灰卡也无法作为真记录存在。

**Approach:** 落地 SceneCatalog（表+域）与只读画廊列表 API；种子近端四条场景（1 亮 + 3 灰）；附带前端 `api`/`types` 契约供 2.3 接线。不含画廊 UI、顶栏、工作台、能力包正文。

**Decisions:**
- 表名 `ebus_scene`；下一号 `APP-META/bootstrap/sql/008_ebus_scene.sql` + 同步 `schema-h2.sql`
- 稳定 `sceneCode`：`ecommerce` / `short_video` / `xiaohongshu` / `local_life`；状态枚举 `AVAILABLE` | `COMING_SOON`
- 展示名与卡片文案对齐 UX mockup `index.html`（电商开店 / 短视频带货 / 小红书种草 / 本地生活 + 既有短句）
- `GET /api/v1/scenes` 需 JWT（与积分/me 一致）；按 `sort_order` 升序返回；字段含 `bizId`、`sceneCode`、`displayName`、`status`、`sortOrder`、`summary`（或等价文案字段）；**不含**提示词/tool/skill 正文、不含图标 SVG（图标由 2.3 按 `sceneCode` 映射）
- 近端无公开写 REST；种子 SQL 即写入；域包名 `scene`（所有权称 SceneCatalog）
- 前端仅 `api` + `types`，不改 Landing/画廊页（→ 2.3）

## Boundaries & Constraints

**Always:**
- SceneCatalog 为场景元数据唯一写者；与 CatalogTemplate（品类）分离（AD-14）
- 灰卡也是真库行（`COMING_SOON`）；种子至少四条：电商 `AVAILABLE`，其余三灰
- 列表只读 Catalog；响应禁止系统提示词或 tool/skill 定义正文（AD-16）
- 对外业务 ID = UUID `biz_id`；库内可有 BIGINT 自增 PK；Controller 薄；读走 `*QueryService`
- SQL 追加下一号 `008_*.sql`；H2 `schema-h2.sql` 同步

**Never:**
- 画廊页 UI / 顶栏 token / 工作台空态（→ 2.2～2.5）
- 场景能力包 classpath 正文与按码加载（→ 3.2）
- GenerationRun / 会话绑场景硬拒 / 积分结算
- 把场景塞进 CatalogTemplate 或前端常量当唯一真相
- 本故事做 `COMING_SOON` 后端硬拒起会话（AD-18 后置）

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 种子后列表 | JWT + 已执行 008 | 4 条，按 sortOrder；电商 AVAILABLE，其余 COMING_SOON；含名/文案/码 | N/A |
| 未登录 | 无/坏 JWT | 与现有受保护 API 一致拒绝 | 401/人话按现有安全链 |
| 响应形状 | 任意成功列表项 | 无 prompt/tool/skill 字段 | N/A |
| 可扩展 | DB 再插一行 COMING_SOON/AVAILABLE | 再次 GET 即出现，无需改 API 契约 | N/A |
| sceneCode 唯一 | 重复 code 种子 | DDL UNIQUE 挡住 | 迁移失败可见 |

</frozen-after-approval>

## Code Map

- `sdd/context/02-be.md` — 域配方：`domain/business/<域>/` → QueryService → Controller；替换为 `scene`
- `…/domain/business/agent/model/GenerationRun.java` + `…/repository/GenerationRunRepository.java` — UUID `id` 聚合 + 仓储端口范本
- `…/infrastructure/persistence/mybatis/po/GenerationRunPO.java` · `GenerationRunMapper` · `…/repository/business/agent/GenerationRunRepositoryImpl.java` — PO/Mapper/Impl 配方
- `…/application/business/credit/query/CreditQueryService.java` · `…/interfaces/web/business/credit/CreditController.java` — 只读 GET + `ApiResponse` + `SecuritySupport.requireUserId()`
- `APP-META/bootstrap/sql/007_pi_resume_idempotency.sql` → 新建 `008_ebus_scene.sql`；`forma-starter/src/test/resources/schema-h2.sql` 同步
- `forma-starter/src/test/java/com/xmut/ebus/CreditIntegrationTest.java` — JWT + MockMvc 集成测金样
- `forma-web/src/api/business/credit/` · `types/business/credit.ts` — FE 只 HTTP + 类型分家范本
- UX 文案源：`sdd/planning-artifacts/ux-designs/ux-lippi-ai-ebusiness-2026-09-26/mockups/index.html`
- **Reuse：** GenerationRun 持久化形状；Credit 只读 Controller；02-be 目录配方
- **Do not change：** CreditLedger、AgentRuntime/SSE、pi_* 表、LandingPage 视觉（本故事不改 UI）、CatalogTemplate（尚无代码则勿新建品类域）

## Tasks & Acceptance

**Execution:**
- [x] `APP-META/bootstrap/sql/008_ebus_scene.sql` + `schema-h2.sql` — 建表 + 种子四场景（固定 biz_id 便于测）— Catalog 真相
- [x] `…/domain/business/scene/`（`Scene`、`SceneStatus`、`SceneRepository`）— 聚合与仓储端口 — AD-14 所有权
- [x] `…/infrastructure/persistence/`（PO/Mapper/XML + `SceneRepositoryImpl`）— 按 sort_order 列出 — 持久化
- [x] `…/application/business/scene/query/SceneQueryService` + DTO — 只读列表 — 用例边界
- [x] `…/interfaces/web/business/scene/SceneController` — `GET /api/v1/scenes` + JWT — API 面
- [x] `forma-starter/.../SceneCatalogIntegrationTest`（或邻名）— 覆盖 I/O 矩阵主路径 — 防回归
- [x] `forma-web/src/api/business/scene/` + `types/business/scene.ts` — 列表客户端契约 — 供 2.3

**Acceptance Criteria:**
- Given 迁移已应用，when `GET /api/v1/scenes` 带有效 JWT，then 返回四条且排序稳定，电商 `AVAILABLE`、三灰 `COMING_SOON`，字段含 bizId/sceneCode/展示名/状态/排序/文案
- Given 成功响应，when 检查 JSON，then 无系统提示词或 tool/skill 定义正文
- Given 额外插入一条场景行，when 再次 GET，then 新行出现且无需改 Controller 契约
- Given 无 JWT，when GET，then 与现有受保护 API 一致拒绝

## Implementation Notes

- 2026-09-26：落地 `ebus_scene` + `GET /api/v1/scenes`；种子固定 biz_id `a1000001-0001-4000-8000-000000000001`～`…0004`；文案对齐 UX mockup。
- 验证：`mvn -pl forma-starter -am test -Dtest=SceneCatalogIntegrationTest -DfailIfNoTests=false` 绿；`cd forma-web && npm run lint` 绿。
- H2 `schema-h2.sql` 种子前 `DELETE` 四码，避免 `sql.init mode=always` 重跑撞 UNIQUE。
- 已有 MySQL 卷需手动跑 `008_ebus_scene.sql`（compose initdb 仅首次生效）。
- Review patch：008 与 H2 同序 DELETE 四码；`ORDER BY sort_order, id`；`SceneStatus.fromCode` 对齐 CreditHoldStatus；IT 全量断言四卡并 finally 删 extra 行。

## Spec Change Log

## Review Triage Log

- `medium` — `008_ebus_scene.sql` 裸 INSERT 不可重复执行，与 H2 先 DELETE 再种不一致；重跑会撞 UNIQUE。verified：对照 008 与 schema-h2 种子块。→ patch
- `medium` — `ORDER BY sort_order ASC` 无二级键，同排序时列表顺序不稳定（AC「排序稳定」）。verified：SceneMapper.xml。→ patch
- `medium` — `listScenesWithJwtReturnsFourOrderedSeedRows` 仅完整断言电商卡，灰卡缺 displayName/summary/bizId。verified：IT 62–83 行 + verification-gap。→ patch
- `low` — `extraSceneAppears…` 写入非种子行，跨 IT 类共享上下文可能污染目录。verified：仅本类 BeforeEach 清非种子。→ patch
- `medium` — `SceneStatus.fromCode` 对 null 直接 `valueOf` 会 NPE，整表列表失败；应对齐 CreditHoldStatus。verified：SceneStatus.java。→ patch
- `medium` — 非法 status 字符串使整次 GET 失败。verified：toDomain→fromCode 无隔离。→ patch（清晰 IllegalArgumentException，与 null 同路径）
- `false` — sprint 仍标 in-progress「交付却未推进」。证据：本会话尚在 step-04，step-05 才会同步 review/done。
- `false` — epic-2-context 丢掉旧 GenerationRun/Pi 决策。证据：Build 因规划改版合法重编；旧系统仍有各自 spec。
- `false` — 无 SceneCatalog 写端口。证据：frozen 明确近端无写 REST、种子 SQL 写入。
- `false` — 缺 findByBizId/sceneCode。证据：本故事 Intent 仅为画廊列表；绑场景属 Epic 3。
- `low` — 缺重复 biz_id UNIQUE 测。rejected：日常不会撞固定种子；补测收益低且非用户路径（保留 UNIQUE DDL）。
- `maybe-false` — H2 同 biz_id 不同 scene_code 重种撞 uk。未验证可达到路径；种子固定配对。rejected。
- `medium`（verification-gap pre-verified）— 生产 `008` 从未在自动化中执行，仅 H2 孪生。→ defer
- `low` — H2 注释称「对齐 008」但 008 无 DELETE。随 008 补 DELETE 一并修正。→ patch（并入第一条）

## Design Notes

种子示例（实现可微调 UUID，须稳定写入迁移）：

```text
ecommerce     AVAILABLE    sort=1  电商开店
short_video   COMING_SOON  sort=2  短视频带货
xiaohongshu   COMING_SOON  sort=3  小红书种草
local_life    COMING_SOON  sort=4  本地生活
```

列表 DTO 只暴露画廊所需元数据；能力包路径不进响应。

## Verification

**Commands:**
- `mvn -pl forma-starter -am test -Dtest=SceneCatalogIntegrationTest` -- expected: 绿（若类名不同则对应该测）
- `cd forma-web && npm run lint` -- expected: 绿（若改了 api/types）

**Manual checks (if no CLI):**
- 本地 compose 起库后打 `GET /api/v1/scenes`（Bearer），目视四卡字段与状态
