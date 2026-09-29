# 电商 Skill 上线门禁与打磨包（设计）

**Date:** 2026-09-29  
**Status:** accepted  
**Decision:**  
- 方法 = **方案 2**（Pack + 黄金用例手工打分；完整自动 Eval Harness 后置）  
- 范围 = **ABCD 全要**：协议稳、内容可用、路径体验、可量化门禁  
- 打磨双层 = **功能层（能跑对）+ 质量层（写得专业、产出像行家）**；质量层对齐业界 Skill 作者实践 + 国内电商选品/Listing 常识  
- 验收主路径 = **同会话串完**：选品 → 点候选硬交接 → Listing 策划 → `ask_human` → 执行稿  

**Related:**  
- Spine AD-16 SceneCapabilityPack（`sceneCode` → prompts/skills/tools）  
- 现网 Pack：`lippi-ai-ebus-starter/src/main/resources/scenes/ecommerce/`  
  - `ecommerce-picklist` / `ecommerce-skulist`  
- 反馈与历史：`2026-09-28-chat-card-feedback-actions-design.md`、Story 3.8  
- 业界参考（作者实践，非照搬 API）：[Anthropic Skill authoring best practices](https://platform.claude.com/docs/en/agents-and-tools/agent-skills/best-practices)、[Agent Skills 规范](https://agentskills.io/specification)、progressive disclosure / description 触发精度（社区与官方一致强调）  

---

## 1. Problem

Agent 基础能力（会话、SSE、积分、成果落库、HITL、反馈）已相对成型；电商场景差异主要在 Capability Pack。现有两份 skill 已有 Workflow / Verification，但距离「正式上线」仍缺：

1. **可量化门禁**（什么叫达标，何时允许标可上线）  
2. **同会话硬交接**（清单点候选 → Listing；今日多为外链 + 手打商品名）  
3. **内容可检查规则**（不只「写得好」，要能对照打分）  
4. **可重复放行流程**（固定用例 + scorecard，而非感觉）  
5. **Skill 本体工程质量**（结构、自由度、渐进披露、触发描述、好坏对照）— 功能勾上了，质量仍可能像「流程说明书」而非「行家操作手册」  

---

## 2. Goals / Non-goals

### Goals

1. 定义电商 Pack **上线门禁**（协议 P\*、路径 H\*、产出内容 B、Skill 文档质量 Q\*）。  
2. 规定黄金路径 **硬交接契约**（FE + skill + artifact 字段）。  
3. 列出 picklist / skulist **打磨包**：功能补丁 + **质量层改写标准**（对齐业界最佳实践）。  
4. 规定近端 **人工评测与放行**产物与通过线。  
5. 明确：打磨对象不仅是「别出错」，还包括 **选品判断力 / Listing 成交表达** 达到可对外售卖的专业度。  

### Non-goals

- CI 自动跑模型 / LLM-as-judge（方案 3，上线后加强）。  
- 真实生图、平台上架 API、新搜索 tool。  
- 一次点选批量给多条候选都出 Listing。  
- 改积分账本语义或跨场景万能助手。  
- 把 Anthropic/Cursor 的 Skill 运行时原样搬进本仓（只吸收**作者原则**，仍走 Pi `SKILL.md` + SceneCapabilityPack）。  

---

## 3. Approach

**方案 2 — Pack + 黄金用例手工打分（采用）**

相对「只改文案」：能覆盖交接与路径红线。  
相对「完整 Eval Harness」：近端可执行，不挡第一版上线。  
相对「只改 Verification 清单」：必须同时做 **Skill 作者质量升级**（见 §6.0），否则产出稳定但「像模板」。

实施顺序：

1. FE 硬交接 + picklist `items[].id`（否则黄金路径测不成）  
2. 按 §6.0 质量原则重组两份 `SKILL.md` / `references/`（协议硬约束保留，业务质量与对照例加深）  
3. 写入 eval 用例与 scorecard（含 Q\* 文档检 + B 产出打分），跑第一批  
4. 通过后写 `RELEASE.md` 标可上线  

---

## 4. 上线门禁表（D + A/B/C）

**放行原则：** 同一批固定用例上，**协议全过 + 黄金路径全通 + 内容分达标**，才标「电商 Pack 可上线」。任一红线失败 = 不可上线。

### 4.1 协议红线（A）— 机器可判，必须 100%

| 编号 | 检查点 | 通过标准 |
|------|--------|----------|
| P1 | 选品 tool | 成功路径恰好 1 次 `search_sku`；链接均来自 `detailUrl` |
| P2 | 选品结构 | `view`+`artifact` 合法；8–12 条；≥3 niche；1–2 条优先试；免责声明含字面量 `非实时平台全站行情`；每条非空 `id` |
| P3 | Listing 策划 | 先策划 JSON → 再 `ask_human`；未确认前无上架四字段 / `framePrompts` |
| P4 | Listing 执行 | 仅 `confirm_execute` 后出执行稿；`framePrompts.length = frames.length` |
| P5 | Fail 形态 | 该 Fail 时只一句人话，无假 JSON / 假链 |
| P6 | 计费节点 | 选品成功扣 1；Listing 策划可用成果与确认后执行扣分与现网一致、不双扣乱扣 |

### 4.2 路径红线（C）

固定黄金路径：

```text
用户要选品 → 出清单卡片
  → 点一条候选「做上架素材」（硬交接：标题+原链+itemId）
  → 出 Listing 策划 → ask_human
  → 确认出执行稿 → 执行 view/artifact 可预览
```

| 编号 | 检查点 | 通过标准 |
|------|--------|----------|
| H1 | 硬交接 | 点候选后发起的 Listing run **必带**商品名 + `sourceUrl`；`picklistItemId` 写入 artifact |
| H2 | 同会话 | 全程同一 `sessionId`，不要求用户重开场景 |
| H3 | 补充回路 | 「补充需求」只改策划并再次 `ask_human`，不跳进执行 |
| H4 | 断点可续 | 刷新/回会话后仍能从策划确认或继续聊（对齐现有 session 回放） |

### 4.3 产出内容门槛（B）— 人打分（业务质量）

每条成果四维 **1–5**（3=凑合可用、4=像懂行运营写的、5=可直接拿去测款/改图上架）。  
**主路径用例：平均 ≥ 3.5，且无单项 < 3。**  
放行叙事：**3 分不是「及格骄傲」，是底线；目标分布应以 4 为主。**

| 维度 | 选品（运营视角） | Listing（成交视角） |
|------|------------------|---------------------|
| 可行动 | 能决定先测哪 1–2 款、怎么测；优先试有「为何先测」 | 标题可检索；详情可粘贴改；分镜可交给美工/生图 |
| 差异化 | 角度/niche 不雷同；避开「大路货空话」 | 卖点不叠词；分镜机位不重复 |
| 可信 | 不编造全站指标；理由能回溯到价带/形态/痛点等可观察事实 | 不编造材质/资质/功效；未知写假设而非假参数 |
| 可读 | Computer 30 秒内能扫完并点优先试 | 策划一篇 markdown 好读；执行三 section 像店铺详情 |

**业务质量锚点（写入 skill 的「行家标准」，打分时对照）：**

- 选品：像「测款清单」不是「搜索结果复述」— 每条有痛点、切入角度、差异化、风险；优先试经得起「为什么不是隔壁那条」。  
- Listing：像「国内电商成交页草稿」不是「AI 作文」— 场景一句落地、卖点具体、主图有任务、禁鸡汤问答腔。  

### 4.4 Skill 文档质量门槛（Q\*）— 作者实践检

上线前对两份 `SKILL.md` + `references/` 做文档检（不跑模型也能判）。全部 Pass 才允许进产出打分轮。

| 编号 | 检查点 | 通过标准（对齐业界实践） |
|------|--------|--------------------------|
| Q1 | 触发描述 | `description` 同时含 **做什么 + 何时用 + 何时不用**；与姐妹 skill 互斥词清晰 |
| Q2 | 渐进披露 | `SKILL.md` 主体偏流程与原则；长 schema/示例在 `references/`，正文用相对路径按需引用 |
| Q3 | 自由度匹配 | 脆弱步骤（tool 次数、ask_human、JSON 门禁）**低自由度**写死；文案创意（角度措辞）**中高自由度**给原则+好坏例 |
| Q4 | 简洁 | 不解释模型已会的常识；无大段重复 Verification；主体保持可扫读（目标：主文件不膨胀成「百科」） |
| Q5 | 好坏对照 | 每 skill 至少 1 组 **好产出 vs 坏产出** 短例（选品条目级 / Listing 段落级），禁止只有抽象形容词 |
| Q6 | 可测 | Verification 条目可观察（是/否），避免「尽量专业」「高质量」无判据空话 |

### 4.5 样本量

- 主黄金路径用例 ≥ 5（含「信息不足靠假设」1 条）  
- 负向用例 ≥ 3  
- 通过线：Q\* 文档检全过 → 主+负向 **0** 条 P\*/H\* 失败 → 内容分仅对成功主路径计  

---

## 5. 黄金路径契约（硬交接）

### 5.1 用户动作

```text
Computer 选品 list
  → 每条候选主按钮「做上架素材」
     （【优先试】可强调；任意合格条可点）
  → 点击不跳外链；外链保留为次要「看原页」
  → 同 session 自动发起 ecommerce-skulist billed run
  → 策划 → ask_human → 确认 → 执行稿
```

### 5.2 交接载荷

| 字段 | 来源 | 必填 |
|------|------|------|
| 商品名 | list `title`（去掉展示用优先试前缀） | 是 |
| 商品原链 | list `href` / artifact `sourceUrl` | 是 |
| `picklistItemId` | artifact `items[].id` | 是（黄金路径） |
| niche / painPoint / angle | 对应 item 字段 | 建议 |
| `picklistArtifactId` | 当前选品成果 id | 建议（溯源） |

用户可见提示词形态（可微调）：

```text
请为商品「{title}」生成上架素材。
原链：{sourceUrl}
来源选品条目：{picklistItemId}
参考：{niche}；痛点：{painPoint}；角度：{angle}
```

Skill：有原链+品名时禁止丢掉交接商品；写入 `assumptions`；`picklistItemId` 在策划/执行 artifact 为黄金路径必填。

### 5.3 状态机

| 步 | 状态 | 下一步 |
|----|------|--------|
| S0 | 空 / 闲聊 | 发选品 |
| S1 | 选品成果在 Computer | 点候选做 Listing；重试选品；或口述 Listing（非黄金路径） |
| S2 | 策划 + 等待 ask_human | 确认执行 / 补充需求 |
| S3 | 补充后新策划 | 同 S2 |
| S4 | 执行成果 | 重试 Listing；赞/踩；可再点其他候选开新 Listing run |

硬规则：S1→S2 不得要求新会话；未 `confirm_execute` 出现执行字段 = H3/P3 失败；点候选 run 必须 `skillId=ecommerce-skulist` 且 text 含标题+原链。

---

## 6. Skill 打磨包（功能 + 质量）

打磨分两层，缺一不可：

| 层 | 解决什么 | 主要手段 |
|----|----------|----------|
| **功能层** | 协议/路径/字段/tool 次数 | Verification、schema、FE 交接、Fail 模板 |
| **质量层** | 像行家、可对外售卖的产出 | 作者结构（Q\*）+ 领域原则 + 好坏对照 + 评分锚点 |

只做功能层 → 稳定但「像模板」。只堆华丽文案 → 协议仍炸。本设计要求两层同轮交付。

### 6.0 质量层作者原则（业界最佳实践 → 本仓落地）

吸收 Anthropic / Agent Skills 社区共识，映射到 Pi Scene Pack（不换运行时）：

1. **Concise / 上下文是公共品**  
   - 删「什么是选品」「什么是 JSON」类常识段。  
   - 重复规则只保留一处权威（Verification 或 Boundaries），另一处改引用。  

2. **Progressive disclosure（渐进披露）**  
   - `SKILL.md`：Overview → When to use / not → Workflow → Tool 要点 → Verification / Failures / Boundaries。  
   - 长 schema、完整 JSON 示例、好坏对照长文 → `references/`（已有 `output.md`；质量向可增 `quality.md` 或并入 output 的「好/坏」小节）。  
   - 正文用相对路径点名：`见 [output.md](references/output.md)#…`，按需读取。  

3. **Degrees of freedom（自由度匹配脆弱度）**  
   - **低自由度（写死）：** `search_sku` 次数、真链、条数门槛、ask_human 参数、未确认禁止执行字段、免责字面量。  
   - **中自由度（模板+可改）：** 提示词交接句式、策划 markdown 小标题结构。  
   - **高自由度（原则+例）：** 痛点措辞、分镜创意、详情段落文风——用「好/坏例」约束，不背诵固定句子。  

4. **Description 触发精度**  
   - 重写两 skill 的 `description`：能力 + 触发词 + **互斥**（选品 ↔ Listing），降低抢跑。  

5. **Examples over adjectives**  
   - 禁止只写「专业、高转化、有洞察」。  
   - 每个关键字段族至少一对 **好 vs 坏**（各 1～3 行）。  

6. **Skill TDD 心态（放行前做一次）**  
   - 先跑 1～2 条用例看模型在**无新质量段**时的典型偷懒（套话、凑数、鸡汤）。  
   - 再针对这些偷懒写规则与坏例（而不是先写长文再猜）。  
   - 改完重跑同一用例，确认偷懒路径被堵住。  

7. **能代码判的不靠散文**  
   - 条数、字段存在、链接协议、tool 次数 → 继续靠应用层/Verification 硬门禁。  
   - Skill 正文留给「判断与文风」——模型真正需要教的部分。  

### 6.1 文件清单

| 资产 | 功能层 | 质量层 |
|------|--------|--------|
| `ecommerce-picklist/SKILL.md` | item id；Verification；Fail 模板 | 重组结构；description；选品判断原则；指向好坏例 |
| `ecommerce-picklist/references/output.md` | `items[].id` schema/示例 | 条目级好/坏对照；评分条写法锚点 |
| `ecommerce-skulist/SKILL.md` | Handoff；`picklistItemId` 路径必填 | 成交文案原则；分镜/Prompt 质量；description |
| `ecommerce-skulist/references/output.md` | 交接字段示例 | 策划/详情/framePrompt 好/坏对照 |
| （可选）`references/quality.md` | — | 若 output 过长则拆出质量专章（保持一层深） |
| （可选）场景 pack 说明 | 引导点候选 | — |

### 6.2 picklist — 功能补丁

- 保持：1 次 `search_sku`、真链、8–12、≥3 niche、1–2 优先试、免责字面量。  
- 新增：每条非空 `id`（推荐 `pl-1`…），与 `view.list` 顺序对应。  
- Fail 文案固定短模板。  

### 6.3 picklist — 质量补丁（业务最佳实践）

写入 Skill / references 的**可教原则**（打分 B 时对照）：

| 主题 | 要求 | 坏例（禁止） |
|------|------|--------------|
| 角色 | 你是帮卖家做**测款筛选**的助手，不是搜索框复读机 | 只改写工具标题+价 |
| 优先试 | 必须答清「为何先测它 vs 清单里下一条」 | 「市场需求大」「性价比高」 |
| niche | 具体到使用场景/人群 | 「日用」「家居」三连 |
| 痛点/角度/差异 | 各一句可验证或可感知的表述 | 三句同义反复 |
| 评分条 | `高｜/中｜/低｜` + 挂钩本条可观察事实 | 「高｜刚需」无事实 |
| 风控默认 | 未点名则避开重货/强季节/高退货尺码服/假认证等（沿用现条款并举例） | 为凑 8 条塞违禁功效款 |
| 反凑数 | 同质微差最多 1 条代表；不够则 Fail | 同款三色充数 |

### 6.4 skulist — 功能补丁

- 保持：策划 → `ask_human` → 仅确认后执行；`templateId` 固定；无 `platformCopies`。  
- Handoff：解析标题/原链/`picklistItemId`/可选参考 → `assumptions` + 必填 `picklistItemId`；禁止丢掉交接商品名。  

### 6.5 skulist — 质量补丁（业务最佳实践）

| 主题 | 要求 | 坏例（禁止） |
|------|------|--------------|
| 角色 | 国内电商**成交素材**编辑；界面换壳不换文案套 | 留学文书/心灵鸡汤腔 |
| driver | 谁 + 场景 + 为什么买（一句） | 「提升生活品质」 |
| frames | 每条=画面任务（主体/场景/卖点之一），机位不重复 | 「展示产品」「突出卖点」×3 |
| detailTitle | 品类词 + 2～4 个可检索卖点词 | 纯情绪词堆砌 |
| detailBody | 场景落地 → 卖点/感受 → 可知规格；短段 | 连续提问开场；编造参数 |
| displayNotes | 主图顺序与禁区（短、可执行） | 「注意美观」 |
| framePrompts | 与 frames 逐条对齐；含主体+场景+约束 | 「8k，最佳质量，杰作」 |

### 6.6 边界（路由）

| 信号 | 走 |
|------|-----|
| 选品/测款/候选清单 | `ecommerce-picklist` |
| 点候选或明确上架/主图/详情 | `ecommerce-skulist` |
| 同轮又要清单又要 Listing | 先清单；Listing 等点候选或第二轮 |

### 6.7 打磨节奏（质量向）

```text
1) 基线：现网 skill 跑 2 条主路径，记录典型「套话/凑数/鸡汤」
2) Q* 重组：description + 结构 + references 好坏例（先文档检）
3) 功能补丁：id / handoff / Verification
4) 全量 E2E + B 打分；不及格只改质量规则或对照例，避免无限加长 SKILL.md
5) 若主文件过长 → 把质量长文下沉 references/quality.md（Q2）
```

---

## 7. 评测与放行

### 7.1 产物

| 路径 | 用途 |
|------|------|
| `docs/superpowers/evals/ecommerce-launch/cases.md` | 用例脚本 |
| `docs/superpowers/evals/ecommerce-launch/scorecard.md` | 跑批打分表 |
| `docs/superpowers/evals/ecommerce-launch/RELEASE.md` | 放行记录 |

近端人工按脚本执行；不做 CI 自动评模型。

### 7.2 用例（最小集）

主路径：E2E-01～05（明确客单价、信息不足、非厨房日用、点非优先试条、补充后再确认）。  
负向：NEG-01 空搜/不足 8 Fail；NEG-02 未确认就要终稿；NEG-03 意图路由。

主路径验收 **禁止**用手打商品名绕过 H1。

### 7.3 放行线

**可上线（按序全过）：**

1. **Q\*** 两 skill 文档检全 Pass  
2. **P\*/H\*** 主+负向零失败  
3. **B** 主路径均分 ≥ 3.5 且无维 < 3（目标以 4 分为主，不只踩线）  
4. E2E-01～05 均到执行可预览  
5. `RELEASE.md` 记模型/日期/执行人，并附「本批典型坏例已写入 references」勾选  

**不可上线：** Q\* 未过；假链/假 JSON/跳过 ask_human；交接丢标题或原链；有维 < 3；SKILL 只有空泛形容词无好坏对照。  

**P/H/Q 红线不可豁免。** 非红线内容瑕疵若需例外，须在 `RELEASE.md` 写豁免项 + 补测日。

### 7.4 不及格闭环

```text
Q* 失败 → 先改 skill 结构/description/对照例，再跑模型
P/H 红线 → 改 Skill/output/FE 交接 → 重跑失败用例
仅 B 分低 → 加严领域原则或补好坏例（优先 references），禁止把 SKILL.md 堆成百科
同用例连续两轮仍红线 → 查 tool/结算/路由，不只堆 prompt
```

上线后：每周抽「质量差」反馈约 10 条，对照同一 scorecard；**优先沉淀为新的坏例**，再改规则。

---

## 8. Error handling / 边界

- 选品工具失败或合格条 < 8：人话 Fail，不扣成功成果语义下的「假成功」。  
- 交接缺原链或 item id：FE 不应发出该 Listing run；若发出，skill 应 Fail 或明确要求补全（实现计划里二选一写死，默认 **FE 拦截**）。  
- 积分不足：走现网不足提示，不进入半截黄金路径充数。  

---

## 9. Testing

- FE：点候选组装 text / skillId / 同 session 的单测或组件测。  
- Skill：以 eval 用例 + scorecard 为准（近端人工）；现有 integration 测继续守 SSE/落库/结算形状。  
- 放行前必须跑完整 E2E-01～05 + NEG-01～03。  

---

## 10. Open points（实现计划前拍板）

1. `items[].id` 格式：`pl-1` 顺序号 vs 短 hash（推荐顺序号，简单可测）。  
2. 交接缺字段时：仅 FE 拦截 vs skill 再防一层（推荐 FE 主拦 + skill Verification 双保险）。  
3. 「做上架素材」按钮文案最终用词（与现有胶囊「生成上架素材」对齐即可）。  
4. 质量对照例放 `output.md` 小节 vs 独立 `references/quality.md`（推荐：先并入 output；超过可扫读长度再拆）。  

---

## Spec self-review

- 无 TBD/TODO 占位；Open points 已收成可选项。  
- 门禁含 P/H/B/**Q**；打磨包含功能层 + 质量层；与放行顺序一致。  
- 主路径均为同会话硬交接；业界实践已映射为可执行的 Q\* / 6.0，而非空泛「参考最佳实践」。  
- 范围单包可做一版 implementation plan；自动 Eval 明确后置。  
- 「可选」字段在黄金路径下的必填语义已写清（`picklistItemId`、`items[].id`）。  
