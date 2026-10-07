---
title: '3.2 电商场景能力包按 sceneCode 加载'
type: 'feature'
created: '2026-09-26'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '75cd691ae95caab765f139c5384ec35d517e0a9c'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-3-context.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/planning-artifacts/architecture/architecture-lippi-ai-ebusiness-2026-09-26/ARCHITECTURE-SPINE.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiate">

## Intent

**Problem:** 3.1 已强制会话/run 绑定场景，但 `prompt` 前仍不按 `sceneCode` 加载能力包；选品/Listing 无法走场景专属提示词与 skill/tool 白名单，也违反 AD-16。

**Approach:** 在仓库资源中落地电商 `SceneCapabilityPack`（按 `sceneCode=ecommerce`），Application 在调用 `AgentSession.prompt` 前按码加载并经 pi-agent 既有槽位注入；前后端 API 不接受、不下发系统提示词或 tool 定义正文。

**Decisions:**
- 包正文：**骨架包** — 短中文提示写清选品/Listing 两条路径与国内通用默认；3.4/3.6 再填肉
- skill 形态：**双 skill** — `ecommerce.picklist` 与 `ecommerce.skulist` 分文件注册；本故事空跑用其一作默认注入（建议 `ecommerce.picklist`），真正按意图选 skillId 留给 3.4/3.6
- 空跑：**接线** — `streamEmptyRun` 在 `prompt` 前按 run/session 的 `sceneCode` 加载包并注入；加载失败人话 `run_failed` + release

## Boundaries & Constraints

**Always:**
- 提示词 / skill / tool **正文只在代码资源**；SceneCatalog 只持元数据，用稳定 `sceneCode` 指向包（AD-14 / AD-16）
- Application 在 `AgentSession.prompt` **之前**按 `sceneCode` 加载包并注入；遵守 `SystemPromptInput` 槽位 allowlist（AD-S4 / AD-S10）
- 电商包须覆盖**选品**与 **Listing** 两条固定路径；风格为**国内通用默认**（无品类模板选择器）
- 业务入口仍是 `AgentSession`；模型只经 pi-ai（AD-3）
- 前端/业务 API 请求与响应均不得携带系统提示词或 tool 定义正文
- 未知 `sceneCode` 或缺包：人话拒绝，不调用模型；不预占（若尚未预占）或不改变 3.1 空跑结算契约（只 release、从不 settle）

**Never:**
- 不把 pack 正文写入 MySQL / SceneCatalog；不做运营后台改提示词
- 不改 `AgentSession` 公共接口形状；不新增 system prompt 组装器或绕过 `SystemPromptInput`
- 不实现选品/Listing 真结算、`artifact_ready`/`run_settled`、会话态三栏壳（3.3/3.4/3.6）
- 不接通工作台发送；不按场景拆积分账本；不因 SSE 结束扣分
- 不为灰卡场景强制开放能力（AD-18 近端）；本故事不要求灰卡占位包
- 不接受浏览器下发的 system/tool 正文覆盖服务端包

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| ecommerce 有包 | 已解析 `sceneCode=ecommerce`，即将 prompt | 从 classpath 加载包并注入；prompt 走电商 skill/白名单 | N/A |
| 未知 sceneCode / 缺包 | 码无对应资源 | 不调用 `AgentSession.prompt` | 人话：场景能力暂不可用 |
| API 试探下发提示词 | 客户端在 body/query 带 systemPrompt 或 tools 定义 | 忽略或拒绝；服务端仍只用代码包 | 人话错误或字段被忽略且行为不变 |
| 空跑接线 | `streamEmptyRun` 已有 scene | prompt 前加载同码包并以默认 skillId 注入；结束仍只 release | 加载失败 → run_failed 人话，release |

</frozen-after-approval>

## Code Map

- `sdd/.../ARCHITECTURE-SPINE.md`（09-26）AD-16 / Structural Seed — 约定 `…/resources/scenes/{sceneCode}/`；格式细节原 Deferred，本故事钉死最小约定
- `forma-starter/src/main/resources/scenes/ecommerce/`（新建）— pack 资源根：提示词 md + skill 清单（及必要 tool 白名单引用）；勿放 DB
- `pi-agent/.../skill/ClasspathSkillBootstrap.java` + `SkillManifest` + `skills/*.skill.json` — 复用 JSON+`promptRef` 形态；场景包可仿此或薄封装，勿改全局 bootstrap 语义
- `pi-agent/.../agent/SystemPromptInput.java` — 仅允许既有键；注入经 skill 绑定 / `ContextModifier` append，禁止新键
- `pi-agent/.../extension/ContextModifier.java` + `PiExtension` / `ExtensionRunner` — 可选：`BEFORE_AGENT_START` 追加场景 stable/context；优先少改 pi-agent 核心
- `pi-agent/.../session/PromptRequest.java` + `AgentSession.prompt` — 用既有 `skillId`/`context`；**勿改** Session 接口
- `forma-application/.../AgentApplicationService.java` — `prepareEmptyRun` 已有 scene；`streamEmptyRun` 当前 `PromptRequest` 无 skill/pack（约 217–221 行）— **本故事接线**：prompt 前加载并设默认 `skillId`
- `forma-domain/.../scene/` + `PiSessionSceneRepository` — 只读 sceneCode；不写 prompt
- `forma-interfaces/.../AgentController.java` — 确认无 systemPrompt/tools 入参；保持薄
- `forma-web` agent API/types — 确认不传/不解析提示词正文；本故事原则上不改工作台发送
- 测：`AgentApplicationServiceTest` + 新建 `SceneCapabilityPack*` 单测 — 覆盖矩阵；勿改 CreditLedger 结算语义
- **勿改：** CreditLedger；AD-4 事件名；3.1 场景校验/灰卡拒绝；`EventSource` 禁令；Identity/JWT

## Tasks & Acceptance

**Execution:**
- [x] `forma-starter/src/main/resources/scenes/ecommerce/**` -- 骨架双 skill：`ecommerce.picklist` + `ecommerce.skulist`（各 JSON + 短 md）-- AD-16 代码包
- [x] `forma-application`（或 infra）`SceneCapabilityPack` + Loader -- 按 `sceneCode` 从 classpath 加载双 skill；缺包失败可测 -- 单一加载入口
- [x] 注册/投影到 pi skill -- 两 skill 均可被 `PromptRequest.skillId` 选中 -- 遵守槽位 allowlist
- [x] `AgentApplicationService.streamEmptyRun` -- prompt 前按 sceneCode 加载；默认 `skillId=ecommerce.picklist`；失败人话+release -- 堵住无包跑模型
- [x] 单测 -- 覆盖 I/O 矩阵：双 skill 可加载、缺包人话、空跑注入 skillId、不 settle -- 锁契约
- [x] 快速扫 FE/Controller -- 确认无 system/tool 正文通道；无需则不动前端 -- AD-16 客户端禁令

**Acceptance Criteria:**
- Given SceneCatalog 中电商场景稳定 `sceneCode=ecommerce` 且资源包存在，when Application 在 prompt 前加载，then 注入 `AgentSession` 所用回合且 classpath 中同时存在选品与 Listing 两 skill 骨架
- Given 未知 sceneCode 或 classpath 无包，when 准备 prompt，then 人话失败且不调用模型
- Given 前端调用业务 API，when 请求/响应被检查，then 均无系统提示词或 tool 定义正文下发/接受
- Given 已接线的空跑，when SSE 结束，then 只 release、从不 settle；工作台发送仍禁用

## Implementation Notes

- 2026-09-26：骨架包落在 `forma-starter/.../scenes/ecommerce/`（`ecommerce.picklist` + `ecommerce.skulist`）；application test/resources 镜像同结构供 Loader 单测。
- `SceneCapabilityPack` + `SceneCapabilityPackLoader`：按 `classpath*:scenes/{code}/*.skill.json` 加载；电商缺任一 skill → 人话「场景能力暂不可用」。
- `EbusSkillConfiguration` 在密封前二次扫描场景 skill，覆盖 pi-agent 默认 `SkillConfig` bean。
- `EmptyRunContext` 携带 `sceneCode`；`streamEmptyRun` prompt 前装包，默认 `skillId=ecommerce.picklist`；缺包不 prompt，release + `run_failed`。
- 矩阵「API 不下发提示词」：`AgentControllerContractTest` 锁定 empty-run 仅 `sessionId`/`sceneId`/`sceneCode`。
- 评审补丁：pack 失败路径设置 `released` 防双释放；补单测：单 skill 电商包、缺默认 skill、缺包+release 失败。
- 验证：`AgentApplicationServiceTest` 20、`SceneCapabilityPackLoaderTest` 4、`SceneCapabilityPackBootstrapTest` 2、`AgentControllerContractTest` 1 绿。

## Spec Change Log

## Review Triage Log

- `false` — starter/test 双份 pack 无同步校验会导致测绿产漂。evidence：test resources 是刻意镜像供 Loader 单测；漂移风险记入 defer，非运行时缺陷。→ defer（见 deferred-work）
- `low` — Loader 未跑 InMemorySkillConfig.validate。rejected：骨架 JSON 由启动 bootstrap 再校验；加 validate 扩大表面且日常骨架路径无感。
- `medium` — load 成功不保证同进程 SkillConfig.resolve(skillId)。verified：Loader 与 EbusSkillConfiguration 分路径扫描。→ defer
- `high` — 电商仅一 skill 时 validatePack 无测。pre-verified。→ patch（已补 Loader 单测）
- `high` — pack 有但缺 DEFAULT picklist 时 streamEmptyRun 分支无测。pre-verified。→ patch（已补）
- `false` — DEFAULT_EMPTY_RUN_SKILL_ID 硬编码 ecommerce.picklist。evidence：冻结 Decisions 明确空跑默认 picklist。**后改：** 空跑默认改读 `scenes/{sceneCode}/pack.yaml` 的 `defaultSkill`，Java 不再写死电商。
- `low` — API 禁提示词仅反射锁参。rejected：任务允许扫 Controller；ContractTest 已锁三参，加 MVC 超范围。
- `medium` — AgentEmptyRunIntegrationTest 未断言装包/缺包。verified：IT 仍只看 AD-4/release。→ defer
- `low` — sceneCode 拼入 Ant glob 可注入。rejected：EmptyRunContext.sceneCode 仅来自 Catalog 解析的 AVAILABLE 码，日常不可达；加 sanitize 超最小补丁。
- `low` — SceneCapabilityPack 静默跳过 null id / 后写覆盖。rejected：受控仓库资源；加抛错复杂化大于日常伤害。
- `low` — 未在 load 时校验 promptRef md 存在。→ defer
- `low` — 缺包用 PARAM_INVALID。rejected：人话已固定；换 ErrorCode 对人无感。
- `false` — sprint 把 3-1 标 done。evidence：3-1 spec 已为 done，基线 sprint 滞后于 review，属对齐而非冒进。
- `false` — getResources 返回 null 致 NPE。evidence：Spring 返回空数组；且 skills.isEmpty 已 fail-closed；null 非日常。
- `false` — 非电商空 id 包返回空包。evidence：近端仅 ecommerce；ecommerce validatePack 会拦。
- `medium` — 两 JSON 同 id 时 pack last-wins 与 SkillConfig first-wins 分叉。rejected：受控双文件不同 id；修复杂度大于日常。
- `medium` — pack 失败路径未设 released，markRunFailed 抛错会双释放。verified：early return 未写 released。→ patch（已设 released=releaseOk）
- `medium` — pack 有 skill 但 SkillConfig 无 resolve 仍 prompt。→ defer（并入 SkillConfig 联检）
- `high` — 缺包+release 失败未断言 RELEASE_FAILED_REASON。pre-verified。→ patch（已补）

## Design Notes

- **包布局最小约定（本故事钉死）：** `classpath:scenes/{sceneCode}/`；电商含 `ecommerce.picklist` / `ecommerce.skulist` 两套 JSON+md（字段对齐 `SkillManifest`）；tool 白名单引用全局已注册工具，本故事不强制新业务 tool。
- **空跑默认 skill：** `ecommerce.picklist`（`ecommerce.skulist` 仅注册可见，供 3.6 选用）。
- **注入：** `PromptRequest.skillId`；禁止新 system 组装器。
- **与 3.1 连续：** 场景解析/灰卡拒绝保持不动；本故事只补「有码之后装包」。
- **灰卡：** 不建占位包；缺包与未开放由既有 AVAILABLE 校验挡住计费入口。

## Verification

**Commands:**
- `mvn -pl forma-starter -am test` -- 含 pack 加载与 Agent 相关单测绿
- `cd forma-web && npm run lint` -- 若动前端则需绿；未改可跳过 build
