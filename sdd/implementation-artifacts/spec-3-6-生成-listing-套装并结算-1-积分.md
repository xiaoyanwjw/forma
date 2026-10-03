---
title: '3.6 生成 Listing 套装并结算 1 积分'
type: 'feature'
created: '2026-09-27'
status: 'done'
route: 'dispatch'
review_loop_iteration: 0
baseline_commit: '218eeb140f8756a24908daaa1d753c9a05604a23'
context:
  - '{project-root}/sdd/implementation-artifacts/epic-3-context.md'
  - '{project-root}/sdd/implementation-artifacts/spec-3-4-生成选品清单并结算-1-积分.md'
  - '{project-root}/sdd/context/02-be.md'
  - '{project-root}/sdd/context/03-fe.md'
  - '{project-root}/forma-starter/src/main/resources/scenes/ecommerce/ecommerce-skulist/SKILL.md'
---

<frozen-after-approval reason="human-owned intent — do not modify unless human renegotiates">

## Intent

**Problem:** 电商工作台「上架」仍走演示 fixture；`ecommerce-skulist` 仅骨架，`SkillRunProfile` 对 skulist 抛「暂不支持」。用户拿不到可结算的 Listing 套装（主图方案 + 详情文案 + 展示说明），也无法扣/退 1 积分。

**Approach:** 对齐 3.4 已落地的通用计费 Generation 管线：预占 1 → 注入填肉后的 `ecommerce-skulist` → 双轨 JSON（`view`+`artifact`）→ 系统为成果挂载占位主图入 MinIO → 可用成果以 `artifact_type=sku` 落库 → settle → SSE；工作台「上架」胶囊/意图接真流，Computer 展示真成果。空跑与选品路径语义不变。

**Decisions:**
- 实现逻辑以 3.4 为样板：复用 `prepareGenerationRun` + `streamBilledRun`，新增 `SkillRunProfile.billedListing()`（`persistAs=sku`），**不**新建 Listing 领域 ApplicationService
- **交互（方案 1）：** 用户只需文字描述要上架的品（或从选品语境带入）；**不**要求用户上传图片；**不**由 Agent 调生图模型。Agent 产出主图方案说明 + 详情文案 + 展示说明；应用层在 settle 前把**系统占位图**写入 MediaStore/MinIO，得到真实 `mediaObjectId`，Computer 用签发 URL 显示主图位
- 入口：用户文本必填；可选关联选品条目（`picklistItemId`）后置到 payload，近端不强制从 Computer 点选
- 不做 `ask_human`；信息不足按国内通用默认假设生成并标明假设，结构不合格则 release
- Listing **不**强制 `search_sku`（选品工具门禁仅属 picklist）
- 风格固定 `templateId=domestic-generic-default`；无品类选择器
- **主图落地 B′：** 新建 `MediaStore` 端口；近端实现走 **MinIO**（S3 兼容；Compose 可起）；业务只存 `mediaObjectId`/`objectKey`，MySQL 不存大图；生产可换阿里云 OSS，端口不变
- 可用成果门槛：≥1 个真实 `mediaObjectId`（已写入 MediaStore）+ 非空详情文案 + 非空展示说明；skill 产出须含可读「主图方案」说明（文案），占位图由系统挂载

## Boundaries & Constraints

**Always:**
- 登录 JWT；余额 ≥1 才可开始；预占 1 → 可用成果落库后 `settle`；失败/取消 `release`；禁止仅因 SSE 结束扣分
- 成功产出：主图方案（媒体真相为 `mediaObjectId`，经 MediaStore/MinIO，MySQL 不存大图）+ 详情文案 + 展示说明；国内通用默认；可挂可选 `picklistItemId`
- 成果挂当前 `GenerationRun.artifactRef`；每次生成新 Run+新预占
- skill 正文 classpath：`sceneCode=ecommerce` + `skillId=ecommerce-skulist`；双轨契约 `view`+`artifact`
- SSE 闭合名与 3.4 相同；fetch+ReadableStream+JWT
- 单次 Run 可观测模型用量/成本（同 3.4 占位日志即可）
- 密钥走环境变量 / `APP-META`；不提交 MinIO/OSS 实密

**Never:**
- 不做选品计费改动；不做超范围拉回产品化（3.5）；不做下载/复制导出产品化（3.7）；不做重试反馈/近 60 天历史（3.8）
- 不接真实平台上架 API；不在浏览器下发系统提示词/tool 定义
- Agent 工具不写 CreditLedger；不把空跑改成 settle；不另造第二套主图 URL 真相（禁止只存外链当唯一真相）
- 不做品类模板选择器 / CatalogTemplate 运营后台
- 不强制 Listing 路径调用 `search_sku`
- 本故事不接阿里云 OSS 生产账号、不做模型生图流水线
- 不做用户上传图片 UI；不做 Agent 自动生图

## I/O & Edge-Case Matrix

| Scenario | Input / State | Expected Output / Behavior | Error Handling |
|----------|--------------|---------------------------|----------------|
| 成功 Listing | 已登录、余额≥1、电商会话；用户只发文字上架意图（无上传） | 预占→流式文案→系统占位图入 MinIO→落库 sku→settle→`artifact_ready`+`run_settled`；Computer 显主图位+文案 | N/A |
| 信息偏少但可假设 | 只说「帮我写上架」等 | 按国内通用默认假设生成并标明；结构仍须合格 | N/A |
| 余额不足 | available&lt;1 | 不预占、不跑模型；人话提示去套餐/升级 | 前端可见原因 |
| 生成失败/取消 | 模型失败、解析失败、用户取消 | `release`；`run_failed`；人话原因 | 不 settle |
| 成果不合格 | 缺详情文案/展示说明，或无真实 mediaObjectId | 不可用成果：release + `run_failed` | 不 settle、不假交付 |
| MinIO 不可用 | 对象存储上传/签发失败 | 视为生成失败：release + 人话「服务繁忙」类提示 | 不 settle |
| 选品对照 | 调 picklist / empty | 行为与现网一致，不受本故事破坏 | N/A |

</frozen-after-approval>

## Code Map

- `SkillRunProfile` — 新增 `PERSIST_SKU` / `billedListing()` / `resolve(ecommerce-skulist)`；可加 `isBilledListing()`；**勿**改 picklist/`search_sku` 门禁语义
- `AgentApplicationService` / `AgentController` — 复用 `prepareGenerationRun`+`streamBilledRun`；可选 `/runs/listing` 别名（对标 picklist）；**勿**让 empty settle
- `ArtifactPersistPlugin` — 已支持 `persistAs=sku`→`ArtifactType.SKU`；补 sku payload 校验（文案/展示/≥1 mediaObjectId）
- `GenerationOutputParser` / `ComputerViewResolver` / `NormalizeViewProjector` — 复用双轨；Listing 靠 skill 输出合格 `view`（`media`+`section`）
- `forma-starter/.../ecommerce-skulist/SKILL.md` + `references/output.md`（及 application/pi-agent test 镜像）— **填肉**：双轨契约、国内通用默认、禁胡编违规；`allowed-tools` 近端以 `read_skill` 为主
- `SceneCapabilityPackLoader.SKILL_SKULIST` — 已注册；确认电商包仍要求双 skill
- **新建** `MediaStore` 端口 + MinIO 实现（S3 API）；媒体元数据表（仅 id/objectKey/contentType 等，无大字段）；可读 URL 签发；**Listing 成功路径由应用层挂系统占位图**（非用户上传、非生图）；测试可用内存桩
- `APP-META/docker-config` — Compose 增加 MinIO 服务与 `.env.example` 占位（无实密）
- FE：`useAgentPicklistRun` 为样板 → `useAgentListingRun`；`api/business/agent/agent.ts` `streamListingRun`；`EcommerceWorkspacePlaceholder.vue` 上架接真 SSE（无上传控件）；成功后停用 `DEMO_LISTING_VIEW` 冒充
- `ComputerRenderer.vue` / `computerView.ts` — 已支持 `listingPreview`/`media`；接通 `mediaObjectId`→可读 URL
- **勿改：** CreditLedger 写者边界；AD-4 事件名；EventSource；选品 `search_sku` 强制门禁；3.7/3.8 范围；生产阿里云 OSS；用户上传/生图 UI

## Tasks & Acceptance

**Execution:**
- [x] `ecommerce-skulist/SKILL.md` + `references/output.md`（三处镜像） -- 填肉双轨：主图方案说明+详情文案+展示说明+templateId/假设；禁伪造实时指标与违规 -- skill 质量
- [x] `SkillRunProfile` + 相关单测 -- `billedListing` / resolve skulist；persistAs=sku -- 打开计费入口
- [x] `MediaStore` + MinIO + SQL 元数据 + Compose/`.env.example` -- 系统占位图上传得 mediaObjectId、签发可读 URL；无 MySQL 大图 -- 交互方案 1 / B′
- [x] `AgentController`（可选别名）+ `AgentApplicationService` 测 -- Listing 预占→文案→挂占位图→落库→settle；失败 release；不影响 picklist/empty -- FR3/9
- [x] sku 成果校验 -- 缺文案/展示/mediaObjectId 不 settle -- 锁可用成果
- [x] FE 工作台 -- 上架胶囊/意图接计费 SSE（无上传）；Computer 显真成果；余额不足/失败人话；刷积分 -- UX-DR5
- [x] 单测矩阵 -- 成功 settle、余额不足、失败 release、不合格不 settle、MinIO 失败、picklist/empty 回归 -- 锁计费

**Acceptance Criteria:**
- Given 已登录且余额≥1 的电商会话且用户仅输入文字，when 发起 Listing 生成，then 预占 1 分，成功落库主图方案说明+系统占位 mediaObjectId+详情文案+展示说明，并结算 1 分；全程无需用户上传、无模型生图
- Given 生成失败或成果不合格，when 结算判定，then 不扣或退回预占，并给出人话原因
- Given 余额不足，when 尝试开始 Listing，then 不可开始并提示套餐/升级
- Given 单次 Listing 结束，when 检查可观测性，then 该次模型用量/成本可统计
- Given 主图字段，when 成果落库，then 真相为 MediaStore 的 `mediaObjectId`，对象在 MinIO，MySQL 无图片大字段

## Implementation Notes

- 2026-09-27：复用 `prepareGenerationRun` + `streamBilledRun`；`SkillRunProfile.billedListing()`（`persistAs=sku`）；别名 `POST /api/v1/agent/runs/listing`。
- 结算前 `ListingMediaMountSupport` 挂系统 1×1 PNG 占位图 → `mediaObjectId`；`ArtifactPersistPlugin` 校验 heroPlan/详情/展示/≥1 mediaObjectId + `templateId=domestic-generic-default`。
- MediaStore：默认 `memory`（data URI）；Compose 可切 `minio`；元数据表 `011_ebus_media_object.sql`（无 BLOB）。
- FE：`useAgentListingRun` + 上架意图启发式；成功 Computer 用 SSE view（含 `src`），不再用 DEMO 冒充真成果。
- 评审补丁：MinIO 孤儿清理 + MediaStore.delete；hero 优先注入；SKU 文案；Compose 无 curl 健康检查；密钥启动校验；补 settle/门禁/别名/MinIO/mount 测。

## Spec Change Log

## Review Triage Log

- `defer` — epic-3-context 仍写「只进阿里云 OSS」，与近端 MinIO 决策并存。→ defer（规划编译上下文漂移；生产仍可换 OSS）
- `false` — Design Notes 管线漏写 ListingMediaMountSupport / 示例缺 heroPlan。→ reject（修 spec 正文被规则禁止；实现以 skill+代码为准）
- `false` — Code Map 称改 ComputerRenderer 接 mediaObjectId。→ reject：SSE view 已写入签发 `src`，当场 Computer 可读；历史重签属 3.8
- `medium` — MinioMediaStore put 成功后元数据 save 失败未删对象。→ patch
- `medium` — Compose MinIO healthcheck 用 curl，官方镜像常无 curl，starter 可能一直等。→ patch
- `low` — MSG_SKU_UNUSABLE 写「真实主图」易被理解为商品摄影。→ patch（改文案为占位主图/主图位）
- `medium` — injectHeroMedia 只补丁首个 `type=media`，非 hero 在前会错挂。→ patch（优先 `role=hero`）
- `medium`（verification-gap）— 无 MinioMediaStore / MediaObject 仓储测。→ patch
- `defer` — 矩阵「取消→release」：FE abort 不中断服务端（与 3.4 empty/picklist 同构）。→ defer
- `false` — FE 未走 `/runs/listing` 别名。→ reject：别名可选；产品路径 `/runs`+skillId 已测
- `low` — 无 Listing 专用 modelUsage 断言。→ reject（复用 3.4 日志路径，日常遇不到缺口）
- `medium` — put 成功后 issueReadUrl 失败留下孤儿媒体。→ patch
- `false` — ebus.media.store 非法值无显式校验。→ reject：无匹配 `@ConditionalOnProperty` 时启动即无 MediaStore bean，失败够响
- `medium` — store=minio 但密钥空白仍建 Client。→ patch
- `false` — mediaObjectIds 仅非空即可、假 id 可 settle。→ reject：Listing 路径 mount 会覆盖为 MediaStore 真实 id
- `false`（claim）— AC「对象在 MinIO」与默认 memory 冲突。→ reject：意图为 Compose/minio；单测用 memory 属约定
- `medium`（verification-gap）— Listing settle 失败文案 LISTING_SETTLE_FAILED 无测。→ patch
- `medium`（verification-gap）— SKU 门禁仅测空 media，未测坏 templateId/缺文案。→ patch
- `medium`（verification-gap）— `/runs/listing` 别名无合同/集成测。→ patch
- `medium`（verification-gap）— view 无 media 块时合成 hero 分支无测。→ patch


## Design Notes

### 对齐 3.4 管线（复用，非分叉）

```
prepareGenerationRun(reserveOne)
  → streamBilledRun(prompt + skillId)
  → GenerationOutputParser(view+artifact)
  → ComputerViewResolver
  → ArtifactPersistPlugin(persistAs=sku)
  → settle → artifact_ready / run_settled
```

### 成果形状（示意；最终以 `references/output.md` 为准）

```json
{
  "view": {
    "version": 1,
    "title": "硅胶沥水垫 · 上架素材",
    "blocks": [
      { "type": "media", "role": "hero", "mediaObjectId": "…", "placeholder": "主图方案", "alt": "主图" },
      { "type": "section", "heading": "详情标题", "body": "…" },
      { "type": "section", "heading": "详情正文", "body": "…" },
      { "type": "section", "heading": "展示说明", "body": "…" }
    ]
  },
  "artifact": {
    "title": "硅胶沥水垫 · 上架素材",
    "templateId": "domestic-generic-default",
    "detailTitle": "…",
    "detailBody": "…",
    "displayNotes": "…",
    "mediaObjectIds": ["…"],
    "picklistItemId": null,
    "assumptions": "可选"
  }
}
```

## Verification

**Commands:**
- `mvn -pl forma-starter -am test` -- 相关后端测绿
- `cd forma-web && npm test -- EcommerceWorkspace ComputerRenderer agent` -- 工作台/Computer 相关测绿
- `cd forma-web && npm run lint` -- 无新增 lint 错误

**Manual checks:**
- 登录有余额：工作台上架 → Computer 出真套装 → 积分 −1
- 余额 0：无法开始，提示升级
- 选品路径仍可结算；空跑仍不 settle
