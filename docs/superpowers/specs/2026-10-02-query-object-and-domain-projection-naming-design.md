# Query 入参对象化 + domain 投影命名对齐

**Date:** 2026-10-02  
**Status:** accepted  
**Decision:**  
- 引入 `BaseQuery`（`userId` + `@SuperBuilder`），与写侧 `BaseCommand` 对称  
- 收参规则：≥2 业务参 **或** 带分页/过滤 → 必须 `*Query`；单参可读方法可暂留散参  
- Session / History QueryService 改为消费 `*Query`；limit/clamp 落在 Query 实例方法  
- domain `PiMessageDTO` → `PiMessage`（只读投影，去 DTO 后缀）；Application 出口仍用 `*DTO`  
- 同步收紧 `sdd/context/02-be.md`（及 coding-style 勾选）上述约定  

**Related:**  
- 编码规约：`sdd/context/02-be.md`、`sdd/context/01-coding-style.md`  
- 两段式回放（行为不变）：`docs/superpowers/specs/2026-10-02-session-turn-two-phase-query-design.md`  

---

## 1. Problem

1. QueryService 大量散参（`userId, scene, limit, nextToken…`），与规约「查询入参 `*PageQuery` / `*Query`」不符；clamp 堆在 Service 静态方法。  
2. domain 只读投影命名为 `PiMessageDTO`，与 Application 出口 DTO 混淆，也和同包 `PiLogicalRunRef` / `PiSessionMeta` 不一致。  
3. 全仓尚无 `*Query` 范本，后续域会继续漂。

## 2. Goals / Non-goals

**Goals**

1. 立住 `BaseQuery` + Session/History `*Query` 范本。  
2. clamp / 默认 pagesize 进入 Query，Service 变薄。  
3. `PiMessage` 命名对齐 domain 投影惯例。  
4. 文档勾选写清「何时必须 *Query」。  

**Non-goals**

- 不改两段式 SQL、ACL、窗口天数、HTTP 路径与响应 JSON 外形。  
- Credit / Scene / Identity **单参**读方法本切片不收 Query。  
- 不重命名 Application `SessionTurnDTO` 等出口 DTO。  
- 不引入读侧 `username` 进 `BaseQuery`。  

## 3. Architecture

```text
Controller
  → XxxQuery.builder().userId(SecuritySupport.requireUserId()).….build()
  → QueryService.method(query)
       → query.getUserId() / query.limit() / query.turnLimit()
       → Repository / 组装 Application *DTO
```

```text
common/BaseQuery
application/.../session/query/{SessionListQuery,SessionTurnPageQuery,SessionLatestArtifactQuery}
application/.../history/query/{HistoryListQuery,HistoryArtifactQuery}
domain/.../agent/model/PiMessage   ← was PiMessageDTO
```

## 4. BaseQuery

```java
@Getter
@SuperBuilder
@EqualsAndHashCode
public abstract class BaseQuery {
    /** 操作者 userId（JWT sub）；Controller 注入 */
    private final String userId;
}
```

- 包：`com.xmut.ebus.common.query`（或与 command 并列的 `common.command` 旁新建 `common.query`；推荐 **`com.xmut.ebus.common.query`**）。  
- Application 不读 SecurityContext。  
- 禁止 `new` + setter；一律 Builder。  

## 5. Session / History Query 清单

### 5.1 SessionListQuery

| 字段 | 说明 |
|------|------|
| `userId` | 继承 |
| `sceneCode` | 可选；空白 → null |
| `limit` | 可选 Integer |

- `limit()`：`null/≤0 → 50`，上限 `100`  
- Service：`list(SessionListQuery)`  

### 5.2 SessionTurnPageQuery

| 字段 | 说明 |
|------|------|
| `userId` | 继承 |
| `sessionId` | 必填（Service `requireHasText`） |
| `nextToken` | 可选；空白 → null |
| `limit` | 可选 Integer |

- `turnLimit()`：`null/≤0 → 20`，上限 `50`  
- Service：`pageTurns(SessionTurnPageQuery)`（替换 `getMessageList`）  
- HTTP 仍 `GET /sessions/{id}/messages`  

### 5.3 SessionLatestArtifactQuery

| 字段 | 说明 |
|------|------|
| `userId` | 继承 |
| `sessionId` | 必填 |
| `artifactType` | 可选 |

- Service：`getLatestArtifact(SessionLatestArtifactQuery)`（合并原重载）  
- HTTP 仍 `GET /sessions/{id}/latest-artifact`  

### 5.4 HistoryListQuery

| 字段 | 说明 |
|------|------|
| `userId` | 继承 |
| `sceneCode` | 可选 |

- Service：`list(HistoryListQuery)`  

### 5.5 HistoryArtifactQuery

| 字段 | 说明 |
|------|------|
| `userId` | 继承 |
| `artifactId` | 必填 |

- Service：`findById(HistoryArtifactQuery)`  

### 5.6 本切片不改

| 方法 | 原因 |
|------|------|
| `CreditQueryService.findByUserId` / `findUsage` | 单参 |
| `SceneQueryService.list` / `listSkillCapsules` | 无/单业务参 |
| `IdentityQueryService.findMe` / `findProfile` | 单参 |

## 6. clamp 归属

- 默认值与上限常量写在对应 Query 类（或同文件常量）。  
- `SessionQueryService` **删除** `clampLimit` / `clampTurnPage`（若测试依赖，改为测 Query 方法）。  
- Controller 示例：

```java
sessionQueryService.pageTurns(SessionTurnPageQuery.builder()
        .userId(SecuritySupport.requireUserId())
        .sessionId(sessionId)
        .nextToken(nextToken)
        .limit(limit)
        .build());
```

## 7. PiMessage 改名

| 项 | 规则 |
|----|------|
| 旧 | `com.xmut.ebus.domain.business.agent.model.PiMessageDTO` |
| 新 | `com.xmut.ebus.domain.business.agent.model.PiMessage` |
| 语义 | `pi_session_entry` 只读投影；非聚合根；非 HTTP DTO |
| 触点 | `PiSessionQueryRepository`、`PiSessionQueryRepositoryImpl`、`SessionTurnAssembler`、`SessionQueryService`、相关测试 |
| 保留 | Application `SessionMessageDTO` / `SessionTurnDTO` |

domain 内其它带 `DTO` 后缀的只读投影：当前仅此一处；本切片扫完即止。

## 8. 文档

更新 `sdd/context/02-be.md`（及 `01-coding-style.md` 勾选若需要）：

1. ≥2 业务参或分页/过滤 → `*Query extends BaseQuery`。  
2. `BaseQuery` 至少 `userId`；读路径不强制 `username`。  
3. limit/clamp 在 Query 实例方法，不在 QueryService 静态工具。  
4. domain 只读投影勿用 `*DTO` 后缀；Application/HTTP 出口用 `*DTO`。  
5. QueryService 方法名优先 `list` / `page*` / `find*` / `getLatestArtifact`。  

## 9. Test plan

| 场景 | 期望 |
|------|------|
| SessionListQuery.limit() | null→50；500→100 |
| SessionTurnPageQuery.turnLimit() | null→20；500→50 |
| Controller → pageTurns / list / getLatestArtifact | 组 Query，行为与现网一致 |
| History list/findById | 换 Query 后单测/集成绿 |
| PiMessage 改名 | 编译与 Assembler/Service 测绿 |
| 两段式回放回归 | SessionQueryServiceTest + Integration + FE 既有 turns 测 |

## 10. Implementation order（建议）

1. `BaseQuery`  
2. `PiMessageDTO` → `PiMessage`（机械改名）  
3. Session 三个 Query + Service/Controller/测试  
4. History 两个 Query + Service/Controller/测试  
5. 更新 `02-be` / coding-style 勾选  

可按 1–2、3、4–5 拆提交或拆 PR，设计一次定稿。  
