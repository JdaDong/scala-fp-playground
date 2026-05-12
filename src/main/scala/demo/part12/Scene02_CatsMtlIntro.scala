package demo.part12

import cats.Monad
import cats.data.{EitherT, ReaderT, StateT, WriterT}
import cats.effect.{IO, IOApp}
import cats.mtl.*
import cats.syntax.all.*

/**
 * ============================================================
 * Scene 02: cats-mtl 核心 type class 一览
 *
 *   cats-mtl 把"拥有某种效果能力"抽象成 6 个核心 type class：
 *
 *   ┌──────────────┬─────────────────────────────────────────┐
 *   │ Type Class   │ 能力                                    │
 *   ├──────────────┼─────────────────────────────────────────┤
 *   │ Ask[F, R]    │ 读配置/环境（Reader）                   │
 *   │ Local[F, R]  │ Ask + 在作用域内临时修改 R（Reader）    │
 *   │ Tell[F, L]   │ 向日志/累加器写入（Writer）             │
 *   │ Stateful[F, S]│ 读写状态（State）                      │
 *   │ Raise[F, E]  │ 抛错误（Either 的短路）                 │
 *   │ Handle[F, E] │ Raise + 捕获错误                        │
 *   └──────────────┴─────────────────────────────────────────┘
 *
 *   有了它们之后，业务签名变成：
 *
 *     def f[F[_]: Monad: Ask[*, Config]: Stateful[*, Stats]
 *                      : Tell[*, List[String]]: Raise[*, AppError]](...)
 *       : F[...]
 *
 *   ✓ 不再需要知道具体的 Monad Transformer 栈
 *   ✓ 不需要手动 lift
 *   ✓ 能力直接用（cfg <- Ask[F, Config].ask，不需要 liftF）
 *   ✓ 换栈顺序 / 加新能力 → 业务代码零修改
 *
 *   本场景把 Scene01 的"订单处理"用 cats-mtl 重写一遍对比。
 * ============================================================
 */
object Scene02_CatsMtlIntro extends IOApp.Simple {

  // ============================================================
  // 业务模型（和 Scene01 一样）
  // ============================================================
  case class Config(discount: BigDecimal, minOrder: BigDecimal)
  case class Stats(processed: Int, totalAmount: BigDecimal)

  sealed trait AppError
  case object OrderTooSmall extends AppError
  case class  InvalidItem(name: String) extends AppError

  // ============================================================
  // ① 业务函数：只声明"我需要哪些能力"，不关心具体栈
  //
  //    写法 a：用 context bound（紧凑）
  //    写法 b：用 using 参数（清晰）——这里用 b
  // ============================================================
  def processOrder[F[_]: Monad](name: String, amount: BigDecimal)(using
      ask:   Ask[F, Config],
      stat:  Stateful[F, Stats],
      tell:  Tell[F, List[String]],
      raise: Raise[F, AppError]
  ): F[BigDecimal] = for {
    cfg   <- ask.ask                                     // ← 直接读，没 lift！
    _     <- tell.tell(List(s"处理订单 $name 金额=$amount 最低=${cfg.minOrder}"))

    // 校验
    _     <- if (name.isEmpty)                raise.raise(InvalidItem(name)): F[Unit]
             else if (amount < cfg.minOrder)  raise.raise(OrderTooSmall):     F[Unit]
             else                              Monad[F].unit

    finalAmt = amount * (BigDecimal(1) - cfg.discount)

    _     <- stat.modify(s => Stats(s.processed + 1, s.totalAmount + finalAmt))
    _     <- tell.tell(List(s"  折后金额=$finalAmt"))
  } yield finalAmt

  // ============================================================
  // ② 组合：一段完整的业务程序，也是泛型
  // ============================================================
  def program[F[_]: Monad](using
      Ask[F, Config], Stateful[F, Stats], Tell[F, List[String]], Raise[F, AppError]
  ): F[List[BigDecimal]] = for {
    a <- processOrder("Book",   BigDecimal(80))
    b <- processOrder("Pencil", BigDecimal(100))
    c <- processOrder("Sticker", BigDecimal(10))     // 会短路
  } yield List(a, b, c)

  // ============================================================
  // ③ 在 main 里"装配"：挑一个具体的栈
  //    这里用：EitherT[WriterT[StateT[ReaderT[IO,Config,*], Stats, *], List[String], *], AppError, *]
  //    关键：cats-mtl 已经为这些 transformer 预装了 Ask/Stateful/Tell/Raise 的 given 实例
  // ============================================================
  type R[A] = ReaderT[IO, Config, A]
  type S[A] = StateT[R, Stats, A]
  type W[A] = WriterT[S, List[String], A]
  type App[A] = EitherT[W, AppError, A]

  def runApp[A](app: App[A], cfg: Config, init: Stats): IO[(Stats, List[String], Either[AppError, A])] = {
    // 从外向内"剥皮"
    val step1: W[Either[AppError, A]]               = app.value
    val step2: S[(List[String], Either[AppError, A])] = step1.run
    val step3: R[(Stats, (List[String], Either[AppError, A]))] = step2.run(init)
    val step4: IO[(Stats, (List[String], Either[AppError, A]))] = step3.run(cfg)
    step4.map { case (s, (logs, r)) => (s, logs, r) }
  }

  // ============================================================
  // ④ 演示
  // ============================================================
  override def run: IO[Unit] = {
    val cfg  = Config(discount = BigDecimal("0.1"), minOrder = BigDecimal("50"))
    val init = Stats(0, BigDecimal(0))

    // 关键魔法：program[App] —— 编译器自动找到 App 对应的 Ask/Stateful/Tell/Raise 实例
    val app: App[List[BigDecimal]] = program[App]

    for {
      _                    <- IO.println("===== Scene02: cats-mtl 优雅解法 =====\n")
      _                    <- IO.println("  业务签名（无栈，纯 type class）：")
      _                    <- IO.println("    def processOrder[F[_]: Monad](name, amount)(using")
      _                    <- IO.println("        Ask[F, Config], Stateful[F, Stats],")
      _                    <- IO.println("        Tell[F, List[String]], Raise[F, AppError]): F[BigDecimal]\n")

      triple               <- runApp(app, cfg, init)
      (stats, logs, res)   =  triple

      _                    <- IO.println("  收集到的日志：")
      _                    <- logs.traverse(s => IO.println(s"    $s"))
      _                    <- IO.println(s"\n  最终状态: $stats")
      _                    <- IO.println(s"  最终结果: $res")

      _                    <- IO.println(
                                """
                                  |  ★ 和 Scene01 对比：
                                  |    1. processOrder 内部干干净净，没有 lift / EitherT.leftT / StateT.liftF
                                  |    2. 能力声明直接跟在 [F[_]] 后面，类型签名一目了然
                                  |    3. 想加"审计"能力？在 using 里加一个 Tell[F, AuditEvent] 即可
                                  |    4. 想把 WriterT 换成 IORef[List]？只改 runApp，业务零修改
                                  |""".stripMargin)
      _                    <- IO.println("===== Scene02 完成 =====")
    } yield ()
  }
}
