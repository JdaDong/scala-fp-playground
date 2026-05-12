package demo.part08

import cats.{Monad, MonadError}
import cats.effect.{IO, IOApp, Ref}
import cats.effect.std.Console
import cats.syntax.all.*

/**
 * ============================================================
 * Scene 03: Tagless Final 工业实战 —— 一个完整的"小银行"
 *
 *   业务：转账 transfer(from, to, amount)
 *
 *   涉及多个能力（algebra）：
 *     - AccountRepo[F[_]]   账户读写
 *     - Logger[F[_]]        日志
 *     - Clock[F[_]]          取当前时间（用于交易记录）
 *
 *   工业级 tagless 的"约束栈"：
 *     def transfer[F[_]: MonadError[*, Throwable]: Logger: Clock](...)
 *
 *   一个方法上叠加多个 type class 约束 →
 *     "我需要 Monad 能力 + 错误处理能力 + 日志能力 + 时间能力"
 *     调用方在 main 里给 F 装配满足这些约束的 instance 即可。
 *
 *   ★ http4s / doobie / skunk 等真实库的 API 长得就是这样。
 * ============================================================
 */
object Scene03_TaglessIndustrial extends IOApp.Simple {

  // ============================================================
  // 业务模型
  // ============================================================
  case class AccountId(value: String)
  case class Account(id: AccountId, balance: BigDecimal)

  // 业务异常（错误是值，不抛）
  sealed trait BankError extends Throwable
  case object InsufficientFunds extends BankError { override def getMessage = "余额不足" }
  case class  AccountNotFound(id: AccountId) extends BankError {
    override def getMessage = s"账户不存在: ${id.value}"
  }

  // ============================================================
  // ① Algebra：3 种能力契约
  // ============================================================
  trait AccountRepo[F[_]] {
    def find(id: AccountId): F[Option[Account]]
    def save(a: Account): F[Unit]
  }

  trait Logger[F[_]] {
    def info(msg: String): F[Unit]
    def warn(msg: String): F[Unit]
  }

  trait Clock[F[_]] {
    def now(): F[Long]   // unix ms
  }

  // ============================================================
  // ② 业务函数 —— 多个 type class 约束叠加
  //    用 context bound 简洁声明能力需求
  // ============================================================
  class BankService[F[_]](using
      M: MonadError[F, Throwable],
      repo: AccountRepo[F],
      log: Logger[F],
      clk: Clock[F]
  ) {

    // 取账户，找不到就抛 BankError（在 F 里）
    private def loadOrFail(id: AccountId): F[Account] =
      repo.find(id).flatMap {
        case Some(a) => M.pure(a)
        case None    => M.raiseError(AccountNotFound(id))
      }

    def transfer(from: AccountId, to: AccountId, amount: BigDecimal): F[Unit] = for {
      ts    <- clk.now()
      _     <- log.info(s"[$ts] 开始转账 ${from.value} → ${to.value} 金额=$amount")

      a     <- loadOrFail(from)
      b     <- loadOrFail(to)

      _     <- if (a.balance < amount) log.warn(s"[$ts] 余额不足") *> M.raiseError[Unit](InsufficientFunds)
               else M.unit

      _     <- repo.save(a.copy(balance = a.balance - amount))
      _     <- repo.save(b.copy(balance = b.balance + amount))
      _     <- log.info(s"[$ts] 转账成功")
    } yield ()
  }

  // ============================================================
  // ③ Interpreter for IO （using Ref 做内存账本）
  // ============================================================
  def makeIORepo(initial: Map[AccountId, Account]): IO[AccountRepo[IO]] =
    Ref.of[IO, Map[AccountId, Account]](initial).map { ref =>
      new AccountRepo[IO] {
        def find(id: AccountId): IO[Option[Account]] = ref.get.map(_.get(id))
        def save(a: Account): IO[Unit]               = ref.update(_.updated(a.id, a))
      }
    }

  given ioLogger: Logger[IO] with {
    def info(msg: String): IO[Unit] = Console[IO].println(s"    [INFO]  $msg")
    def warn(msg: String): IO[Unit] = Console[IO].println(s"    [WARN]  $msg")
  }

  given ioClock: Clock[IO] with {
    def now(): IO[Long] = IO.realTime.map(_.toMillis)
  }

  // ============================================================
  // ④ 演示
  // ============================================================
  override def run: IO[Unit] = {
    val a1 = AccountId("ACC-001")
    val a2 = AccountId("ACC-002")
    val a3 = AccountId("ACC-999")  // 故意一个不存在的

    val initial = Map(
      a1 -> Account(a1, BigDecimal(100)),
      a2 -> Account(a2, BigDecimal(50))
    )

    for {
      _    <- IO.println("===== Scene03: Tagless Final 工业实战 =====")

      repo <- makeIORepo(initial)
      // 给作用域安装 implicit 实例
      given AccountRepo[IO] = repo
      bank = BankService[IO]

      _    <- IO.println("\n【场景 1】成功转账 30")
      r1   <- bank.transfer(a1, a2, 30).attempt
      _    <- IO.println(s"    结果：$r1")

      _    <- IO.println("\n【场景 2】余额不足（再转 200 必失败）")
      r2   <- bank.transfer(a1, a2, 200).attempt
      _    <- IO.println(s"    结果：$r2")

      _    <- IO.println("\n【场景 3】账户不存在")
      r3   <- bank.transfer(a3, a1, 10).attempt
      _    <- IO.println(s"    结果：$r3")

      _    <- IO.println("\n  最终账本：")
      // 直接读 ref：再装一个 IO repo 不好取出 ref，简单读全表
      _    <- repo.find(a1).flatMap(x => IO.println(s"    $a1 → $x"))
      _    <- repo.find(a2).flatMap(x => IO.println(s"    $a2 → $x"))

      _    <- IO.println(
                """
                  |  ★ 工业级亮点：
                  |    1. transfer 一个方法上叠了 4 个约束（Monad/Error/Logger/Clock）
                  |       每多一个能力就在 using 里加一行
                  |    2. 测试时 mock 任意一个能力（比如 Clock 永远返回 0）轻松搞定
                  |    3. 业务代码看起来像同步、清晰可读，但全是异步、可失败的 effect
                  |    4. 这就是 http4s / doobie 库的设计模式
                  |""".stripMargin)
      _    <- IO.println("===== Scene03 完成 =====")
    } yield ()
  }
}
