# Context · Coding style（索引）

范本域：**选品 `picklist`**。把文中 `picklist` / `Picklist` / `picklists` 换成你的域 slug。

## 按需打开（减加载）

| 你在改… | 只读 |
|---------|------|
| Java / Controller / Command / 包结构 | [`02-be.md`](./02-be.md) |
| Vue / api / types / 路由 | [`03-fe.md`](./03-fe.md) |
| 分支 / 校验 / 可读性（早返回等） | [`04-quality.md`](./04-quality.md) |
| 两端一起的新功能 | 对齐勾选 + 按端开 02/03；写逻辑时加 04 |

## 30 秒链路

```text
*Request → Controller（JWT → userId）→ ApplicationService（写）/ QueryService（读）→ ApiResponse
FE views → api/<区>/*.ts（仅 HTTP）→ types/<区> → HTTP
编辑：路由 id → get*；catch → handleError
计费生成：SSE（fetch+ReadableStream + JWT）→ 事件名见 Spine AD-4
```

## 前后端对齐（做完勾）

```
[ ] …domain…/<域>  ↔  FE …/<域>  ↔  /api/v1/<复数>
[ ] 业务键同名 camelCase（如 picklistId）
[ ] 写：FE 动词 ↔ ApplicationService
[ ] 读：FE get* ↔ QueryService find*/page/get*
[ ] 查询类 *PageQuery；勿 Get*Command
[ ] Application 不自己抠 SecurityContext；userId 由 Controller 注入
[ ] api 无 export type；types 为类型唯一出处
[ ] FE 状态数值 === BE *Status（若有）
```

## 优雅写法 7 条（细节见 [`04-quality.md`](./04-quality.md)）

1. **主路径要直**：卫语句先挡非法；真正做事的那段浅缩进。
2. **一层一事**：Controller 组命令；App 写；Query 读；FE `api` 只 HTTP、`types` 只形状。
3. **名字说身份**：查询 `*PageQuery`；布尔 `canEdit` / `isOn`；缺参抛错用 `require*`。
4. **失败带代号**：业务失败 `BusinessException` + `ErrorCode`；FE `handleError`。
5. **显式优于聪明**：userId 在入口注入。
6. **真相在服务端**：编辑靠路由 id + `get*`，勿靠 `history.state`。
7. **注释写为什么**：非显然意图一行即可；禁复述步骤。

**守卫方法命名**

| 前缀 | 语义 | 例 |
|------|------|-----|
| `require*` | 必须具备，否则 throw | `requireUserId` |
| `ensure*` | 确保已达某状态，否则补齐或 throw | `ensureLoaded` |
| `check*` | Validator / 参数体检 | `checkTemplateId` |
| `can*` / `is*` | 只返布尔，不抛 | `canRetry()` |

少用 `assert*`（易与单测混淆）。
