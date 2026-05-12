# cats-mtl：把 Tagless Final 推向工业级

> 配套代码：`src/main/scala/demo/part12/`
>
> 目标：把 "多能力组合" 这个老大难从 Monad Transformer 地狱中解救出来。

---

## 🎯 全文导航

| Scene | 主题 | 配套文件 |
|---|---|---|
| 1 | Monad Transformer 的 4 大痛点 | [Scene01_MTPain.scala](../src/main/scala/demo/part12/Scene01_MTPain.scala) |
| 2 | cats-mtl 6 个核心 type class | [Scene02_CatsMtlIntro.scala](../src/main/scala/demo/part12/Scene02_CatsMtlIntro.scala) |
| 3 | 工业实战：同业务、两套 interpreter | [Scene03_TwoInterpreters.scala](../src/main/scala/demo/part12/Scene03_TwoInterpreters.scala) |

---

## Part 1 · 为什么需要 cats-mtl

### 1.1 真实业务的"能力集合"

一个订单服务往往同时需要：

| 能力 | 例子 |
|---|---|
| **读配置** | 折扣率、最低订单金额 |
| **维护状态** | 库存、订单统计 |
| **累积日志** | 审计流水 |
| **短路错误** | 库存不足立刻终止 |
| **异步 IO** | 写数据库、调下游 |

### 1.2 传统做法：Monad Transformer 栈

```scala
type Stack0[A] = ReaderT[IO, Config, A]              // IO + 读配置
type Stack1[A] = StateT[Stack0, Stats, A]            // + 状态
type App[A]    = EitherT[Stack1, AppError, A]        // + 错误
```

### 1.3 4 大痛点（Scene01 实测）

| # | 痛点 | 例子 |
|---|---|---|
| 1 | **类型签名恶心长** | 一个 `processOrder` 的签名就要 3 行 |
| 2 | **lift 地狱** | 读个 Config 要写 `EitherT.liftF(StateT.liftF(ReaderT.ask))` |
| 3 | **耦合栈顺序** | `raise` 必须知道自己在 `EitherT.leftT[Stack1, A]` 第几层 |
| 4 | **难以扩展** | 想在 Reader 和 State 之间插一层？所有业务签名都要改 |

```scala
// Scene01 里真实的"读配置"代码
val readConfig: App[Config] =
  EitherT.liftF(StateT.liftF(ReaderT.ask[IO, Config]))
//  ^        ^       ^       ^
//  穿 3 层才能读一个配置
```

---

## Part 2 · cats-mtl 的答案

### 2.1 6 个核心 type class

```
┌──────────────────┬────────────────────────────────┬───────────────────┐
│  Type Class      │  能力                          │  对标 Transformer │
├──────────────────┼────────────────────────────────┼───────────────────┤
│  Ask[F, R]       │  读配置/环境                    │  ReaderT          │
│  Local[F, R]     │  Ask + 作用域内临时修改 R       │  ReaderT          │
│  Tell[F, L]      │  追加日志/累加器                │  WriterT          │
│  Stateful[F, S]  │  读写状态                       │  StateT           │
│  Raise[F, E]     │  抛错误（短路）                 │  EitherT          │
│  Handle[F, E]    │  Raise + 捕获错误               │  EitherT          │
└──────────────────┴────────────────────────────────┴───────────────────┘
```

### 2.2 业务签名变优雅了

```scala
def processOrder[F[_]: Monad](name: String, amount: BigDecimal)(using
    ask:   Ask[F, Config],
    stat:  Stateful[F, Stats],
    tell:  Tell[F, List[String]],
    raise: Raise[F, AppError]
): F[BigDecimal] = for {
  cfg   <- ask.ask                            // ← 直接读，零 lift
  _     <- tell.tell(List(s"处理订单 $name"))
  _     <- if (amount < cfg.minOrder) raise.raise(OrderTooSmall): F[Unit]
           else                              Monad[F].unit
  finalAmt = amount * (BigDecimal(1) - cfg.discount)
  _     <- stat.modify(s => s.copy(processed = s.processed + 1, ...))
} yield finalAmt
```

### 2.3 与手写 Transformer 的对比

| 维度 | 手写 MT 栈 (Scene01) | cats-mtl (Scene02) |
|---|---|---|
| 读 Config | `EitherT.liftF(StateT.liftF(ReaderT.ask))` | `ask.ask` |
| 改状态 | `EitherT.liftF(StateT.modify[Stack0, Stats](f))` | `stat.modify(f)` |
| 抛错误 | `EitherT.leftT[Stack1, A](err)` | `raise.raise(err)` |
| 签名 | `App[A]`（具体栈） | `F[_]` + 能力约束（完全抽象） |
| 业务代码 | 夹杂 lift 噪音 | 干净 for-comprehension |
| 加一个新能力 | 改栈 + 改所有业务 | 在 `using` 里加一行 |

### 2.4 实测等价（Scene01 vs Scene02）

同样的输入：
- Book(80)、Pencil(100)、Sticker(10)；最低金额 50，折扣 10%

**两个 Scene 都得到完全一致的输出：**
```
Stats(2, 162.0)            ← 处理了 2 笔，总额 72+90=162
Left(OrderTooSmall)        ← Sticker(10) 短路
```

---

## Part 3 · 工业实战：Scene03

### 3.1 场景：订单服务（更完整）

- 业务：用户下单 → 校验 → 按会员等级应用折扣 → 扣库存 → 追加事件流水
- 能力：`Ask / Stateful / Tell / Raise` 四件套

### 3.2 一段业务代码，两套 Interpreter

**业务代码（对 F 完全多态，只声明能力）：**

```scala
def placeOrder[F[_]: Monad](userId: String, tier: UserTier, sku: Sku, qty: Int)(using
    ask:   Ask[F, AppConfig],
    inv:   Stateful[F, Inventory],
    tell:  Tell[F, Chain[Event]],
    raise: Raise[F, DomainError]
): F[BigDecimal] = ...
```

#### Interpreter A：Monad Transformer 栈

```scala
type R[A]   = Kleisli[IO, AppConfig, A]
type S[A]   = StateT[R, Inventory, A]
type W[A]   = WriterT[S, Chain[Event], A]
type App[A] = EitherT[W, DomainError, A]
```

cats-mtl **自动为这些 transformer 提供 given 实例**，业务代码直接跑。适合快速搭原型、全部纯函数式风格。

#### Interpreter B：直接 IO + Ref（生产型）

```scala
given raiseIO: Raise[IO, DomainError] with {
  def functor = Functor[IO]
  def raise[E2 <: DomainError, A](e: E2): IO[A] = IO.raiseError(DomainErr(e))
}

def make(cfg, invRef, eventsRef): (Ask[IO, ...], Stateful[IO, ...], Tell[IO, ...]) = {
  val ask      = new Ask[IO, AppConfig]       { def ask[E2 >: AppConfig] = IO.pure(cfg) ... }
  val stateful = new Stateful[IO, Inventory]  { def get = invRef.get; def set(s) = invRef.set(s) ... }
  val tell     = new Tell[IO, Chain[Event]]   { def tell(l) = eventsRef.update(_ |+| l) ... }
  (ask, stateful, tell)
}
```

- 完全没 Monad Transformer
- 性能最好（每次操作直接是 IO）
- 线上调试友好（栈轨迹清晰）

### 3.3 实测结果

两个 Interpreter 输出**位比特完全一致**：

```
结果:   Left(OutOfStock(SKU-A))
库存:   Inventory(Map(SKU-A -> 3, SKU-B -> 7))
事件(4):
  - OrderPlaced(U001, SKU-A, 2, 180.00)
  - StockChanged(SKU-A, 3)
  - OrderPlaced(U002, SKU-B, 3, 120.00)
  - StockChanged(SKU-B, 7)

结果一致? true
库存一致? true
事件一致? true
```

### 3.4 场景推理

假设初始库存 `{SKU-A: 5, SKU-B: 10}`，依次处理 3 笔订单：

| 订单 | 会员等级 | 买 SKU | 数量 | 会员折扣 | 状态 | 结果 |
|---|---|---|---|---|---|---|
| U001 | Member | A | 2 | 10% | 成功 | 原价 100×2=200，折后 180 |
| U002 | Vip | B | 3 | 20% | 成功 | 原价 50×3=150，折后 120 |
| U003 | Normal | A | 10 | 0 | **失败** | OutOfStock（A 剩 3，买 10） |

最终：库存 `{A: 3, B: 7}`，4 个事件（2 订单 + 2 库存变更），返回 `Left(OutOfStock)`。

---

## 🧠 心法总结

### cats-mtl 与其他方案的定位

```
Monad Transformer 栈 ─── "一切具体化"（硬编码栈）
         ↓
Tagless Final (part08) ── "行为抽象"（trait + IO）
         ↓
cats-mtl (part12)     ── "能力抽象"（通用 type class，与 F 解耦）
```

### 什么时候用 cats-mtl

| 情况 | 用什么 |
|---|---|
| 业务只需要 1~2 种能力（如 IO+错误） | Tagless Final（自己写 trait） |
| 业务需要 3+ 种能力，且能力通用（Ask/State/Tell...） | **cats-mtl** ✅ |
| 需要静态分析"程序"、收集 AST | Free Monad |
| 领域特定 DSL 要被多种解释器解读 | Free + cats-mtl 结合 |

### 7 句心法

1. **Ask** = 读配置；**Tell** = 写日志/累加；**Stateful** = 读写状态；**Raise** = 抛错
2. 业务只声明 `[F[_]]` + `using 能力...`，**完全不关心具体栈**
3. `ask.ask` / `stat.modify` / `tell.tell` / `raise.raise` 比 `liftF` 清晰一万倍
4. Interpreter 可以选 **Monad Transformer 栈** 或 **直接 IO + Ref**（性能差很多）
5. 加新能力 = 在 `using` 里加一行，业务零修改
6. cats-mtl 是 **Tagless Final 的"升级版"**：把"能力"从自定义 trait 升级为标准化 type class
7. `http4s4s`/`doobie` 的最佳实践：**业务层用 cats-mtl，边界层用 IO + Ref**

---

## 🚀 运行方式

```bash
sbt "runMain demo.part12.Scene01_MTPain"
sbt "runMain demo.part12.Scene02_CatsMtlIntro"
sbt "runMain demo.part12.Scene03_TwoInterpreters"
```

---

## 🎓 学完这 3 个 Scene，你已经能

- ✅ 识别一段 Scala 代码是"MT 栈"还是"cats-mtl"
- ✅ 把 MT 栈的旧代码重构成 cats-mtl 风格
- ✅ 同时维护两套 Interpreter（教学用 MT + 生产用 IO+Ref）
- ✅ 设计跨微服务的"能力契约"（Ask/Tell/Stateful 作为领域抽象）

> 至此 Scala 函数式抽象层的终极武器已到手。
> 继续实战：把 `doobie + http4s + cats-mtl` 组合搭一个真实服务吧！
