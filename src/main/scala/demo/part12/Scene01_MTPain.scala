package demo.part12

import cats.Monad
import cats.data.{EitherT, ReaderT, StateT, WriterT}
import cats.effect.{IO, IOApp}
import cats.syntax.all.*

/**
 * ============================================================
 * Scene 01: Monad Transformer 的痛点 —— 为什么需要 cats-mtl
 *
 *   真实业务往往同时需要多种"效果能力"：
 *     - 读配置        → Reader
 *     - 维护状态      → State
 *     - 累积日志      → Writer
 *     - 短路错误      → Either
 *     - 异步执行      → IO
 *
 *   传统做法：用 Monad Transformer 把它们"叠"起来：
 *     type App[A] = EitherT[StateT[ReaderT[IO, Config, *], Counter, *], Err, A]
 *
 *   写业务时的 3 大痛点：
 *     ① 类型签名恶心长
 *     ② 每一层要用对应的 lift 才能访问（ReaderT.ask、StateT.get、EitherT.leftT...）
 *     ③ 想换一层的顺序/增加一层 → 所有代码签名都要改
 *
 *   cats-mtl 的答案：
 *     用 type class 抽象"拥有某种能力"，不再关心具体的 transformer 栈。
 *     业务代码写 [F[_]: Ask: Stateful: Tell: Raise]，调用方在 main 挑具体类型。
 *
 *   本场景先展示"痛点"：真实写一个 Monad Transformer 栈的业务函数。
 *   Scene02/03 再展示 cats-mtl 如何优雅解决。
 * ============================================================
 */
object Scene01_MTPain extends IOApp.Simple {

  // ============================================================
  // 业务模型
  // ============================================================
  case class Config(discount: BigDecimal, minOrder: BigDecimal)
  case class Stats(processed: Int, totalAmount: BigDecimal)

  sealed trait AppError
  case object OrderTooSmall extends AppError
  case class  InvalidItem(name: String) extends AppError

  // ============================================================
  // ① "手写 Monad Transformer 栈" —— 真实代码长什么样
  //    栈的顺序从外到内：EitherT → StateT → ReaderT → IO
  //    读法："先跑 IO，注入 Config，维护 Stats 状态，允许抛 AppError"
  // ============================================================
  type Stack0[A] = ReaderT[IO, Config, A]              // 最内层：IO + 读 Config
  type Stack1[A] = StateT[Stack0, Stats, A]            // + 状态
  type App[A]    = EitherT[Stack1, AppError, A]        // + 错误

  // ============================================================
  // ② 在这个栈里写业务 —— 注意每个能力要用"对应的 lift"
  // ============================================================

  // 读配置（在 ReaderT 那一层）
  val readConfig: App[Config] =
    EitherT.liftF(StateT.liftF(ReaderT.ask[IO, Config]))

  // 读状态（在 StateT 那一层）
  val readStats: App[Stats] =
    EitherT.liftF(StateT.get[Stack0, Stats])

  // 更新状态
  def updateStats(f: Stats => Stats): App[Unit] =
    EitherT.liftF(StateT.modify[Stack0, Stats](f))

  // 打日志（IO 层，需要穿透 3 层）
  def log(msg: String): App[Unit] =
    EitherT.liftF(StateT.liftF(ReaderT.liftF(IO.println(s"    [log] $msg"))))

  // 抛错误
  def raise[A](err: AppError): App[A] =
    EitherT.leftT[Stack1, A](err)

  // ============================================================
  // ③ 业务函数：处理一笔订单
  //    看看这段"本应很简单"的业务，夹杂了多少 lift 噪音
  // ============================================================
  def processOrder(name: String, amount: BigDecimal): App[BigDecimal] = for {
    cfg   <- readConfig
    _     <- log(s"处理订单 $name 金额=$amount 最低=${cfg.minOrder}")

    // 校验
    _     <- if (name.isEmpty)         raise[Unit](InvalidItem(name))
             else if (amount < cfg.minOrder) raise[Unit](OrderTooSmall)
             else                       EitherT.pure[Stack1, AppError](())

    // 计算折后价
    finalAmt = amount * (BigDecimal(1) - cfg.discount)

    // 更新统计
    _     <- updateStats(s => Stats(s.processed + 1, s.totalAmount + finalAmt))
    _     <- log(s"  折后金额=$finalAmt")
  } yield finalAmt

  // ============================================================
  // ④ 运行栈：一层一层"剥"出来
  //    run(...) 要分别提供 Stats 初始值 + Config，最后拿 IO[...]
  // ============================================================
  def runApp[A](app: App[A], cfg: Config, init: Stats): IO[(Stats, Either[AppError, A])] = {
    // 外 → 内：先 EitherT.value，再 StateT.run(init)，最后 ReaderT.run(cfg)
    val step1: Stack1[Either[AppError, A]] = app.value
    val step2: Stack0[(Stats, Either[AppError, A])] = step1.run(init)
    val step3: IO[(Stats, Either[AppError, A])]     = step2.run(cfg)
    step3
  }

  // ============================================================
  // 演示
  // ============================================================
  override def run: IO[Unit] = {
    val cfg  = Config(discount = BigDecimal("0.1"), minOrder = BigDecimal("50"))
    val init = Stats(0, BigDecimal(0))

    // 组合多笔订单
    val program: App[List[BigDecimal]] = for {
      a <- processOrder("Book",   80)
      b <- processOrder("Pencil", 100)
      // 故意塞一笔过小的，会短路
      c <- processOrder("Sticker", 10)
    } yield List(a, b, c)

    for {
      _           <- IO.println("===== Scene01: Monad Transformer 的痛点 =====")
      _           <- IO.println(s"  类型签名：App[A] = EitherT[StateT[ReaderT[IO, Config, *], Stats, *], AppError, A]\n")

      tuple       <- runApp(program, cfg, init)
      (stats, r)  =  tuple

      _           <- IO.println(s"\n  最终状态: $stats")
      _           <- IO.println(s"  最终结果: $r")

      _           <- IO.println(
                       """
                         |  ★ 痛点总结：
                         |    1. 类型签名恶心长（读写都累）
                         |    2. 每个访问都要 EitherT.liftF(StateT.liftF(ReaderT.ask))... —— lift 地狱
                         |    3. raise 要用 EitherT.leftT[Stack1, A]，必须知道栈顺序
                         |    4. 想在 Reader 和 State 之间插一层？所有代码签名都要改
                         |
                         |    cats-mtl 的解药：下一个 Scene 见
                         |""".stripMargin)
      _           <- IO.println("===== Scene01 完成 =====")
    } yield ()
  }
}
