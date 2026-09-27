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

- 选品回合：**至少成功调用一次** `search_marketplace` 后再输出终态双轨 JSON。  
- 候选条目必须能对应工具返回的商品摘要（品名 / 价格带以抽样为准）。  
- `ecommerce-picklist/SKILL.md` 按 **§4 标准模板**重写；`allowed-tools` 含 `read_skill` + `search_marketplace`。  
- 积分仍 AD-5：可用 `artifact` 落库 + view 门禁后 settle；搜不到合格结果则 release。  

## 3. Non-goals

- 生意参谋全站行情、闲鱼开放、无合同聚合爬虫 API。  
- 应用层「模型先编清单再事后贴证据」（已否决）。  
- 近端解析并驱动 Run 管道的完整 YAML `output:`（可先写在 SKILL 里作合同；解析器可后续故事，管道近端仍可用 `SkillRunProfile` 注册表映射 `ecommerce-picklist` → billed picklist）。  
- 把 `search_marketplace` 做成前端直连。  

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
allowed-tools: read_skill search_marketplace
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
| `allowed-tools` | 空格分隔；本 Skill 必须含 `search_marketplace` |
| `metadata.output.*` | Adam 扩展：计费 / 落库插件 / 是否必须 view（正文也须复述，不单靠解析器） |

### 4.2 正文固定章节（顺序锁定）

```markdown
# <Skill 显示标题>

## When to use
（何时加载；与 description 一致的人话）

## Workflow（必须按序）
1. 从用户 query 提炼搜索词（信息不足时写 assumptions，仍先搜）
2. 调用 search_marketplace（至少 1 次成功）
3. 基于返回结果筛选 / 排名（可再搜 1～2 次换词）
4. 写出合格 artifact（插件校验）
5. 用同一事实编 view.blocks（仅白名单组件）
6. 只输出一个双轨 JSON

## Tools
- search_marketplace：入参 / 出参摘要；失败时怎么做

## Boundaries
- 做 / 不做；避开坑位；禁止未搜索就编完整清单

## Output contract
- artifact 字段表 + view 示例要点 + disclaimer 必须点名「推广池抽样」

## Failures
- 无结果 / 工具错误 → 人话说明，不编造全站蓝海；不输出假合格 artifact
```

### 4.3 改造验收（文档 / 评审用）

- [ ] frontmatter 含 `name` / `description` / `allowed-tools` / `metadata.output`  
- [ ] 正文含 §4.2 全部标题且 Workflow 含「先搜再排再 artifact 再 view」  
- [ ] 三份镜像同步：`starter` 主资源 + `application` / `pi-agent` test resources  

## 5. Runtime 形状（方案 A）

```text
reserve → prompt(skillId=ecommerce-picklist)
  → agent ⇄ tools（search_marketplace ≥1 次成功）
  → 终态 JSON { view, artifact }
  → Picklist persist 插件 → ComputerViewResolver → settle
  → artifact_ready + run_settled
```

| 组件 | 职责 |
|------|------|
| `SKILL.md` | 流程 SSOT；白名单工具 |
| `search_marketplace` Tool | 淘宝客（MVP）/ 可选多多客；只读；密钥环境变量；超时限流 |
| `PicklistArtifactPersistPlugin` | 仍校验 8–12 + 四维等；可增「来源声明」字段（可选后续） |
| `SkillRunProfile` | 近端仍 `billedPicklist()`；日后可由 `metadata.output` 驱动 |

**禁止模型：** 零次成功搜索就输出 8–12 条完整候选。  
**工具失败 / 空结果：** 终态不得假装可用成果；编排 release + `run_failed`（或模型输出不可用结构被校验拒绝）。

## 6. Tool 合同（MVP）

**Name:** `search_marketplace`

| 入参 | 说明 |
|------|------|
| `query` | 搜索词（必填） |
| `platform` | `taobao_tbk`（默认）\| `pdd_ddk`（可关） |
| `pageSize` | 默认小页（如 10），上限封顶 |

| 出参（摘要列表） | 说明 |
|------------------|------|
| `platform` | 来源标记 |
| `title` / `price` / `category` | 平台返回能给的字段 |
| `rawRef` | 可选平台侧 id，不进 Computer 业务类型 |

实现放 ebus application（或 infrastructure 适配器），经 pi-agent ToolCatalog **按名注册**；Skill `allowed-tools` 启用。遵守淘宝开放平台：不跨店聚合商家私域、不转售原始商家数据。

## 7. Disclaimer / UX

Computer `note` 与 artifact disclaimer 统一口径，例如：

> 候选基于淘宝客/多多客推广池抽样检索与助手排序，非平台全站实时行情。

## 8. Acceptance

1. SKILL 符合 §4 模板；`allowed-tools` 含 `search_marketplace`。  
2. 集成/单测：选品成功路径可观察到 ≥1 次 tool 调用（mock 即可）。  
3. 无 tool / 空结果 → 不 settle。  
4. 有 tool 结果 → 合格 artifact + view → settle；FE 仍只渲染 `view`。  
5. 密钥仅环境变量 / APP-META；前端零直连。  

## 9. 落地顺序（建议）

1. 定稿本设计（用户审阅）。  
2. 实现 `search_marketplace`（淘宝客 mock + 真客户端开关）。  
3. 按 §4 重写 `ecommerce-picklist` 三份 SKILL。  
4. 补 Agent / Skill 测；更新 dual-track 修订记录。  
5. （可选）多多客；`output` frontmatter 解析进 `SkillRunProfile`。  

## 修订记录

| 日期 | 说明 |
|------|------|
| 2026-09-27 | 初稿：数据先行 + 方案 A；标准 SKILL 模板强制改造 |
