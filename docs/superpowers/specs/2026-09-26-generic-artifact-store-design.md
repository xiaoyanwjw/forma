# 通用 ArtifactStore：选品 / Listing 共用落库

日期：2026-09-26  
状态：draft（待人审）  
范围：物理表从 `ebus_picklist*` 收成 `ebus_artifact`；逻辑所有权仍分选品 / Listing  
前置：故事 3.4 已用专用表交付；本变更属 course-correction（对齐「存储通用、业务形状专用」）

## 问题

`ebus_picklist` + `ebus_picklist_item` 只服务选品字段，Listing 与后续场景成果会再复制一套表与 Repository。近端需要的是**通用成果存储**（谁、哪次 Run、什么类型、JSON 载荷），不是「全世界共用一套选品列」。

## 目标

1. 一张 **`ebus_artifact`** 承载所有计费可用成果；`GenerationRun.artifactRef` = `artifact.biz_id`。
2. 选品校验 / 解析 / Computer 投影仍在应用层 `picklist` 包（类型化），落库前序列化进 `payload_json`。
3. 上架素材 / SKU（3.6）复用同表、`artifact_type=sku`，不新开表。
4. 更新 Spine AD-6：逻辑所有者保留；**物理写口**归 **ArtifactStore**。

## 非目标

- 不做历史页 UI / 导出（3.7/3.8）。
- 不把 Computer `view` 持久化为第二套真相（仍 SSE 投影）。
- 不做跨场景「万能选品字段」；`payload_json` 形状按 `artifact_type` 约定。
- **不迁生产数据**（近端开发库：删旧表 / 重建；无旧选品行迁移脚本）。

## 决策摘要

| 项 | 选择 |
|----|------|
| 存储 | 单表 `ebus_artifact` + JSON payload |
| 选品域 | 应用层保留 Parser / Service 校验 / DTO / Projector；删除 Picklist 实体表与专用 Repo |
| Listing | 同表，`artifact_type=sku`（对齐 ecommerce-skulist），后续故事 |
| 本地已有 picklist 数据 | **丢弃重建**（开发库） |
| Spine | AD-6 增 ArtifactStore；Picklist/Listing 改为逻辑形状所有者 |

## 表结构

```sql
CREATE TABLE ebus_artifact (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    biz_id          VARCHAR(36)  NOT NULL,  -- 对外 UUID = artifactRef
    user_id         VARCHAR(36)  NOT NULL,
    run_id          VARCHAR(36)  NOT NULL,  -- UNIQUE：一次 Run 一份成果
    artifact_type   VARCHAR(32)  NOT NULL,  -- picklist | sku
    scene_code      VARCHAR(64)  NOT NULL,  -- ecommerce …
    template_id     VARCHAR(64)  NULL,      -- 选品必填；sku 可空
    title           VARCHAR(256) NOT NULL,  -- 列表摘要
    payload_json    JSON         NOT NULL,  -- MySQL JSON；H2 可用 CLOB + 应用校验
    created_at      DATETIME(3)  NOT NULL,
    updated_at      DATETIME(3)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_ebus_artifact_biz (biz_id),
    UNIQUE KEY uk_ebus_artifact_run (run_id),
    KEY idx_ebus_artifact_user_time (user_id, created_at),
    KEY idx_ebus_artifact_user_type (user_id, artifact_type)
);
```

### `artifact_type = picklist` 的 payload 形状

```json
{
  "disclaimer": "…非实时…",
  "assumptions": "可选",
  "items": [
    {
      "title": "…",
      "priceBand": "…",
      "reason": "…",
      "differentiation": "…",
      "demand": "…",
      "competition": "…",
      "margin": "…",
      "risk": "…"
    }
  ]
}
```

- 条数 8–12、disclaimer 含「非实时」、字段非空等：**写入前**由现有 `PicklistApplicationService` / Parser 规则保证；表不拆 item 行。
- `template_id` 列冗余自 payload/命令，便于列表筛选；与 payload 一致。

### `artifact_type = sku`（预留，本轮不实现写入）

后续：上架素材（Listing 套装）文案 + `mediaObjectId[]` + 可选来源选品条目 id 进 payload；`title` 用商品名摘要。常量/枚举名用 `sku`，与 skill `ecommerce-skulist` 对齐；对外文案仍可叫「上架素材」。

## 代码边界

| 层 | 职责 |
|----|------|
| `domain/.../artifact` | `Artifact` 聚合、`ArtifactType`、`ArtifactRepository` |
| `infrastructure` | MyBatis PO/Mapper；替换 `PicklistRepositoryImpl` |
| `application/.../picklist` | 解析、可用性校验、组装 DTO、投影 `view`；`persistUsable` 改为调 ArtifactStore |
| `application/.../artifact`（薄） | 可选：`ArtifactApplicationService.save(type, payload)` |
| Agent 编排 | 不变：落库成功 → settle → `artifact_ready`（`artifactRef`） |

删除：`010_ebus_picklist.sql` 内容改为 `ebus_artifact`（或新 `011` + bootstrap 调整）、`Picklist`/`PicklistItem` 域模型与 Mapper、H2 中对应表。

## Spine 补丁（落地时写入 ARCHITECTURE-SPINE）

- AD-6 表增加：**ArtifactStore** — 唯一物理写入 `ebus_artifact`（及未来同族存储）。
- **PicklistArtifact** / **ListingArtifact**：定义各 type 的可用成果形状与校验规则；**不**各自持有专用表。
- AD-7 文字：选品/Listing「持久化」均指写入 ArtifactStore；语义（条数、templateId、mediaObjectId）不变。

## 迁移步骤（实现计划细化）

1. 新 SQL + H2；compose 开发库 `down -v` 或手工 `DROP` 旧表后 bootstrap。
2. Artifact 域 + Repo；PicklistApplicationService 改写。
3. 集成测 / `PicklistRepositoryIntegrationTest` → Artifact 等价测。
4. 更新 Spine + 本 spec 状态 → implemented。

## 风险

| 风险 | 缓解 |
|------|------|
| JSON 难做条目级 SQL 查询 | 近端历史只按 type/时间列表；详情读整包；可接受 |
| H2 `JSON` 类型差异 | H2 用 `CLOB`，应用层 Jackson 校验 |
| 误以为可跳过选品校验 | 强制：仅 `persistUsable` 路径写 picklist type |

## 验收

- [ ] 选品成功路径：预占 → 解析 → **写入 ebus_artifact** → settle → SSE 含 `artifactRef` + `view`
- [ ] 库中无 `ebus_picklist` / `ebus_picklist_item`
- [ ] 同 `run_id` 不可插入第二份成果（唯一约束）
- [ ] 相关单测 / starter 集成测绿
- [ ] Spine AD-6/7 已改表述
