# Cats Effect 入门：从 Future 到 IO

> 配套代码：`src/main/scala/demo/part06/`
>
> 依赖：`"org.typelevel" %% "cats-effect" % "3.5.4"`（已加入 `build.sbt`）

---

## 🎯 全文导航

| 章节 | 主题 | 配套文件 |
|---|---|---|
| Part 1 | Future vs IO 的本质差异 | `Scene01_FutureVsIO.scala` |
| Part 2 | Cancellation：真正可取消的并发 | `Scene02_Cancellation.scala` |
| Part 3 | Resource Safety：资源即使异常也能正确释放 | `Scene03_ResourceSafety.scala` |
| Part 4 | 综合实战：限流 + 重试 + 资源回收 | `Scene04_RateLimitedCrawler.scala` |

---

## Part 1 · Future vs IO 的本质差异

### 1.1 一句话总结

| | `scala.concurrent.Future` | `cats.effect.IO` |
|---|---|---|
| 性质 | **已启动的任务**（eager） | **任务的描述**（lazy） |
| 定义即执行？ | ✅ 定义那一刻就在跑 | ❌ 不 `unsafeRun` 或 `IOApp.run` 不会跑 |
| 引用透明？ | ❌ `val x = Future(...)` 与原表达式不等价 | ✅ 完全等价 |
| 可重试？ | ❌（已经执行完了） | ✅ 任意次 |
| 可取消？ | ❌（只能"忽略结果"） | ✅ 真取消 |
| 错误丢失风险 | ⚠️ `onComplete` 没装好就丢了 | ✅ 错误是值（`attempt`/`handleErrorWith`） |
| 线程模型 | 显式 `ExecutionContext` | 由 `IORuntime` 内部管理（含计算池/阻塞池/调度池） |

### 1.2 "定义即执行"的陷阱

```scala
// Future 版：副作用只发生 1 次（即使你"用"了 2 次）
val printHello: Future[Unit] = Future { println("hello!") }   // ← 已经打印了
for {
  _ <- printHello
  _ <- printHello   // ← 不会再打印
} yield ()

// IO 版：副作用真的发生 2 次
val printHello: IO[Unit] = IO.println("hello!")               // ← 还没打印
for {
  _ <- printHello
  _ <- printHello   // ← 真的会执行第 2 次
} yield ()
```

> **核心心法**：`IO[A]` 是 *"未来执行后会得到 A、并可能产生副作用的程序"*，不是 *"正在执行的任务"*。

### 1.3 引用透明性

```scala
val tick: IO[Unit] = IO.println("tick")
val planA = tick *> tick *> tick
val planB = IO.println("tick") *> IO.println("tick") *> IO.println("tick")
// planA 和 planB 行为完全相同 → IO 是值
```

这条性质看似学术，但在大型代码库里收益巨大：
- **可重构** —— 任意把表达式抽成 `val`，行为不变
- **可测试** —— 不必担心"测试运行 IO 时已经发生过副作用"
- **可推理** —— 像数学公式一样代换

### 1.4 错误是值

```scala
val boom: IO[Int] = IO.raiseError(new RuntimeException("💥"))

boom.attempt                     // IO[Either[Throwable, Int]]
boom.handleErrorWith(e => ...)   // 类似 try-catch
boom.recover { case e => -1 }    // 同 Future.recover
```

错误不会"漏掉"——只要没显式 `attempt` 或 `handleErrorWith`，错误就一直是 `IO` 的一部分。

---

## Part 2 · Cancellation：真正可取消的并发

### 2.1 五个核心 API

| API | 作用 | 何时取消 |
|---|---|---|
| `io.start` | 启动一个 Fiber，立刻返回 `Fiber[IO, Throwable, A]` | 手动 `fiber.cancel` |
| `io.timeout(d)` | 限时执行 | 超时自动取消 + 抛 `TimeoutException` |
| `IO.race(a, b)` | 并行执行，谁先完成谁赢 | 输者自动取消 |
| `(a, b, c).parTupled` | 并行执行 | 任一失败 → 其他自动取消 |
| `IO.uncancelable { ... }` | 关键区不可取消 | （保护内部不被取消） |

### 2.2 取消是怎么"发生"的？

> Cats Effect 的取消是**协作式**的：不是 `Thread.interrupt` 那种暴力中断，
> 而是在每个**挂起点（cede point）**检查取消信号。

挂起点包括：
- `IO.sleep` / `IO.async` / `IO.cede`
- `*>` 序列连接处
- `flatMap` / `map` 边界

```scala
def loop(n: Int = 0): IO[Unit] =
  IO.println(s"tick #$n") *>
  IO.sleep(200.millis) *>     // ← 这里可以被取消
  loop(n + 1)
```

如果你写的是**纯计算 loop**（无 sleep / 无 cede），它就不可取消——因为没有挂起点。
此时可以手动插入 `IO.cede`：

```scala
def heavyCompute(n: Int): IO[Int] =
  if (n == 0) IO.pure(0)
  else        IO.cede *> heavyCompute(n - 1).map(_ + 1)
```

### 2.3 `race` 的典型场景

```scala
// 主备双查：哪个先返回用哪个
val result = IO.race(primaryDB.query, replicaDB.query)

// 给任意 IO 加超时（不用 timeout 也能写）
val withTimeout = IO.race(io, IO.sleep(1.second) *> IO.raiseError(new TimeoutException))
```

### 2.4 `uncancelable`：保护关键区

```scala
def transfer(from: Account, to: Account, amount: Money): IO[Unit] =
  IO.uncancelable { _ =>
    debit(from, amount) *> credit(to, amount)
    // 这两步是"原子"的，即使外面 cancel 也会跑完
  }
```

> 即使父任务被取消，`uncancelable` 块也会继续执行直到结束。

---

## Part 3 · Resource Safety：资源即使异常也能正确释放

### 3.1 为什么需要 `Resource`？

| 方案 | 问题 |
|---|---|
| `try-finally` | 不能组合，多资源就是嵌套地狱 |
| `scala.util.Using` | 只解决同步资源，对 `IO` 无效 |
| 手写 `IO` 的 `bracket` | 写起来繁琐，容易忘记某个释放路径 |
| **`Resource[IO, A]`** | ✅ 可组合 ✅ 自动释放 ✅ 三态都安全（成功/失败/取消） |

### 3.2 构造 Resource 的 3 种方式

```scala
// 1) make：手动 acquire / release
Resource.make(acquire = openConn)(release = _.close)

// 2) fromAutoCloseable：直接对接 Java AutoCloseable
Resource.fromAutoCloseable(IO(new FileInputStream("x.txt")))

// 3) eval / pure：把已有 IO 包成 Resource（无需释放）
Resource.eval(IO.pure("token"))
```

### 3.3 组合：释放顺序自动逆序

```scala
val combined: Resource[IO, (DB, HttpClient, Logger)] =
  for {
    db     <- dbResource
    http   <- httpResource
    logger <- loggerResource
  } yield (db, http, logger)

// acquire 顺序：db → http → logger
// release 顺序：logger → http → db   （栈式，自动正确）
```

### 3.4 三态都安全

```scala
resource.use { r => doSomething(r) }
//   ✅ doSomething 正常返回 → release
//   ✅ doSomething 抛异常   → release 仍然执行
//   ✅ 外部被 cancel        → release 仍然执行
```

> **这是 `try-finally` 永远做不到的事**：`finally` 不知道什么是"取消"。

---

## Part 4 · 综合实战：限流 + 重试 + 资源回收

### 4.1 业务需求

并发抓取 N 个 URL，要求：
1. 全局并发不超过 3
2. 每个请求最多重试 3 次
3. 每个请求超时 1 秒
4. 实时统计成功/失败数（线程安全）
5. HTTP 客户端不论成功/失败/取消都要释放

### 4.2 关键工具一览

| 工具 | 作用 | 标准库对照 |
|---|---|---|
| `Semaphore[IO](n)` | 并发限流 | `java.util.concurrent.Semaphore` |
| `Ref[IO, A]` | 线程安全可变状态 | `AtomicReference` |
| `Queue[IO, A]` | 异步队列 | `BlockingQueue` |
| `Deferred[IO, A]` | 单次写入的"Promise" | `CompletableFuture` |
| `Resource[IO, A]` | 资源生命周期 | try-with-resources |

### 4.3 把 5 个能力组合起来

```scala
def fetchOne(client, url, sem, stats): IO[Unit] =
  sem.permit.use { _ =>                              // ① 限流
    val once = client.fetch(url).timeout(1.second)   // ② 超时
    retry(once, maxAttempts = 3, delay = 200.millis) // ③ 重试
      .attempt
      .flatTap(...)                                  // ④ 日志
      .flatMap(r => stats.update(_ + r))             // ⑤ 计数
  }

def crawl(urls): IO[Stats] =
  httpClient.use { client =>                          // ⑥ Resource
    for {
      sem   <- Semaphore[IO](3)
      stats <- Ref.of[IO, Stats](Stats(0, 0))
      _     <- urls.parTraverse_(fetchOne(client, _, sem, stats))
      r     <- stats.get
    } yield r
  }
```

### 4.4 这就是 Cats Effect 的"日常工作量"

每个能力都不是自己造的轮子，而是：
- 选一个标准组合子（`Semaphore` / `Ref` / `parTraverse_`）
- 用 `flatMap` 把它们粘起来
- 用 `Resource` 兜底所有"释放"逻辑

代码读起来就像**业务规约**，而不是**多线程实现细节**。

---

## 🚀 运行方式

```bash
sbt "runMain demo.part06.Scene01_FutureVsIO"
sbt "runMain demo.part06.Scene02_Cancellation"
sbt "runMain demo.part06.Scene03_ResourceSafety"
sbt "runMain demo.part06.Scene04_RateLimitedCrawler"
```

第一次运行会下载 cats-effect 依赖（约 5MB）。

---

## 🧠 核心心法（背下来）

1. **`IO[A]` 是值，不是任务** —— 它描述"如果运行将做什么"，引用透明。
2. **副作用要显式** —— 任何 `println` / `Random` / `System.currentTimeMillis` 都该包进 `IO`。
3. **`Resource` > `try-finally`** —— 唯一能正确处理"取消"的资源管理工具。
4. **取消是协作式的** —— 在挂起点检查信号，长循环要插 `IO.cede`。
5. **错误是值** —— `attempt` 把它变 `Either`，像普通数据一样处理。
6. **优先用组合子，不要 `unsafeRunSync`** —— 整个 `main` 应该只有一个 `IOApp.run`。
7. **`IOApp` 帮你管 Runtime** —— 不要手动 `IORuntime.global`，除非测试。

---

## 🔗 进阶方向

- **fs2**：基于 IO 的 Stream，处理无限/反压数据流
- **http4s**：基于 IO 的 HTTP 服务/客户端
- **doobie**：基于 IO 的 JDBC 函数式封装
- **circe**：JSON ADT + type class 编解码（和 Cats 同一作者）
- **skunk**：Postgres 原生协议客户端（不走 JDBC）

它们组合起来就是 **Typelevel Stack** —— Scala 函数式生态的事实标准。
