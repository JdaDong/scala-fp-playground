# ✅ 偏函数 8 大场景学习 To-Do List

> 基于 `src/main/scala/demo/part04/` 下已创建的 8 个场景文件，整理成可勾选的学习清单。

---

## 📋 主任务清单

### 🎯 阶段一：基础场景（掌握核心 API）

- [ ] **Task 1 —— 订单状态机** 📄 `Scene01_OrderStateMachine.scala`
  - [ ] 运行文件，观察合法/非法状态转换输出
  - [ ] 理解 `isDefinedAt` 的作用
  - [ ] 动手添加新状态 `Refunded` 和事件 `Refund`
  - [ ] 思考：如果用 `if-else` 写，代码会膨胀多少？

- [ ] **Task 2 —— 日志分级管道** 📄 `Scene02_LogPipeline.scala`
  - [ ] 运行文件，对比生产 vs 开发环境的输出差异
  - [ ] 理解 `orElse` 的优先级语义
  - [ ] 动手添加 `TRACE` 级别处理器
  - [ ] 尝试把 `defaultHandler` 放到链的最前面，观察行为变化

---

### 🎯 阶段二：进阶场景（组合与解耦）

- [ ] **Task 3 —— HTTP 路由** 📄 `Scene03_HttpRouter.scala`
  - [ ] 运行文件，验证认证中间件的优先级
  - [ ] 动手添加 `productRoutes` 商品路由
  - [ ] 思考：为什么 `authMiddleware` 必须放最前？

- [ ] **Task 4 —— ETL 数据清洗** 📄 `Scene04_DataCleansing.scala`
  - [ ] 运行文件，对比"传统写法" vs "collect 写法"
  - [ ] 理解 `collect(pf)` = `filter + map` 的合体
  - [ ] 动手实现：从 `List[Any]` 中只抽取日期字符串

---

### 🎯 阶段三：高级场景（真实系统应用）

- [ ] **Task 5 —— Actor 消息处理** 📄 `Scene05_ActorMessageHandling.scala`
  - [ ] 运行文件，观察"正常模式 ↔ 维护模式"切换
  - [ ] 理解 `become(newReceive)` 的行为切换原理
  - [ ] 动手添加"降级模式"：只接收 Query，拒绝 Create

- [ ] **Task 6 —— 表单校验器** 📄 `Scene06_FormValidator.scala`
  - [ ] 运行文件，观察 4 个测试表单的错误收集
  - [ ] 理解 `lift` 把 `PartialFunction` → `Option` 的转换
  - [ ] 动手添加"密码必须包含特殊字符"校验规则

- [ ] **Task 7 —— 类 Spark 数据处理** 📄 `Scene07_SparkLikeProcessing.scala`
  - [ ] 运行文件，理解事件流的类型安全抽取
  - [ ] 动手实现：统计每个 server 的 Heartbeat 次数
  - [ ] 思考：`collect` vs `filter + map + cast` 有多少行差异？

- [ ] **Task 8 —— Future 异常恢复** 📄 `Scene08_FutureRecovery.scala`
  - [ ] 运行文件，观察"已知异常被捕获、未知异常继续抛出"
  - [ ] 对比文件末尾的"反面教材"，理解吞异常的危害
  - [ ] 动手添加：`UnauthorizedExceptionX` 的恢复策略（重新登录）

---

### 🎯 阶段四：整合与运行

- [ ] **Task 9 —— 一键运行全部场景** 📄 `ScenesRunner.scala`
  - [ ] 运行 `sbt "runMain demo.part04.ScenesRunner"`
  - [ ] 或在 IDEA 中右键 → Run 'ScenesRunner'
  - [ ] 确认 8 个场景都能跑通

---

## 🎓 补充学习任务

- [ ] **复习核心文档**
  - [ ] `partial_function.md` — 偏函数语法与核心 API
  - [ ] `scala_features_applications.md` — Scala 高级特性应用场景
  - [ ] `currying_vs_chaining.md` — 柯里化 vs 链式调用

- [ ] **核心 API 记忆卡片**
  - [ ] `isDefinedAt(x)` — 判断是否能处理
  - [ ] `orElse(pf2)` — 组合（前者未定义时尝试后者）
  - [ ] `andThen(f)` — 结果再加工
  - [ ] `lift` — `PartialFunction[A,B]` → `A => Option[B]`
  - [ ] `applyOrElse(x, default)` — 带兜底的执行
  - [ ] `collect(pf)` — 集合上的 filter + map 合体

- [ ] **延伸挑战**（可选）
  - [ ] 把 Scene 3 改造成真正的 Akka HTTP 服务
  - [ ] 把 Scene 5 替换成真正的 Akka Actor（引入 `akka-actor` 依赖）
  - [ ] 把 Scene 7 接入真实 Spark（引入 `spark-core` 依赖）

---

## 📊 进度追踪

```
阶段一: [ ][ ]              0/2  ░░░░░░░░░░░░░░░░░░░░
阶段二: [ ][ ]              0/2  ░░░░░░░░░░░░░░░░░░░░
阶段三: [ ][ ][ ][ ]        0/4  ░░░░░░░░░░░░░░░░░░░░
阶段四: [ ]                 0/1  ░░░░░░░░░░░░░░░░░░░░
补充学习:                   0/3  ░░░░░░░░░░░░░░░░░░░░
─────────────────────────────────────────────────────
总计:   0/12  ░░░░░░░░░░░░░░░░░░░░  0%
```

---

## 🗂️ 文件速查表

| # | 场景 | 文件 | 核心 API |
|---|------|------|----------|
| 1 | 订单状态机 | `Scene01_OrderStateMachine.scala` | `isDefinedAt` |
| 2 | 日志分级管道 | `Scene02_LogPipeline.scala` | `orElse` |
| 3 | HTTP 路由 | `Scene03_HttpRouter.scala` | `orElse` 链 |
| 4 | ETL 数据清洗 | `Scene04_DataCleansing.scala` | `collect` |
| 5 | Actor 消息 | `Scene05_ActorMessageHandling.scala` | `Receive` + `become` |
| 6 | 表单校验 | `Scene06_FormValidator.scala` | `lift` + `flatMap` |
| 7 | 类 Spark 处理 | `Scene07_SparkLikeProcessing.scala` | `collect(pf)` |
| 8 | Future 恢复 | `Scene08_FutureRecovery.scala` | `recover` |
| 9 | 统一入口 | `ScenesRunner.scala` | — |

---

> 💡 学习建议：按阶段顺序完成，每完成一个 Task 就把 `[ ]` 改为 `[x]`，边学边改边跑。
