# Scala 函数式生态最后 4 块拼图

> 配套代码：`src/main/scala/demo/part08..11/`
>
> 这 4 个主题分别从"业务架构"、"DSL 设计"、"流处理"、"类型系统"4 个维度，
> 完成 Scala 函数式生态最深的 4 个抽象。

---

## 🗺️ 总览

| Part | 主题 | 抽象层级 | 工业代表 |
|---|---|---|---|
| **part08** | Tagless Final | 业务架构 | http4s / doobie / skunk |
| **part09** | Free Monad | DSL 设计 | doobie / cats-mtl |
| **part10** | fs2 Stream | 流处理 | Kafka 消费者 / 爬虫 / 网关 |
| **part11** | HKT + Variance | 类型系统 | cats / scala 标准库 |

---

# Part 1 · Tagless Final（part08）

## 1.1 痛点：业务和"效果类型"耦合

```scala
class UserService(repo: UserRepoIO) {
  def greet(id: Long): IO[String] = ...   // ← 永远绑死 IO
}
```

切换 IO ↔ ZIO ↔ Future 需要把所有方法签名改一遍；测试时也必须跑真实 IO。

## 1.2 Tagless Final 三步曲

```scala
// Step 1: Algebra（能力契约）
trait UserRepo[F[_]] {
  def findById(id: Long): F[Option[User]]
  def save(u: User): F[Unit]
}

// Step 2: Service 完全多态
class UserService[F[_]: Monad](repo: UserRepo[F]) {
  def greet(id: Long): F[String] = for {
    opt <- repo.findById(id)
  } yield opt.map(u => s"Hello, ${u.name}!").getOrElse("Stranger")
}

// Step 3: 在 main 里装配
object Main extends IOApp.Simple {
  override def run = {
    val svc = UserService[IO](UserRepoIOInterpreter)
    svc.greet(1).flatMap(IO.println)
  }
}
```

## 1.3 超能力：同一业务，N 种解释器（Scene02 实测）

| F | 用途 |
|---|---|
| `IO`     | 生产环境真实运行 |
| `Id`     | 同步纯函数，单元测试直接断言 |
| `State`  | 内存 DB 模拟，可断言"状态变化"|
| `Writer` | 自动收集所有调用日志（trace）|

★ **业务代码 1 行不改**，只换 `F`，测试和生产共用业务代码且互不污染。

## 1.4 工业实战：约束栈（Scene03）

```scala
class BankService[F[_]](using
  M: MonadError[F, Throwable],
  repo: AccountRepo[F],
  log: Logger[F],
  clk: Clock[F]
) {
  def transfer(from: AccountId, to: AccountId, amount: BigDecimal): F[Unit] = ...
}
```

> **方法签名上叠加多个 type class 约束** = "我需要这些能力"。
> 调用方在 main 里拼装 instance 即可。这就是 http4s / doobie 的 API 模式。

## 1.5 Tagless Final 的判断要点

- 你的代码签名里出现 `IO[X]`、`Future[X]`？ → 考虑改成 `F[X]`
- 想做单元测试但跑 IO 太麻烦？ → 用 Id Monad
- 想给同一业务加 trace / metrics？ → 加一个 `Logger[F]` 约束

---

# Part 2 · Free Monad（part09）

## 2.1 核心思想：把"程序"建模成数据

```scala
sealed trait KVStoreA[A]
case class Put(key: String, value: String) extends KVStoreA[Unit]
case class Get(key: String)                extends KVStoreA[Option[String]]
case class Delete(key: String)             extends KVStoreA[Unit]

type KVStore[A] = Free[KVStoreA, A]
```

业务代码（**只是构造 AST**，没执行任何东西）：

```scala
val program: KVStore[Option[String]] = for {
  _      <- put("user:1", "Alice")
  _      <- put("user:2", "Bob")
  _      <- update("user:1", _ + "-Senior")
  _      <- delete("user:2")
  result <- get("user:1")
} yield result
```

## 2.2 Interpreter（用 `~>`）

```scala
val realInterpreter: KVStoreA ~> Id = new (KVStoreA ~> Id) {
  def apply[A](fa: KVStoreA[A]): Id[A] = fa match {
    case Put(k, v) => storage.update(k, v)
    case Get(k)    => storage.get(k)
    case Delete(k) => storage.remove(k); ()
  }
}

program.foldMap(realInterpreter)   // 执行
```

同一段 program 还可以：
- 用 `dryRunInterpreter` 只看会做什么，不真跑（部署前预览）
- 用 `analyzer` 静态扫描 AST 结构（统计/优化）

## 2.3 多 Algebra 组合（Scene02）

```scala
type App[A] = EitherK[CacheA, LogA, A]   // Coproduct
type Prog[A] = Free[App, A]

class CacheOps[F[_]](using I: InjectK[CacheA, F]) { ... }
class LogOps  [F[_]](using I: InjectK[LogA,   F]) { ... }
```

业务里同时使用 Cache + Log，写起来就像在一个 Monad 里。

## 2.4 Free vs Tagless Final

| 维度 | Tagless Final | Free Monad |
|---|---|---|
| 抽象方式 | 通过 type class | 通过 ADT |
| 业务代码 | `F[_]: Monad` | `Free[F, A]` |
| 编译期/运行期 | 编译期解决，零开销 | 运行期 AST，有间接调用 |
| 静态分析 | 难 | ✅ 容易（program 是数据） |
| 性能 | 通常更好 | 略差 |
| 适用场景 | 大多数后端 | 需要 batch/优化的 DSL（如 SQL） |

---

# Part 3 · fs2 Stream（part10）

## 3.1 Stream 的本质

```
Stream[F, A] = "产生 A 的、副作用是 F 的、惰性的可组合序列"
```

| | List | Iterator | fs2 Stream |
|---|---|---|---|
| 惰性 | ❌ | ✅ | ✅ |
| 可表达无限流 | ❌ | ✅ | ✅ |
| 元素带 effect | ❌ | ❌ | ✅ |
| 反压 / 并发 | ❌ | ❌ | ✅ |
| 资源安全 | ❌ | ❌ | ✅（与 cats-effect 集成）|

## 3.2 基础（Scene01）

```scala
Stream(1, 2, 3, 4, 5)                                    // 静态
Stream.range(1, 11)                                      // 范围
Stream.iterate(1)(_ + 1)                                 // ★ 无限流（List 做不到）
Stream.iterate(1)(_ + 1).take(5).compile.toList          // → [1,2,3,4,5]
```

收尾方式：
- `.compile.toList` —— 收集成 List
- `.compile.count`  —— 计数
- `.compile.foldMonoid` —— Monoid 折叠
- `.compile.drain` —— 只跑副作用，不收集结果

## 3.3 反压：4 种"快慢匹配"模式（Scene02 实测）

| 模式 | 关键 API | 实测耗时 |
|---|---|---|
| 串行 | `evalMap` | 832 ms |
| 并行 | `parEvalMap(4)` | **439 ms** ← 缩短一半 |
| 缓冲 | `buffer(N)` | 平滑处理 |
| 限流 | `metered(200.millis)` | 严格按速率发 |

> ★ 不需要 BlockingQueue / Semaphore / synchronized——
> fs2 自动反压。

## 3.4 Pipe 流水线（Scene03）

```scala
type Pipe[F, A, B] = Stream[F, A] => Stream[F, B]

val parse:        Pipe[IO, String, LogLine]   = ...
val onlyAlerts:   Pipe[IO, LogLine, LogLine]  = ...
val stampTime:    Pipe[IO, LogLine, LogLine]  = ...
val metrics:      Pipe[IO, LogLine, Metrics]  = ...

rawLogs
  .through(parse)
  .through(onlyAlerts)
  .through(stampTime)
  .through(metrics)
```

每个 Pipe 是独立、可测试、可复用的乐高积木 —— 这就是 Spark Dataset / Akka Stream graph 的"小型工业版"。

## 3.5 实战：事件总线（Scene04）

```
3 producers → Queue (bounded 32) → 4 consumers
                                  ↘ statsReporter（每秒打印吞吐）

Stream.emits(producers ++ consumers ++ List(reporter))
  .parJoinUnbounded
  .interruptAfter(3.seconds)
  .compile.drain
```

实测：3 秒内 4 个 consumer 处理 74 条事件，自动反压、自动取消、资源安全释放。

---

# Part 4 · HKT + Variance（part11）

## 4.1 Kind 阶梯

```
*           = 具体类型           (Int, String)
* → *       = 一参类型构造器      (List, Option, Future)
* → * → *   = 二参类型构造器      (Either, Map)
(* → *) → * = ★ 高阶类型构造器    (如 Free)
```

普通泛型 `def f[A]`：对"任意值类型"通用。
HKT `def f[F[_], A]`：对"任意装值的容器"通用 —— 这是 cats 整个生态的地基。

## 4.2 HKT 基础（Scene01）

```scala
trait Container[F[_]] {
  def headOr[A](fa: F[A], default: A): A
  def map[A, B](fa: F[A])(f: A => B): F[B]
}

given Container[List]   with { ... }
given Container[Option] with { ... }
given Container[Vector] with { ... }

// ★ 一个签名通吃所有容器
def firstUpper[F[_]: Container](fa: F[String]): F[String] = ...

firstUpper(List("hello", "world"))  // List(HELLO, WORLD)
firstUpper(Option("hi"))             // Some(HI)
firstUpper(Vector("a", "b"))         // Vector(A, B)
```

Either（2 参数）想用？用 type alias 部分应用：

```scala
type StrOr[A] = Either[String, A]
firstUpper(Right("scala"): StrOr[String])  // Right(SCALA)
```

## 4.3 Variance（Scene02）

| 标注 | 名称 | 何时用 | 经典例子 |
|---|---|---|---|
| `+A` | 协变 | 只读容器（A 在产出位置）| `List[+A]`, `Option[+A]` |
| `-A` | 逆变 | 只消费容器（A 在消费位置）| `Function1[-A, +R]`, `Comparator[-A]` |
| `A` | 不变 | 读写都做 | `Array[A]`, 可变集合 |

PECS（Producer-Extends, Consumer-Super）法则的语言级体现：

```scala
val cats: List[Cat]       = ...
val animals: List[Animal] = cats           // ✅ 协变

val animalPrinter: Printer[Animal] = ...
val catPrinter:    Printer[Cat]    = animalPrinter   // ✅ 逆变

// Function1 经典
val f: Animal => Persian = (_: Animal) => Persian()
val g: Cat => Cat        = f               // ✅ 输入逆变、输出协变
```

## 4.4 自然变换 `F ~> G`（Scene03）

```scala
trait NaturalTransform[F[_], G[_]] {
  def apply[A](fa: F[A]): G[A]
}
type ~>[F[_], G[_]] = NaturalTransform[F, G]

val listToOption: List ~> Option = new (List ~> Option) {
  def apply[A](fa: List[A]): Option[A] = fa.headOption
}
```

这就是 Tagless Final 和 Free Monad 里 **interpreter 的本质**。

## 4.5 Phantom Type：把状态写到类型里（Scene03）

```scala
sealed trait Raw          // 编译期标签，运行时不存在
sealed trait Validated

case class FormData[S] private[Phantom] (email: String, age: Int)

def validate(d: FormData[Raw]): Either[String, FormData[Validated]] = ...
def save(d: FormData[Validated]): Unit = ...

// save(rawData)  ← ❌ 编译期就拒绝
validate(rawData) match {
  case Right(v) => save(v)             // ✅
  case Left(e)  => println(e)
}
```

> **运行时零开销**（type-erased），但编译器替你巡检。

## 4.6 类型化 ID（opaque type + phantom tag）

```scala
opaque type Id[Tag] = Long
extension [Tag](id: Id[Tag]) def value: Long = id

sealed trait UserTag
sealed trait OrderTag

type UserId  = Id[UserTag]
type OrderId = Id[OrderTag]

def loadUser(id: UserId): String = ...
// loadUser(orderId)  ← ❌ 编译失败

```

UserId 和 OrderId 在 JVM 上**就是 Long**（零拆装箱开销），但编译期严格区分。

---

# 🚀 运行方式

```bash
# part08: Tagless Final
sbt "runMain demo.part08.Scene01_FromConcreteToTagless"
sbt "runMain demo.part08.Scene02_MultipleInterpreters"
sbt "runMain demo.part08.Scene03_TaglessIndustrial"

# part09: Free Monad
sbt "runMain demo.part09.Scene01_FreeMonadIntro"
sbt "runMain demo.part09.Scene02_FreeAdvanced"

# part10: fs2 Stream
sbt "runMain demo.part10.Scene01_FS2Intro"
sbt "runMain demo.part10.Scene02_FS2Backpressure"
sbt "runMain demo.part10.Scene03_FS2Pipeline"
sbt "runMain demo.part10.Scene04_FS2EventBus"

# part11: HKT + Variance
sbt "runMain demo.part11.Scene01_HKTBasics"
sbt "runMain demo.part11.Scene02_Variance"
sbt "runMain demo.part11.Scene03_HKTAdvanced"
```

---

# 🧠 心法总结

## 关于"抽象"的全景

```
   值                   值的容器                  程序
   ─                    ─────                     ──
泛型 [A]              HKT [F[_]]            Free Monad / Tagless Final
   │                    │                          │
   ▼                    ▼                          ▼
 普通方法             cats Functor               业务和效果解耦
 通用                  Monad 等                  "程序也是数据"
```

## 选择决策树

| 你想做的事 | 用什么 |
|---|---|
| 业务代码与 IO 解耦，还想要测试好写 | **Tagless Final** |
| 想把"程序"序列化、分析、优化 | **Free Monad** |
| 处理大量事件流、需要反压/限流 | **fs2 Stream** |
| 写库 API、需要严格的类型契约 | **HKT + Variance + Phantom** |

## 7 句话记一辈子

1. **Tagless Final**：把 `IO` 改成 `F[_]: Monad`，业务和效果立刻解耦。
2. **Multiple Interpreters**：测试用 `Id`，模拟用 `State`，trace 用 `Writer`，生产用 `IO`。
3. **Free Monad**：业务代码先变 AST，再选 interpreter，能"分析"也能"运行"。
4. **fs2 Stream**：自动反压，组合子表达"快慢匹配"，根本不写线程同步代码。
5. **Pipe**：让流处理像 Unix pipe 一样可拼装。
6. **Variance**：`+A` 产出位置，`-A` 消费位置，函数 `Function1[-A, +R]` 是经典案例。
7. **Phantom Type**：把"状态/校验"写到类型里，编译期就替你看门。

---

# 🎓 学完这 12 个 Scene 后，你已经能

- ✅ 看懂 http4s / doobie / skunk 这类工业库的 API 设计
- ✅ 设计自己的 type class 体系，做到"业务通吃多种运行时"
- ✅ 用 fs2 写出工业级流式应用（爬虫、ETL、消息消费者）
- ✅ 用 Phantom + opaque type 写出**编译期就拒绝错误**的库 API
- ✅ 看懂 cats / scalaz 任意源码而不再迷路

> Scala 函数式生态的"语言关 + 模式关"已全部过关。
> 剩下的就是**实战**：找一个真实项目，把这些武器在生产中用起来。
