# 选品 Skill：数据先行（推广池搜索）+ 标准 SKILL 模板改造

**Date:** 2026-09-27  
**Status:** draft（待用户审阅）  
**Decision:** 方案 A — Skill 定义流程；模型 `tool_call` 调商品搜索；再排名 → artifact → view  
**Related:**  
- [`2026-09-27-skill-dual-track-computer-contract-design.md`](./2026-09-27-skill-dual-track-computer-contract-design.md)  
- [`2026-09-26-official-pi-skills-adapter-design.md`](./2026-09-26-official-pi-skills-adapter-design.md)  
- 调研结论：淘宝客 / 多多客可作近端官方通道；闲鱼 / 生意参谋全站 / 无合同聚合 API 不做近端主路径  

## 1. Problem

当前 `ecommerce-picklist` 完全依赖大模型常识编清单，SKILL 明文「不做实时平台数据」。用户需要：

1. **数据先行**：按用户 query 调商品搜索 → 分析结果排名 → 业务 `artifact` → Computer `view`  
2. **Skill 按标准模板改造**：对齐官方 Agent Skills / Pi `SKILL.md` 形态，并挂上 Adam 双轨 `output` 元数据  

## 2. Goal

- 选品回合：**至少成功调用一次** `search_sku` 后再输出终态双轨 JSON。  
- 候选条目必须能对应工具返回的商品摘要（品名 / 价格带以抽样为准）。  
- **Computer 预览每条候选必须可跳到平台原链**：用户一点就能打开商品页（新标签、`https` only）。  
- `ecommerce-picklist/SKILL.md` 按 **§4 标准模板**重写；`allowed-tools` 含 `read_skill` + `search_sku`。  
- 积分仍 AD-5：可用 `artifact` 落库 + view 门禁后 settle；搜不到合格结果则 release。  

## 3. Non-goals

- 生意参谋全站行情、闲鱼开放、无合同聚合爬虫 API。  
- 应用层「模型先编清单再事后贴证据」（已否决）。  
- 近端解析并驱动 Run 管道的完整 YAML `output:`（可先写在 SKILL 里作合同；解析器可后续故事，管道近端仍可用 `SkillRunProfile` 注册表映射 `ecommerce-picklist` → billed picklist）。  
- 把 `search_sku` 做成前端直连。  
- 应用内嵌 iframe 打开商品页（只用外链跳转）。  

## 4. 标准 SKILL 模板（Adam 电商强制）

所有计费 / 场景 Skill（近端先改 picklist；skulist 跟进）必须按此骨架。依据：

- 官方 [Pi / Agent Skills](https://pi.dev/docs/latest/skills)：目录 + `SKILL.md`；frontmatter `name` / `description` / `allowed-tools`  
- 本仓官方适配：[`2026-09-26-official-pi-skills-adapter-design.md`](./2026-09-26-official-pi-skills-adapter-design.md)  
- 双轨输出：[`2026-09-27-skill-dual-track-computer-contract-design.md`](./2026-09-27-skill-dual-track-computer-contract-design.md) §2  

### 4.1 Frontmatter（必填 + Adam 扩展）

```yaml
---
name: ecommerce-picklist          # 与目录名一致；小写连字符
description: >-                   # 做什么 + 何时用（路由用，≤1024）
  国内电商选品清单：先搜索推广池商品，再排名并输出双轨 JSON。
  在用户要「选品 / 卖什么 / 候选清单」时使用。
allowed-tools: read_skill search_sku
metadata:
  output:
    billing: true
    persistAs: picklist
    requiresView: true
---
```

| 字段 | 规则 |
|------|------|
| `name` | = 父目录名；官方连字符 |
| `description` | 含能力 + 触发场景；禁止空泛「帮助选品」 |
| `allowed-tools` | 空格分隔；本 Skill 必须含 `search_sku` |
| `metadata.output.*` | Adam 扩展：计费 / 落库插件 / 是否必须 view（正文也须复述，不单靠解析器） |

### 4.2 正文固定章节（顺序锁定）

```markdown
# <Skill 显示标题>

## When to use
（何时加载；与 description 一致的人话）

## Workflow（必须按序）
1. 从用户 query 提炼搜索词（信息不足时写 assumptions，仍先搜）
2. 调用 search_sku（至少 1 次成功）
3. 基于返回结果筛选 / 排名（可再搜 1～2 次换词）；**只收录带有效 detailUrl 的条目**
4. 写出合格 artifact（含每条 sourceUrl）
5. 用同一事实编 view.blocks：list 每项填 href=sourceUrl（仅白名单组件）
6. 只输出一个双轨 JSON

## Tools
- search_sku：入参 / 出参摘要（必须含可点开的 detailUrl）；失败时怎么做

## Boundaries
- 做 / 不做；避开坑位；禁止未搜索就编完整清单
- 禁止编造或手写假链接；href/sourceUrl 必须来自工具返回

## Output contract
- artifact 字段表（含 sourceUrl）+ view 示例（list.item.href）+ disclaimer 必须点名「推广池抽样」
- 预览验收：每条候选可见「查看原商品」类跳转（或标题可点），新开页打开原链

## Failures
- 无结果 / 工具错误 → 人话说明，不编造全站蓝海；不输出假合格 artifact
- 工具结果缺 detailUrl → 该条不得进入 8–12；若合格条数不足则整单失败不 settle
```

### 4.3 改造验收（文档 / 评审用）

- [x] frontmatter 含 `name` / `description` / `allowed-tools` / `metadata.output`  
- [x] 正文含 §4.2 全部标题且 Workflow 含「先搜再排再 artifact 再 view」且强调原链  
- [x] 三份镜像同步：`starter` 主资源 + `application` / `pi-agent` test resources  
- [x] 预览 list 项均有 `href`（与 artifact `sourceUrl` 对齐）  

## 5. Runtime 形状（方案 A）

```text
reserve → prompt(skillId=ecommerce-picklist)
  → agent ⇄ tools（search_sku ≥1 次成功）
  → 终态 JSON { view, artifact }
  → Picklist persist 插件 → ComputerViewResolver → settle
  → artifact_ready + run_settled
```

| 组件 | 职责 |
|------|------|
| `SKILL.md` | 流程 SSOT；白名单工具 |
| `search_sku` Tool | 淘宝客（MVP）/ 可选多多客；只读；密钥环境变量；超时限流 |
| `PicklistArtifactPersistPlugin` | 仍校验 8–12 + 四维等；可增「来源声明」字段（可选后续） |
| `SkillRunProfile` | 近端仍 `billedPicklist()`；日后可由 `metadata.output` 驱动 |

**禁止模型：** 零次成功搜索就输出 8–12 条完整候选。  
**工具失败 / 空结果：** 终态不得假装可用成果；编排 release + `run_failed`（或模型输出不可用结构被校验拒绝）。

## 6. Tool 合同（MVP）

**Name:** `search_sku`

| 入参 | 说明 |
|------|------|
| `query` | 搜索词（必填） |
| `platform` | `taobao_tbk`（默认）\| `pdd_ddk`（可关） |
| `pageSize` | 默认小页（如 10），上限封顶 |

| 出参（摘要列表） | 说明 |
|------------------|------|
| `platform` | 来源标记 |
| `title` / `price` / `category` | 平台返回能给的字段 |
| `detailUrl` | **必填（MVP）**：可在浏览器打开的商品原链 / 推广落地链（`https`）；无此字段的条目不可入选 |
| `rawRef` | 可选平台侧 id，不进 Computer 业务类型 |

实现放 ebus application（或 infrastructure 适配器），经 pi-agent ToolCatalog **按名注册**；Skill `allowed-tools` 启用。遵守淘宝开放平台：不跨店聚合商家私域、不转售原始商家数据。

### 6.1 原链贯通（预览可跳转）

| 层 | 字段 | 规则 |
|----|------|------|
| Tool 出参 | `detailUrl` | 淘宝客/多多客返回的落地 URL；mock 也须给可解析的 `https` 假链 |
| `artifact.items[]` | `sourceUrl` | = 对应工具条目的 `detailUrl`；插件校验：8–12 条且**每条非空 https** |
| `view.blocks` list item | `href` | = 同条 `sourceUrl`；见双轨合同 `ListItem.href` |
| FE Computer | 外链 | 每条展示可点控件（标题可点或「查看原商品」）；`target=_blank` + `rel=noopener noreferrer`；非 `https:` 丢弃不渲染 |

**禁止：** 模型臆造 URL；把短链写成 markdown 纯文本却不落 `href`（FE 必须以 `href` 为准）。

## 7. Disclaimer / UX

Computer `note` 与 artifact disclaimer 统一口径，例如：

> 候选基于淘宝客/多多客推广池抽样检索与助手排序，非平台全站实时行情。点击可打开平台商品页核对。

## 8. Acceptance

- [x] 1. SKILL 符合 §4 模板；`allowed-tools` 含 `search_sku`。  
- [x] 2. 集成/单测：选品成功路径可观察到 ≥1 次 tool 调用（mock 即可）。  
- [x] 3. 无 tool / 空结果 → 不 settle。  
- [x] 4. 有 tool 结果 → 合格 artifact + view → settle；FE 仍只渲染 `view`。  
- [x] 5. **预览**：成功清单每条可点开原链（`href`/`sourceUrl` 对齐）；缺链条目不得 settle。  
- [x] 6. 密钥仅环境变量 / APP-META；前端零直连。（`search_sku` Mock 路径；淘宝客真客户端 Task 6 deferred。）  

## 9. 落地顺序（建议）

1. 定稿本设计（用户审阅）。  
2. 双轨合同补 `ListItem.href`；FE Renderer 渲染外链。  
3. 实现 `search_sku`（淘宝客 mock + 真客户端开关；出参含 `detailUrl`）。  
4. 按 §4 重写 `ecommerce-picklist` 三份 SKILL；artifact 增 `sourceUrl` 校验。  
5. 补 Agent / Skill 测；更新 dual-track 修订记录。  
6. （可选）多多客；`output` frontmatter 解析进 `SkillRunProfile`。  

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-09-27 | 初稿：数据先行 + 方案 A；标准 SKILL 模板强制改造 |
| 2026-09-27 | Tool 改名：`search_marketplace` → `search_sku` |
| 2026-09-27 | 预览必含原链跳转：`detailUrl` → `sourceUrl` → `view.href` |
| 2026-09-27 | 实现计划：[`../plans/2026-09-27-picklist-search-sku.md`](../plans/2026-09-27-picklist-search-sku.md) |
| 2026-09-27 | Tasks 1–5 已合入：§4.3 / §8 验收勾选；Task 6（TBK 真客户端）后置 follow-up |
