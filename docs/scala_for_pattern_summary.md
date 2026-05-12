### 标题：Scala for推导式 + case class模式匹配 知识总结

# Scala 进阶：`for` 推导式 + `case class` & 模式匹配 知识总结

> 配套代码：
> - [`Scene01_ForComprehension.scala`](src/main/scala/demo/part05/Scene01_ForComprehension.scala)
> - [`Scene02_CaseClassPatternMatch.scala`](src/main/scala/demo/part05/Scene02_CaseClassPatternMatch.scala)

---

## 目录

- [Part 1：`for` 推导式](#part-1for-推导式)
  - [1.1 本质：语法糖](#11-本质语法糖)
  - [1.2 Option 链式取值](#12-option-链式取值)
  - [1.3 Future 异步编排](#13-future-异步编排)
  - [1.4 Either 业务校验（短路）](#14-either-业务校验短路)
  - [1.5 Try 异常安全链](#15-try-异常安全链)
  - [1.6 Validated 错误累积（与 Either 对比）](#16-validated-错误累积与-either-对比)
- [Part 2：`case class` + 模式匹配](#part-2case-class--模式匹配)
  - [2.1 `case class` 白送的能力](#21-case-class-白送的能力)
  - [2.2 ADT：`sealed trait` + `case class`](#22-adtsealed-trait--case-class)
  - [2.3 模式匹配的全姿势](#23-模式匹配的全姿势)
  - [2.4 实战：订单状态机](#24-实战订单状态机)
  - [2.5 实战：表达式 AST](#25-实战表达式-ast)
  - [2.6 实战：JSON 模型](#26-实战json-模型)
  - [2.7 访问者模式 vs 模式匹配](#27-访问者模式-vs-模式匹配)
- [Part 3：速查表](#part-3速查表)

---

## Part 1：`for` 推导式

### 1.1 本质：语法糖

```scala
for { x <- A; y <- B; z <- C } yield f(x, y, z)
// 编译器自动展开为：
A.flatMap(x => B.flatMap(y => C.map(z => f(x, y, z))))
```

> 任何具备 `flatMap` / `map` / （可选）`withFilter` 的容器都能用 `for` 推导式。
> 因此 `Option` / `Either` / `Try` / `Future` / `List` / `Stream` / `IO`（cats-effect） 都通用。

**关键特性：短路。** 中间任何一步是 `None / Failure / Left`，后续不会执行。

---

### 1.2 Option 链式取值

```scala
val cityOpt: Option[String] = for {
  user    <- userDb.get(id)
  profile <- user.profile
  addr    <- profile.address
  city    <- addr.city
} yield city
```

| 不用 `for` | 用 `for` |
|---|---|
| 4 层 `if (x.isDefined) {...}` 嵌套 | 4 行干净的赋值 |
| 任一步 `null` / `None` 容易漏掉 | 自动短路返回 `None` |

---

### 1.3 Future 异步编排

#### ⚠️ 串行 vs 并行的关键技巧

```scala
// ❌ 错误：3 个 Future 全部串行（耗时 = 之和）
for {
  user     <- fetchUser(id)
  orders   <- fetchOrders(id)
  discount <- fetchDiscount(id)
} yield ...

// ✅ 正确：第一个串行，后两个并行（耗时 = max）
val ordersF   = fetchOrders(id)        // ← 在 for 外面立即触发
val discountF = fetchDiscount(id)      // ← 在 for 外面立即触发
for {
  user     <- fetchUser(id)
  orders   <- ordersF                   // 这里只是"等结果"，不重新触发
  discount <- discountF
} yield ...
```

> **记忆点**：`Future` 在被定义时就开始执行；`for` 里的 `<-` 是"等结果"不是"启动"。

#### 失败兜底

```scala
result.recover { case e: TimeoutException => "默认值" }
result.recoverWith { case _ => fallbackFuture }
```

---

### 1.4 Either 业务校验（短路）

```scala
def register(form: RegisterForm): Either[String, RegisterForm] =
  for {
    n <- validateName(form.name)        // Either[String, String]
    e <- validateEmail(form.email)
    a <- validateAge(form.age)
    p <- validatePassword(form.password)
  } yield RegisterForm(n, e, a, p)
```

- 约定：`Left = 错误`，`Right = 成功`
- 任何一步 `Left` 立即返回，**只能拿到第一个错误**

---

### 1.5 Try 异常安全链

```scala
def compute(a: String, b: String): Try[Double] =
  for {
    x <- parseInt(a)        // Try[Int]
    y <- parseInt(b)
    q <- divide(x, y)
    r <- sqrt(q)
  } yield r

compute(...) match {
  case Success(v) => ...
  case Failure(e) => e.getMessage
}
```

把"会抛异常的链式调用"安全地组合，并保留**失败原因**。

---

### 1.6 Validated 错误累积（与 Either 对比）

| 对比维度 | `Either` | `Validated` |
|---|---|---|
| 类型类身份 | **Monad**（有 `flatMap`） | **Applicative**（无 `flatMap`，有 `mapN`） |
| 是否支持 `for` | ✅ 支持 | ❌ 不支持（必须用 `mapN`） |
| 错误处理 | **短路**：第一个错误就返回 | **累积**：所有错误一起返回 |
| 适用场景 | 步骤之间**有依赖** | 字段之间**相互独立** |

```scala
// 给同一个错误的表单：名字空 / 邮箱错 / 年龄小 / 密码短
val bad = RegisterForm("", "bad-email", 15, "123")

// Either 短路 → 只看到第一个错误
register(bad)            // Left("姓名不能为空")

// Validated 累积 → 一次性拿到 4 个错误
registerAccumulate(bad)  // Invalid(List(姓名..., 邮箱..., 年龄..., 密码...))
```

> 真实项目里：直接用 [`cats.data.ValidatedNel`](https://typelevel.org/cats/datatypes/validated.html)。

#### 决策树

```
是否需要"步骤 B 用到步骤 A 的结果"？
├─ 是 → 用 Either + for（短路）
└─ 否 → 用 Validated + mapN（累积）
```

---

### 1.7 Cats `Validated` 真实用法

> 配套代码：[`Scene03_CatsValidated.scala`](src/main/scala/demo/part05/Scene03_CatsValidated.scala)
>
> 依赖：`"org.typelevel" %% "cats-core" % "2.10.0"`（已在 `build.sbt` 中加好）

#### ① 三个核心类型

| 类型 | 含义 | 推荐度 |
|---|---|---|
| `Validated[+E, +A]` | 最基础，错误是 `E`（必须是 `Semigroup`） | ⭐ |
| `ValidatedNel[+E, +A]` | `Validated[NonEmptyList[E], A]` | ⭐⭐⭐ 最常用 |
| `ValidatedNec[+E, +A]` | `Validated[NonEmptyChain[E], A]` | ⭐⭐ 拼接更快 |

#### ② 核心 API 一张图

```scala
import cats.data.{Validated, ValidatedNel, NonEmptyList}
import cats.syntax.validated.*    // .validNel / .invalidNel
import cats.syntax.apply.*        // (a, b, c).mapN(...)
import cats.syntax.either.*       // .toValidatedNel
import cats.syntax.traverse.*     // list.traverse(f) / list.sequence

// 构造
"abc".validNel[String]              // Valid("abc")
"err".invalidNel[String]            // Invalid(NEL.of("err"))
Right("ok").toValidatedNel          // Either ↔ Validated 互转

// 组合（错误自动累积）
(va, vb, vc, vd).mapN(MyClass.apply)

// 串联依赖（替代 flatMap）
v1.andThen(a => v2(a))

// 批量
forms.traverse(validate)             // List[A] → ValidatedNel[E, List[B]]
```

#### ③ 手写版 vs Cats 版 对比表

| 维度 | 手写版（`Scene01`） | Cats `Validated` |
|---|---|---|
| 错误类型 | `List[String]` 写死 | 任意 `Semigroup`（`NEL` / `NEC` / `Set` / `Map`...） |
| 组合 API | 自己写 `map2` / `map3` / `map4` | `mapN` 通用（2~22 元） |
| 依赖步骤 | ❌ 不支持 | ✅ `andThen` |
| 批量校验 | 自己 `fold` | `traverse` / `sequence` |
| 生态互通 | 无 | cats-effect / circe / http4s / fs2 ... |
| 学习成本 | 低 | 中（需要懂 Applicative） |
| 适用阶段 | 入门理解原理 | 生产项目 |

#### ④ 强类型错误（推荐生产做法）

```scala
sealed trait FormError { def code: String; def message: String }
case object EmptyName              extends FormError { val code = "E001"; ... }
case class  BadEmail(raw: String)  extends FormError { val code = "E002"; ... }
case class  BadAge(value: Int)     extends FormError { val code = "E003"; ... }

// 错误类型从 String 升级为 sealed trait → 国际化 / 序列化 / 统计 都方便
def vName(n: String): ValidatedNel[FormError, String] =
  if (n.trim.nonEmpty) n.trim.validNel else EmptyName.invalidNel

def register(f: Form): ValidatedNel[FormError, Form] =
  (vName(f.name), vEmail(f.email), vAge(f.age), vPwd(f.pwd)).mapN(Form.apply)
```

#### ⑤ `traverse`：批量校验的杀手锏

```scala
val forms: List[RegisterForm] = ...

forms.traverse(register)
//   List[Form] → ValidatedNel[FormError, List[Form]]
//
//   全部 Valid → Valid(List(...))
//   任一 Invalid → 把所有人的所有错误累积到 NEL
```

#### ⑥ `andThen`：累积 + 依赖 混合

```scala
// 字段校验（累积错误） → 数据库查重（依赖前一步成功）
register(form).andThen { ok =>
  checkUnique(ok.name).map(_ => ok)
}
```

> ⚠️ `Validated` 没有 `flatMap`、不支持 `for` 推导式。需要"前一步通过才能下一步"时，用 `andThen`（或先 `.toEither` 转回去）。

---



## Part 2：`case class` + 模式匹配

### 2.1 `case class` 白送的能力

```scala
case class User(name: String, age: Int)
```

编译器自动生成：

| 能力 | 说明 |
|---|---|
| `apply` | 不用 `new`：`User("a", 1)` |
| `unapply` | 解构：`val User(n, a) = u` |
| `equals` / `hashCode` | 按字段比较 |
| `toString` | `"User(a,1)"` |
| `copy` | 不可变更新：`u.copy(age = 2)` |
| 字段默认 `val` | 不可变 |
| 实现 `Product` & `Serializable` | 可直接序列化 |

> 一个 `case class` ≈ Java 的 POJO + Lombok @Data + Builder + Record。

---

### 2.2 ADT：`sealed trait` + `case class`

```scala
sealed trait Shape
case class Circle(r: Double)                        extends Shape
case class Rectangle(w: Double, h: Double)          extends Shape
case class Triangle(a: Double, b: Double, c: Double) extends Shape
case object EmptyShape                               extends Shape
```

`sealed` 关键字 → 所有子类**必须在同文件**定义 → 编译器知道完整子类集合 → `match` **穷尽性检查**：

```scala
def area(s: Shape): Double = s match {
  case Circle(r)       => math.Pi * r * r
  case Rectangle(w, h) => w * h
  // 漏写 Triangle / EmptyShape → 编译器警告 "match may not be exhaustive"
}
```

---

### 2.3 模式匹配的全姿势

```scala
x match {
  case 0                       => ...   // 常量
  case n: Int if n < 0         => ...   // 类型 + 守卫
  case s: String               => ...   // 类型
  case (a, b)                  => ...   // 元组
  case List()                  => ...   // 空列表
  case head :: tail            => ...   // 中缀模式
  case List(1, 2, _*)          => ...   // 序列通配
  case Some(v)                 => ...   // 提取器
  case all @ Point(x, y)       => ...   // @绑定（同时拿整体和子部分）
  case Created | Paid(_)       => ...   // 联合模式
  case _                       => ...   // 通配
}
```

---

### 2.4 实战：订单状态机

```scala
def transit(s: OrderState, e: OrderEvent): OrderState = (s, e) match {
  case (Created,    PayEvent(amt))         => Paid(amt)
  case (Paid(_),    ShipEvent(no))         => Shipped(no)
  case (Shipped(_), DeliverEvent)          => Delivered(now)
  case (Created | Paid(_), CancelEvent(r)) => Cancelled(r)
  case (s, e)                              => s   // 非法转换保持原状态
}
```

> 模式匹配做状态机：**所有合法跳转一目了然**，胜过 `if-else`/`switch` 套娃。

---

### 2.5 实战：表达式 AST

```scala
sealed trait Expr
case class Num(v: Double)            extends Expr
case class Var(name: String)         extends Expr
case class Add(l: Expr, r: Expr)     extends Expr
case class Mul(l: Expr, r: Expr)     extends Expr
case class Neg(e: Expr)              extends Expr

def simplify(e: Expr): Expr = e match {
  case Add(l, r) => (simplify(l), simplify(r)) match {
    case (Num(0), x)      => x                  // 0+x = x
    case (x, Num(0))      => x
    case (Num(a), Num(b)) => Num(a + b)         // 常量折叠
    case (sl, sr)         => Add(sl, sr)
  }
  case Mul(l, r) => (simplify(l), simplify(r)) match {
    case (Num(0), _) | (_, Num(0)) => Num(0)
    case (Num(1), x)               => x
    case (x, Num(1))               => x
    case (Num(a), Num(b))          => Num(a * b)
    case (sl, sr)                  => Mul(sl, sr)
  }
  case Neg(inner) => simplify(inner) match {
    case Num(v) => Num(-v)
    case Neg(x) => x                            // --x = x
    case other  => Neg(other)
  }
  case leaf => leaf
}
```

`(x + 0) * (1 + 2) + -(-y)` → `simplify` → `x*3 + y` —— 这就是编译器优化的雏形。

---

### 2.6 实战：JSON 模型

```scala
sealed trait Json
case object JNull                            extends Json
case class  JBool(v: Boolean)                extends Json
case class  JNum(v: Double)                  extends Json
case class  JStr(v: String)                  extends Json
case class  JArr(items: List[Json])          extends Json
case class  JObj(fields: Map[String, Json])  extends Json

def query(json: Json, path: String): Option[Json] =
  path.split("\\.").foldLeft(Option(json)) { (acc, key) =>
    acc.flatMap {
      case JObj(fields) => fields.get(key)
      case _            => None
    }
  }
```

`circe` / `play-json` / `spray-json` 等库的核心建模思路就是这套。

---

### 2.7 访问者模式 vs 模式匹配

#### OOP 风格：访问者模式

```scala
trait ShapeV { def accept[R](v: ShapeVisitor[R]): R }
trait ShapeVisitor[R] {
  def visitCircle(c: CircleV): R
  def visitRectangle(r: RectangleV): R
  def visitTriangle(t: TriangleV): R
}

object AreaVisitor      extends ShapeVisitor[Double] { ... }
object PerimeterVisitor extends ShapeVisitor[Double] { ... }

shape.accept(AreaVisitor)
```

#### FP 风格：模式匹配

```scala
def area(s: Shape): Double      = s match { ... }
def perimeter(s: Shape): Double = s match { ... }
```

#### 维度对比（表达式问题 / Expression Problem）

| 维度 | 访问者模式（OOP） | 模式匹配（FP / ADT） |
|---|---|---|
| 加新**操作** | ✅ 新建一个 Visitor | ✅ 新写一个函数 |
| 加新**数据类型** | ❌ 改所有 Visitor | ⚠️ 改所有 `match` |
| 代码样板 | 多（`accept` / `visit` 双跳转） | 少（直接 `case`） |
| 穷尽性检查 | ❌ 运行时才发现漏 | ✅ `sealed` 编译期警告 |
| 可读性 | 低（控制流要跳两次） | 高（一处看到全部分支） |

#### 经验法则

| 数据类型变化频率 | 操作变化频率 | 推荐做法 |
|---|---|---|
| 稳定 | 经常加新操作 | **模式匹配**（绝大多数业务） |
| 经常变化 | 操作固定 | **访问者模式**（编译器、IDE 插件、AST 工具链） |
| 都常变 | — | 用 type class / 结构化匹配组合，例如 cats `Inject` |

---

## Part 3：速查表

### `for` 推导式选型

| 场景 | 推荐容器 | 理由 |
|---|---|---|
| 多层可空字段取值 | `Option` | 自动短路 |
| 异步任务编排 | `Future` | 异步组合 + `recover` |
| 业务校验（步骤有依赖） | `Either` | 短路 + 错误原因 |
| 异常包装（链式） | `Try` | 异常安全 |
| 表单/配置校验（错误累积） | `Validated`（cats） | 不短路 |
| 列表笛卡尔积 | `List` | `for` 嵌套循环 |

### 模式匹配模板

| 形态 | 写法 |
|---|---|
| 常量 | `case 0 =>` |
| 类型 + 守卫 | `case n: Int if n > 0 =>` |
| 元组解构 | `case (a, b) =>` |
| 列表头尾 | `case head :: tail =>` |
| `Option` | `case Some(v) =>` / `case None =>` |
| `case class` 解构 | `case User(n, a) =>` |
| `@` 绑定 | `case all @ Point(x, y) =>` |
| 联合模式 | `case A \| B =>` |
| 嵌套 | `case Add(Num(0), x) =>` |

### 选 `case class` 还是普通 `class`？

| 需求 | 选择 |
|---|---|
| 不可变值对象、需要解构、需要 `equals` | **`case class`** |
| 大量字段（>22）或需要继承复杂行为 | 普通 `class` |
| 单例 + 模式匹配 | **`case object`** |
| 算术容器（自定义 `+ - * /`） | 普通 `class` + `extends AnyVal` |

---

## 🎯 一句话精华

> **`for` 推导式**让"嵌套的容器操作"变成"看似顺序的代码"；
> **`case class` + 模式匹配**让"数据建模 + 分支处理"变成 Scala 最优雅的部分。
>
> 这两件武器是 Scala 区别于 Java 最大的生产力来源。

---

## 🚀 运行方式

```bash
sbt "runMain demo.part05.Scene01_ForComprehension"
sbt "runMain demo.part05.Scene02_CaseClassPatternMatch"
sbt "runMain demo.part05.Scene03_CatsValidated"     # 需要 cats-core 依赖
```
