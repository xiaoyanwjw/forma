---
name: tech-competitor
description: >-
  粘贴一个公网产品 URL（或正文），按 Urlcomp 风格产出分层竞品拆解（JSON：view + artifact）。
  在用户提到竞品分析、拆解官网、产品雷达时使用。
allowed-tools: read_skill ingest_competitor write_file read_file render_view
metadata:
  billing: true
  persistAs: tech_competitor
  requiresView: true
  output: view.json
---

# 竞品分析

帮用户**拆解一个**公网产品：一次 `ingest_competitor`（抓取或粘贴 → 摘句）→ 按固定分层写可审计报告（Found / 推断 / 未公开）。没有可用正文就不要写盘、不要编造。

## When to use

- **用：** 竞品分析、拆解产品官网、产品雷达、贴了一个产品 http(s) 链接或粘贴介绍正文
- **不用：** 科技文章/文档一页摘要 → 科技前沿「链接速读」（`tech-digest`）
- **不用：** 产品早报 / 多条热点扫描（未开放）
- **不用：** 用户要两家硬比对照表 → 说明本 Skill 一次拆一家，可再跑一轮第二个 URL；不要一次拉多链
- **不用：** 选品 / Listing / 小红书 → 对应场景

## Workflow

1. **判断原文从哪来，并调用一次 `ingest_competitor`。**
   - **已有粘贴正文**（足够长的可见正文）：传 `paste`（可选再传 `url` 写入 `sourceUrl`）。`artifact.source` = 工具返回的 **`paste`**。**不要**再为抓取单独调工具。
   - **只有 http(s) 链接、没有可用粘贴：** 传 `url`。成功则 `source` = **`fetch`**，`sourceUrl` 优先工具返回；正文在工作区（默认 `source.md`）。失败或太短 → Failures，**禁止**编造。
   - **既无链接也无粘贴：** Fail。

2. **禁止二次 ingest。** 同一轮 **`ingest_competitor` 恰好 1 次**；不并行、不自动跟 pricing 第二页。

3. **只用返回的 `excerpts`。** 写各层时**只使用**工具返回的 `excerpts` 与可选 `concern`，**禁止**把 `source.md` 全文灌进总结。

4. **构造领域实体。** 按 [output.md](references/output.md) 拼完整 **artifact**（分层 + 三态）。`rivals` **默认 `inferred`**（除非页上明文列举对手）。禁止估算 MRR/CAC；无公开价 → `pricingSignal` / `packaging` 用 `not_public`。`write_file` → `artifact.json`。

5. **渲染视图。** 调用 **`render_view`**（默认 `artifact.json` → `view.json`）。勿手写 HTML。近端**无**手递按钮。

6. **成功标准。** 盘上已有 reminder 中的 **output**（通常 `view.json`）。不要在对话里输出 `{"output":...}` 或整包 JSON。

7. **过 Verification** 再结束。

## Tool: ingest_competitor

一次完成抓取|粘贴 → 切块摘句。成功 JSON 含 `source`（`fetch`|`paste`）、`sourceUrl?`、`sourcePath`、非空 `excerpts`。

| 参数 | 说明 |
|------|------|
| `url` | 单个产品公网首页；已有粘贴可不传 |
| `paste` | 用户粘贴正文；提供则不抓取 |
| `sourcePath` | 默认 `source.md` |

失败或太短：禁止编造；走 Failures。本轮恰好 1 次。

## Quality

- **三态诚实：** found 必须能对 quotes；inferred 必有 reason；没有就 not_public
- **单品拆解：** 不要求用户交第二个链接；同类对手在 `rivals` 层
- **concern：** 用户「我关心…」写入 `concern`，并影响 whyPay / packaging 取舍

## Output

成功终态：盘上 reminder 指定的 **output**。字段与示例 → [output.md](references/output.md)。

## Verification

- [ ] 有可用原文（粘贴或一次成功 ingest），且 `excerpts` 非空
- [ ] 本轮 `ingest_competitor` **恰好 1 次**；`excerpts` 来自该次返回
- [ ] 各层 `status` 仅为 found / inferred / not_public；found 可对照 quotes
- [ ] rivals 默认 inferred（或页上列举才 found）；未写估算 MRR/CAC
- [ ] 已写 `artifact.json` 并成功 `render_view` → `view.json`
- [ ] 未输出 `{"output":...}`；view 含请对照原文核实；无手递按钮
- [ ] 成功路径无跑题闲聊

## Failures

只回一句人话：

- 没给链接也没粘贴
- 拉页失败/太短且无粘贴 → 请换公开链接或粘贴正文
- 要两家硬比 → 说明一次拆一家，可再跑第二个 URL
- 完全离题 → 拉回本 Skill

## Boundaries

- 禁止无原文空写、禁止假 found
- 不二次 / 并行 ingest；不代登、不收 Cookie、不解析 PDF
- 不做日监控、不写销售 Battlecard
