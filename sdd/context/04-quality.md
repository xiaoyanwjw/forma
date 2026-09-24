# Context · 控制流与可读性

**何时读**：写分支、校验、handler 逻辑时（与命名片分开加载）。  
**索引**：[`01-coding-style.md`](./01-coding-style.md) · 后端 [`02-be.md`](./02-be.md) · 前端 [`03-fe.md`](./03-fe.md)

---

## 1. 卫语句优先（核心）

多条件时：**先挡非法/早退，再写主路径**。主路径保持浅缩进、从上到下读。

### Good

```java
if (userId == null) {
    throw new BusinessException(ErrorCode.UNAUTHORIZED);
}
if (!creditLedger.hasAvailable(userId, 1)) {
    throw new BusinessException(ErrorCode.CREDIT_INSUFFICIENT);
}
doGenerate(userId);
```

```typescript
if (!row) return
if (submitting.value) return
await doSubmit(row)
```

### Bad

```java
if (userId != null) {
    if (creditLedger.hasAvailable(userId, 1)) {
        doGenerate(userId);
    }
}
```

### 规则

| MUST | FORBIDDEN |
|------|-----------|
| 前置条件用卫语句（`return` / `throw`）摊平 | 为「对称」硬写 `else` 包住主逻辑 |
| 业务失败抛 `BusinessException`；**新建 throw 带 `ErrorCode`**（ErrorCode 落地后） | 深层 `if { if { if {`（一般 ≤2 层；更深先抽方法） |
| 布尔意图用域方法：`canRetry()` / `isSettled()` | 长表达式堆在 `if` 里不命名 |

---

## 2. 分支形态选用

| 场景 | 用法 |
|------|------|
| 前置校验 / 空值 | 卫语句，无 `else` |
| 枚举多路 | `switch`；勿长 `else if` 链复制业务 |
| 循环内跳过非法行 | `if (bad) continue;` |
| 可选副作用 | `if (flag) { side(); }` 后继续；勿包整段主流程 |

**禁止**：`if (x) return y; else return z;` → 写成卫语句 + 最后 `return z`。

---

## 3. 方法形状（Happy path 最后）

```text
1) 参数/用户/权限校验 → throw 或 return
2) 加载聚合 / 查库
3) 业务守卫 → throw
4) 主变更
5) 事件 / 返回
```

- 一个方法只做一件事；卫语句变多就 **抽 private 方法**。
- 命名：`require*` / `ensure*` / `check*` / `can*` · `is*`；**少用** `assert*`。
- **写 API**：校验失败 **抛**。
- **读/映射**：「无需处理」可 **return**；早退宜打日志说明 why。
- **大 ApplicationService**：允许按 `// ── module ──` 块维护。

---

## 4. 空值与集合

| Good | Bad |
|------|-----|
| `StringUtils.hasText(s)` | 散落 `trim().isEmpty()` |
| 空集合：`orElse(Collections.emptyList())` | NPE 后才发现 |
| FE：`if (!token) return` | 假设一定有值 |

---

## 5. 其它质量习惯（短）

| 项 | MUST | FORBIDDEN |
|----|------|-----------|
| 魔法值 | 命名常量 / 枚举 | 裸数字当状态 |
| 副作用 | 变更集中在一处 | 在 getter 里改状态 |
| 异常 | 业务失败抛 `BusinessException` | 吞异常空 `catch` |
| 嵌套回调 | FE async/await | Promise 金字塔 |
| 注释 | 只写「为什么」 | 复述步骤、「✅ DDD」废话 |

---

## 6. 自检（改完逻辑勾）

```
[ ] 主路径缩进浅（卫语句在上）
[ ] 无超过 2 层的业务 if 嵌套（或已抽方法）
[ ] 无「整段主逻辑包在 else」
[ ] 本 PR 新增业务 throw 带 ErrorCode（模块落地后）；无 silent 假 success
[ ] 复杂条件已变成 canX / isY
[ ] FE 哨兵/步骤非裸数字
```
