# 设计：通用 `@CasRetry` 乐观锁重试

日期：2026-09-24  
状态：已批准并实现（见 plans/2026-09-24-cas-retry-annotation.md）  
范围：`forma-common` 基础设施 + CreditLedger 首个接入方

## 1. 背景与目标

CreditLedger 的预占 / 结算 / 释放 / 懒月重置使用乐观锁（`version` 条件更新），当前用 `MAX_CAS_RETRIES` + `for` 手写重试，可读性差且无法复用。

目标：

- 在 **common** 提供通用、基于注解的 CAS 重试能力
- 业务方法在冲突时抛 `CasConflictException`；切面负责重试与耗尽转译
- Credit 接入后删除手写 `for`，**计费语义不变**（含 settle/release 先 claim hold）

非目标：

- 不引入 Spring Retry
- 不按「返回值 0」隐式重试
- 不把整段 `settle`/`release` 标成可重试（hold 已 claim 后不可整方法重放）
- 不引入 `REQUIRES_NEW`；重试仍在**同一外层事务**内重读 + 再 update

## 2. 决策摘要

| 项 | 选择 |
|----|------|
| 作用域 | 全仓通用（放 common） |
| 机制 | 自研 `@CasRetry` + AOP |
| 冲突信号 | 抛 `CasConflictException` |
| 结构 | 注解/异常/切面/自动配置在 common；Credit 将「单次 CAS 尝试」拆到独立 Bean |
| 默认次数 | `maxAttempts = 8`（与现网一致） |

## 3. common API

### 3.1 `@CasRetry`

```java
@Target(METHOD)
@Retention(RUNTIME)
public @interface CasRetry {
    int maxAttempts() default 8;
    String exhaustedMessage() default "操作冲突，请稍后重试";
    ErrorCode exhaustedErrorCode() default ErrorCode.SYSTEM_ERROR;
}
```

### 3.2 `CasConflictException`

- 包：`com.xmut.ebus.common.cas`
- 仅表示「乐观锁/条件更新未命中，可再试」
- **不是**业务失败：`CREDIT_INSUFFICIENT`、`CREDIT_HOLD_INVALID` 等仍直接抛 `BusinessException`，切面**不重试**

### 3.3 `CasRetryAspect`

行为：

1. 调用目标方法
2. 若抛出 `CasConflictException` 且未达 `maxAttempts` → 再次调用
3. 若达上限仍冲突 → `BusinessException(exhaustedErrorCode, exhaustedMessage)`
4. 其它异常原样抛出（含 `BusinessException`）
5. 无退避（`backoff`）：与当前 for 循环一致，立即重试；本版不加 sleep

约束：

- `@CasRetry` 方法必须经 **Spring 代理**调用（独立 Bean）；禁止同类 `this.xxx()` 自调用
- 与 `@Transactional` 共存时：事务边界仍在 ApplicationService 外层；CAS 尝试方法**不必**再开事务

### 3.4 自动配置

- `common` 增加依赖：`spring-boot-starter-aop`（Boot 2.7 / Java 8）
- `CasRetryAutoConfiguration` 注册 `CasRetryAspect`
- Boot 2.7：`META-INF/spring.factories`  
  `org.springframework.boot.autoconfigure.EnableAutoConfiguration=\ …CasRetryAutoConfiguration`
- starter 引入 common 后无需业务侧手工 `@Enable…`

## 4. Credit 接入

### 4.1 新 Bean：`CreditCasWriter`（application）

| 方法 | `@CasRetry` 耗尽文案 | 职责 |
|------|---------------------|------|
| `reserveAttempt(userId)` | 积分预占冲突，请稍后重试 | ensureReady → 不足则业务异常 → `updateAddReserved`；0 行抛 CasConflict；成功建 hold 返回 holdId |
| `settleAccountAttempt(hold, now)` | 积分结算冲突，请稍后重试 | 读账户 → `updateSubtractBalanceAndReserved`；0 行抛 CasConflict |
| `releaseAccountAttempt(hold, now)` | 积分释放冲突，请稍后重试 | 读账户 → `updateSubtractReserved`；0 行抛 CasConflict |
| `monthlyResetAttempt(accountId, now)` | 月重置冲突，请稍后重试 | 读账户 → 无需重置则返回；否则 apply + `updateBalanceAndNextReset`；0 行抛 CasConflict |

### 4.2 `CreditApplicationService` 编排（保持语义）

- `reserveOne` → 调 `casOps.reserveAttempt`
- `settle` / `release`：**先** `updateStatusIfActive` claim（只一次）；成功后再调对应 `*AccountAttempt`
- `ensureReady` → 懒月重置走 `monthlyResetAttempt`
- 删除 `MAX_CAS_RETRIES` 与全部 CAS `for` 循环
- 外层 `@Transactional` 不变

### 4.3 危险路径（明确禁止）

在已 claim 的 `settle`/`release` 整方法上标 `@CasRetry` —— 重试会二次 claim 失败，导致账户永远扣不到。

## 5. 测试

**common**

- 冲突 N-1 次后成功
- 耗尽 → `BusinessException` + 配置的 message/code
- 方法内直接 `BusinessException` → 不重试、立即失败

**Credit（回归）**

- 现有 `CreditApplicationServiceTest` / 集成测保持绿
- `reserveCasExhaustion` 等行为与文案对齐新耗尽路径

## 6. 文件清单（预期）

```
forma-common/
  pom.xml                                          (+ spring-boot-starter-aop)
  …/common/cas/CasRetry.java
  …/common/cas/CasConflictException.java
  …/common/cas/CasRetryAspect.java
  …/common/cas/CasRetryAutoConfiguration.java
  src/main/resources/META-INF/spring.factories
  src/test/…/cas/CasRetryAspectTest.java

forma-application/
  …/credit/service/CreditCasWriter.java         (新建)
  …/credit/service/CreditApplicationService.java   (改编排)
  …/credit/service/CreditApplicationServiceTest.java
```

## 7. 风险与缓解

| 风险 | 缓解 |
|------|------|
| 自调用导致注解不生效 | 独立 `CreditCasWriter` Bean；文档/02-be 可补一句规范 |
| common 引入 Spring AOP 变重 | 可接受：本仓已是 Spring Boot 单体；注解枚举引用已有 `ErrorCode` |
| 切面顺序与事务代理 | CAS 方法不加 `@Transactional`；事务只在 ApplicationService |

## 8. 验收标准

- [ ] 无手写 `MAX_CAS_RETRIES` for 循环
- [ ] `@CasRetry` 可在非 Credit 服务复用（仅依赖 common）
- [ ] 注册建账 / 预占结算释放 / 懒月重置语义与现测一致
- [ ] 相关单测 + Credit 集成测绿
