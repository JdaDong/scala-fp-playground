package demo.part07

/**
 * ============================================================
 * Scene 04: 走进 Cats —— 看真实库里的 Type Class 长什么样
 *
 *   Cats 把"代数"建模成 Type Class 阶梯：
 *
 *      Semigroup     —— 有 combine
 *           ↓ 加 empty
 *      Monoid        —— 有 combine + empty  ★ 90% 常用
 *           ↓ 加 inverse
 *      Group         —— 有 combine + empty + inverse
 *
 *      Functor       —— 能 map
 *           ↓ 加 ap
 *      Applicative   —— 能 map + 独立任务并行组合
 *           ↓ 加 flatMap
 *      Monad         —— 能 map + flatMap（for-comprehension 的灵魂）
 *           ↓ 加 handleError
 *      MonadError    —— 能错误恢复（IO / Future / Either 的能力源头）
 *
 *   ★ 这些不是抽象数学概念，每一个都对应一种"组合代码的模式"。
 *   本场景只演示最常用的 4 个：Semigroup / Monoid / Functor / Monad。
 * ============================================================
 */
object Scene04_CatsTypeClasses {

  import cats.{Semigroup, Monoid, Functor, Monad}
  import cats.syntax.all.*           // 一次导入所有 syntax，从此可以写 a |+| b、xs.map、x.pure

  // ============================================================
  // 1. Semigroup —— 能 combine
  //    Cats 用 |+| 作为 combine 的中缀符号
  // ============================================================
  def demo_Semigroup(): Unit = {
    println("===== 1. Semigroup（|+|）=====")

    // 内置实例：Int / String / List / Map / Option / ...
    println(s"  Int    : ${1 |+| 2 |+| 3}")               // 6
    println(s"  String : ${"hello" |+| " " |+| "world"}") // hello world
    println(s"  List   : ${List(1, 2) |+| List(3, 4)}")   // List(1,2,3,4)

    // ★ Map 的 combine 是"按 key 合并 value"——超实用
    val m1 = Map("a" -> 1, "b" -> 2)
    val m2 = Map("b" -> 10, "c" -> 5)
    println(s"  Map    : ${m1 |+| m2}")  // Map(a -> 1, b -> 12, c -> 5) ← b 的 1+10=12 自动！

    // Option 的 combine：Some(1) |+| Some(2) = Some(1+2)，None 是 zero
    println(s"  Option : ${Option(1) |+| Option(2) |+| None}") // Some(3)
  }

  // ============================================================
  // 2. Monoid —— Semigroup + 零元
  //    主要好处：可以泛型地写 sum / fold
  // ============================================================
  def demo_Monoid(): Unit = {
    println("\n===== 2. Monoid（empty + combineAll）=====")

    // 任何 Monoid 都能做 combineAll（即"求和"）
    println(s"  combineAll Int  : ${List(1, 2, 3, 4, 5).combineAll}")
    println(s"  combineAll Str  : ${List("a", "b", "c").combineAll}")

    // ★ 真实业务场景：合并多份配置
    case class Config(features: Map[String, Int], tags: List[String])
    object Config {
      // 一行：声明 Config 也是 Monoid（基于字段的 Monoid）
      given Monoid[Config] = new Monoid[Config] {
        def empty: Config = Config(Map.empty, Nil)
        def combine(x: Config, y: Config): Config =
          Config(x.features |+| y.features, x.tags |+| y.tags)
      }
    }

    val configs = List(
      Config(Map("dark" -> 1), List("v1")),
      Config(Map("beta" -> 1), List("admin")),
      Config(Map("dark" -> 2), List("admin"))   // dark 计数会被累加
    )
    val merged = configs.combineAll
    println(s"  合并配置        : $merged")
  }

  // ============================================================
  // 3. Functor —— 能 map
  //    所有"能装东西的容器"都是 Functor：List / Option / Either / Future / IO ...
  // ============================================================
  def demo_Functor(): Unit = {
    println("\n===== 3. Functor（统一的 map）=====")

    // 写一个对"任何 Functor"通用的方法
    def addOne[F[_]: Functor](fa: F[Int]): F[Int] =
      fa.map(_ + 1)

    println(s"  addOne(List)   : ${addOne(List(1, 2, 3))}")
    println(s"  addOne(Option) : ${addOne(Option(10))}")
    val rightVal: Either[String, Int] = Right(42)
    println(s"  addOne(Either) : ${addOne(rightVal)}")
    // ★ 同一个 addOne 函数适用于所有“能装东西”的类型
    //   这就是为什么 Cats 能写出那么多“通用”的工具函数
  }

  // ============================================================
  // 4. Monad —— flatMap + pure，for-comprehension 的灵魂
  // ============================================================
  def demo_Monad(): Unit = {
    println("\n===== 4. Monad（统一的 flatMap）=====")

    // 任何 Monad 都能用 for-comprehension
    def addPair[F[_]: Monad](fa: F[Int], fb: F[Int]): F[Int] =
      for {
        a <- fa
        b <- fb
      } yield a + b

    println(s"  addPair(Option) : ${addPair(Option(1), Option(2))}")
    println(s"  addPair(List)   : ${addPair(List(1, 2), List(10, 20))}")  // 笛卡尔积
    val ra: Either[String, Int] = Right(1)
    val rb: Either[String, Int] = Right(2)
    println(s"  addPair(Either) : ${addPair(ra, rb)}")

    // ★ 同一段 for-comprehension 业务逻辑，
    //   用 Option 跑就是"短路"，用 List 跑就是"笛卡尔积"，
    //   用 Future 跑就是"异步"，用 IO 跑就是"副作用安全"
    //   ── 这就是 Monad 的力量
  }

  // ============================================================
  // 5. ★ 高潮：把以上抽象组合起来
  //    定义一个对"任何 Monad + 任何 Monoid"通用的算法
  // ============================================================
  def demo_Combination(): Unit = {
    println("\n===== 5. 抽象组合：Monad + Monoid =====")

    // 给一个 List[F[A]]：拿到所有 F 里的 A，combine 起来
    //   要求：F 是 Monad（能 flatMap），A 是 Monoid（能 combine + empty）
    def sumF[F[_]: Monad, A: Monoid](xs: List[F[A]]): F[A] =
      xs.foldLeft(Monoid[A].empty.pure[F]) { (accF, fa) =>
        for {
          acc <- accF
          a   <- fa
        } yield acc |+| a
      }

    // 用同一个 sumF：
    val opts = List(Option(1), Option(2), Option(3))
    println(s"  sumF(Option[Int]) : ${sumF(opts)}")          // Some(6)

    val lst = List(List(1, 2), List(10, 20))
    println(s"  sumF(List[Int])   : ${sumF(lst)}")           // 笛卡尔积所有 sum

    val withNone = List(Option(1), Option.empty[Int], Option(3))
    println(s"  sumF(有 None)      : ${sumF(withNone)}")     // None ← 短路

    println("  ★ 一段代码同时支持\"短路\"、\"异步\"、\"笛卡尔积\"等多种语义")
    println("    取决于你给 F 选哪个 Monad —— 这就是 Cats 的核心设计")
  }

  def main(args: Array[String]): Unit = {
    demo_Semigroup()
    demo_Monoid()
    demo_Functor()
    demo_Monad()
    demo_Combination()
    println("\n===== Scene04 完成 =====")
  }
}
