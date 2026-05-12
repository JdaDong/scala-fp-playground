# Type Class 模式：Scala 区别于 Java 最深的特性

> 配套代码：`src/main/scala/demo/part07/`
>
> 目标：把 "Type Class 思想" 从 *为什么需要* → *标准模板* → *派生* → *Cats 实战* → *业务实战* 一次讲透。

---

## 🎯 全文导航

| 章节 | 主题 | 配套文件 |
|---|---|---|
| Part 1 | 为什么需要 Type Class（4 种方案演进） | `Scene01_WhyTypeClass.scala` |
| Part 2 | 标准三件套：trait + companion + syntax | `Scene02_StandardPattern.scala` |
| Part 3 | 派生：手动 / contramap / Mirror 自动 | `Scene03_Derivation.scala` |
| Part 4 | Cats 真实生态：Semigroup / Monoid / Functor / Monad | `Scene04_CatsTypeClasses.scala` |
| Part 5 | 实战：可插拔 ETL（4 个 type class 组合） | `Scene05_PluggableETL.scala` |

---

## Part 1 · 为什么需要 Type Class

### 1.1 Java/OOP 的天然约束

Java 给类型赋予能力的唯一方式：**让类型 implements 接口**。

这意味着：
- `Int` / `String` 等内置类型 ❌ 改不了
- 第三方库的类 ❌ 改不了
- 同一类型对"加法/排序"等行为只能有 **一种实现**

### 1.2 4 种方案的演进对比

| 方案 | 通用性 | 不修改原类型 | 调用简洁 | 备注 |
|---|---|---|---|---|
| 方法重载 | ❌ | ✅ | ✅ | 每多一种类型多写一份 |
| OOP 接口 | ✅ | ❌ | ✅ | 不能给内置/第三方类型用 |
| 函数参数注入 | ✅ | ✅ | ❌ | 调用方每次都要传行为 |
| **Type Class** | ✅ | ✅ | ✅ | **三者兼得** |

### 1.3 Type Class 的"三步曲"

```scala
// Step 1: 定义"行为契约"
trait Monoid[A] {
  def empty: A
  def combine(a: A, b: A): A
}

// Step 2: 为各种类型提供 given（隐式）实例
given Monoid[Int]    with { def empty = 0;  def combine(a: Int, b: Int) = a + b }
given Monoid[String] with { def empty = ""; def combine(a: String, b: String) = a + b }

// Step 3: 写函数时用 [A: Monoid] 约束 ── 调用方什么都不用传
def sum[A: Monoid](xs: List[A]): A =
  xs.foldLeft(summon[Monoid[A]].empty)(summon[Monoid[A]].combine)

sum(List(1, 2, 3))           // 6
sum(List("a", "b", "c"))     // "abc"
```

### 1.4 核心心法

> **行为不属于类型，行为属于"类型 + 上下文"。**
> 同一个 `Int`，在数据库里是 Monoid（求和），在游戏里是 Group（带逆元），完全不冲突。

这就是 Scala 区别于 Java 最深的设计哲学。

---

## Part 2 · 标准三件套：trait + companion + syntax

工业级 Type Class 的"模板"——Cats / Scalaz / circe 里每个 type class 都按这个结构写。

### 2.1 三件套结构

```scala
// ① trait：行为契约
trait Show[A] {
  def show(a: A): String
}

// ② companion：放 apply + 标准实例 + 工厂方法
object Show {
  def apply[A](using s: Show[A]): Show[A] = s        // ← Show[Int].show(42)
  def from[A](f: A => String): Show[A] = a => f(a)   // ← 工厂方法

  given Show[Int]    = from(_.toString)
  given Show[String] = from(s => s"\"$s\"")

  // 高阶 instance：依赖另一个 instance
  given listShow[A](using sh: Show[A]): Show[List[A]] =
    from(_.map(sh.show).mkString("[", ",", "]"))
}

// ③ syntax：扩展方法
object syntax {
  extension [A](a: A)(using sh: Show[A]) {
    def show: String = sh.show(a)
  }
}

// 用户侧
import syntax.show
List(1, 2, 3).show        // "[1,2,3]"
```

### 2.2 关键术语

| 写法 | 含义 |
|---|---|
| `given X: Show[T] with { ... }` | 完整 given，能取名字 |
| `given Show[T] = ...` | 匿名 given，编译器自动起名 |
| `using sh: Show[A]` | Scala 3 的隐式参数（替代 implicit） |
| `summon[Show[A]]` | 取出当前作用域的 given（替代 implicitly） |
| `[A: Show]` | context bound，等价 `(using Show[A])` |
| `extension [A](a: A) ...` | 扩展方法（替代 Scala 2 的 implicit class） |

### 2.3 隐式查找的优先级

编译器找 `given Show[T]` 时按这个顺序：

1. 当前 lexical 作用域里 `import` 进来的 given
2. `T` 的 companion object 里的 given
3. `Show` 的 companion object 里的 given
4. given 参数链中的 given（依赖注入）

> **最佳实践**：业务类型 `Foo` 的 given 写在 `object Foo` 里，
> 这样调用方完全不用 import 就能用。

---

## Part 3 · 派生（Derivation）：实例的"自动生成"

### 3.1 手动派生

`Encoder[A]` → `Encoder[List[A]]` 由 A 的实例自动拼出来：

```scala
given listEnc[A](using e: Encoder[A]): Encoder[List[A]] =
  Encoder.from(_.map(e.encode).mkString("[", ",", "]"))
```

### 3.2 contramap：反向映射派生

如果 `A → B` 并且有 `Encoder[B]`，那就有了 `Encoder[A]`：

```scala
extension [B](eb: Encoder[B])
  def contramap[A](f: A => B): Encoder[A] = Encoder.from(a => eb.encode(f(a)))

case class UserId(value: Long)
given Encoder[UserId] = Encoder[Long].contramap(_.value)
//                          ↑ 复用 Long 的实现，零样板代码
```

| 方法 | 类型 | 用途 |
|---|---|---|
| `map[B](f: A => B): F[B]` | covariant | "我能产生 A，那我也能产生 B" |
| `contramap[A](f: A => B): F[A]` | contravariant | "我能消费 B，那我也能消费 A" |
| `imap[B](f: A => B, g: B => A): F[B]` | invariant | 双向（如 codec） |

### 3.3 Scala 3 自动派生（Mirror）

最强大的能力：**编译期反射 case class 字段，自动生成 instance**。

```scala
// 在 Encoder 的 companion 里实现 derived
object Encoder {
  inline def derived[A](using m: Mirror.ProductOf[A]): Encoder[A] = {
    val labels = labelsOf[m.MirroredElemLabels]
    val encs   = summonEncoders[m.MirroredElemTypes]
    // ... 把每个字段编码后拼成 JSON
  }
}

// 用户侧：一行搞定
case class User(id: Long, name: String, addr: Address) derives Encoder
//                                                     ↑ 编译器自动调用 Encoder.derived[User]
```

效果：

```
User(1, "Alice", Address("Beijing", 100000))
  → {"id":1,"name":"Alice","addr":{"city":"Beijing","zip":100000}}
```

> 这就是 **circe / jsoniter / quill** 这些库 "JSON 自动编解码" 的原理。
> 不再需要 macros 库（Shapeless 时代的痛点），Scala 3 内建支持。

---

## Part 4 · 走进 Cats：真实生态的 Type Class 阶梯

### 4.1 代数阶梯（最重要的 4 个）

```
Semigroup ──加 empty──► Monoid ──加 inverse──► Group
   |                       |
   |                       └── 业务 90% 都够用
   └── 仅"能 combine"，无零元

Functor ──加 ap──► Applicative ──加 flatMap──► Monad ──加 handleError──► MonadError
   |                                              |
   |                                              └── for-comprehension 的灵魂
   └── 仅"能 map"
```

### 4.2 Semigroup / Monoid：能"加"的东西

```scala
import cats.syntax.all.*

1 |+| 2 |+| 3                                  // 6
"a" |+| "b"                                    // "ab"
List(1) |+| List(2)                            // List(1, 2)
Map("a" -> 1) |+| Map("a" -> 2, "b" -> 3)      // Map(a -> 3, b -> 3)  ← 按 key 合并！
Option(1) |+| Option(2) |+| None               // Some(3)

List(1, 2, 3, 4, 5).combineAll                 // 15  （要求 Monoid，因为要 empty）
```

**业务场景**：合并多份配置、聚合统计、把 `List[Map]` 折叠成一个 `Map`……

### 4.3 Functor：能 `map`

```scala
def addOne[F[_]: Functor](fa: F[Int]): F[Int] = fa.map(_ + 1)

addOne(List(1, 2, 3))                  // List(2, 3, 4)
addOne(Option(10))                     // Some(11)
addOne(Right(42): Either[String, Int]) // Right(43)
```

> 一个函数适用于所有"能装东西"的类型——**这就是 Cats 库工具函数那么少但那么通用的原因**。

### 4.4 Monad：能 `flatMap`，for-comprehension 的灵魂

```scala
def addPair[F[_]: Monad](fa: F[Int], fb: F[Int]): F[Int] =
  for { a <- fa; b <- fb } yield a + b

addPair(Option(1), Option(2))            // Some(3)
addPair(List(1, 2), List(10, 20))        // List(11, 21, 12, 22) ← 笛卡尔积
addPair(Right(1), Right(2): Either[...]) // Right(3)
```

**同一段 for-comprehension 的"语义"取决于你选哪个 Monad**：

| Monad | 语义 |
|---|---|
| `Option` | 短路（任一 `None` 直接终止） |
| `Either[E, *]` | 失败短路并保留错误 |
| `List` | 笛卡尔积（所有组合） |
| `Future` | 异步顺序执行 |
| `IO` | 副作用安全的顺序执行 |
| `Validated` | 错误累积（不过它只是 Applicative，不是 Monad） |

### 4.5 终极组合：Monad + Monoid

```scala
def sumF[F[_]: Monad, A: Monoid](xs: List[F[A]]): F[A] =
  xs.foldLeft(Monoid[A].empty.pure[F]) { (acc, fa) =>
    for { a <- acc; b <- fa } yield a |+| b
  }

sumF(List(Option(1), Option(2), Option(3)))  // Some(6)
sumF(List(Option(1), None, Option(3)))       // None     ← 短路
sumF(List(List(1, 2), List(10, 20)))         // List(11, 12, 21, 22)  ← 笛卡尔积
```

> **一段 8 行代码**同时支持短路、异步、笛卡尔积、错误累积等多种语义。
> 这是 OOP 永远写不出来的抽象高度。

---

## Part 5 · 实战：可插拔 ETL

### 5.1 业务诉求

- 多种"源"（CSV / JSON / 内存）
- 多种"汇"（控制台 / 文件 / 收集到 List）
- 自由组合（CSV 进 → JSON 出）
- 加新格式只写最少的样板代码

### 5.2 4 个 type class 的设计

```scala
trait Parser[A]   { def parse(line: String): Either[String, A] }
trait Renderer[A] { def render(a: A): String }
trait Source[A]   { def read(): Iterator[A] }
trait Sink[A]     { def write(a: A): Unit }
```

### 5.3 实例库

```scala
object CsvFormat {
  given csvParser:   Parser[Record]   = ...
  given csvRenderer: Renderer[Record] = ...
}

object JsonFormat {
  given jsonParser:   Parser[Record]   = ...
  given jsonRenderer: Renderer[Record] = ...
}
```

### 5.4 业务管道（与具体格式完全解耦）

```scala
def etl[A](source: Source[A], sink: Sink[A], filter: A => Boolean = _ => true): Int = {
  var n = 0
  source.read().filter(filter).foreach { a => sink.write(a); n += 1 }
  sink.close(); n
}
```

### 5.5 自由组合

```scala
// CSV 进 → JSON 出（跨格式自动转换）
import CsvFormat.csvParser       // 只导入解析端
import JsonFormat.jsonRenderer   // 只导入渲染端
etl(linesSource[Record](csvLines), consoleSink[Record]("CSV→JSON"))
```

实际效果：

```
[CSV→JSON] {"id":1,"name":"Alice","score":95}
[CSV→JSON] {"id":2,"name":"Bob","score":72}
[CSV→JSON] {"id":3,"name":"Charlie","score":88}
```

### 5.6 加新格式 = 写两个 given

```scala
// 新增 XML 支持？只要写：
object XmlFormat {
  given xmlParser:   Parser[Record]   = ...
  given xmlRenderer: Renderer[Record] = ...
}
// 然后 etl 立刻就能用 XML，不用改任何业务代码
```

> 这就是 **fs2 / akka-streams / Spark Dataset** 等流式库的设计核心：
> 数据源/数据汇是 type class，业务管道是泛型函数。

---

## 🚀 运行方式

```bash
sbt "runMain demo.part07.Scene01_WhyTypeClass"
sbt "runMain demo.part07.Scene02_StandardPattern"
sbt "runMain demo.part07.Scene03_Derivation"
sbt "runMain demo.part07.Scene04_CatsTypeClasses"
sbt "runMain demo.part07.Scene05_PluggableETL"
```

---

## 🧠 心法总结（背下来一辈子受用）

1. **Type Class = 类型与行为解耦** —— 行为放进 trait，实例用 given 注册。
2. **三件套是模板** —— `trait + companion + syntax`，照抄即可。
3. **优先把 given 放在 companion 里** —— 调用方零 import。
4. **派生优于重复实现** —— `contramap` / `derives` 让你只写一次逻辑。
5. **泛型函数用 context bound** —— `[A: Monoid]` 比 `(using Monoid[A])` 更优雅。
6. **不要把 type class 当继承用** —— 它是 *能力赋予*，不是 *分类层次*。
7. **优先用 Cats 已有的** —— 别自己造 Functor / Monad，标准实例非常完整。

---

## 🆚 Type Class vs OOP 接口（一图看懂）

| 维度 | OOP 接口 | Type Class |
|---|---|---|
| 行为绑定方式 | 类型 implements 接口 | 类型 + 隐式实例 |
| 能给内置类型加吗？ | ❌ | ✅ |
| 能给第三方类加吗？ | ❌（除非包装器） | ✅ |
| 同一类型多种行为？ | ❌（钻石继承） | ✅（不同作用域不同 instance） |
| 自动派生？ | ❌ | ✅（Mirror / 宏） |
| 性能开销 | 虚方法分派 | 编译期确定 → 几乎零开销 |
| 思想 | 分类（is-a） | 能力（has-a-instance） |

---

## 🔗 进阶方向

- **Cats Effect** ✅ 已学（part06）
- **Free Monad / Tagless Final** ── 用 type class 把"业务 DSL"和"解释器"分离
- **Shapeless / Scala 3 Mirrors 进阶** ── 把派生玩到极致
- **HKT (Higher-Kinded Types)** ── `F[_]` 的更深层模式
- **Type Class Coherence** ── Scala 不强制全局唯一 instance 的取舍

掌握了 Type Class，**Scala 函数式生态的"语言关"基本就过了**。
后面无论遇到 fs2 的 `Pipe`、http4s 的 `EntityDecoder`、circe 的 `Encoder`，都不会陌生——
全是同一套模式的不同应用。
