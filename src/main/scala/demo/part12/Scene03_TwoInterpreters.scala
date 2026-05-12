package demo.part12

import cats.Monad
import cats.data.{Chain, EitherT, Kleisli, ReaderT, StateT, WriterT}
import cats.effect.{IO, IOApp, Ref}
import cats.mtl.*
import cats.syntax.all.*

/**
 * ============================================================
 * Scene 03: cats-mtl 工业实战 —— 同一业务，两套 Interpreter
 *
 *   业务：用户下单 → 校验 → 应用折扣 → 更新库存 → 落流水
 *   涉及的能力：
 *     - Ask[F, AppConfig]     读全局配置
 *     - Stateful[F, Inventory] 维护库存
 *     - Tell[F, Chain[Event]]  追加事件流水
 *     - Raise[F, DomainError]  业务错误短路
 *     - Monad（for-comprehension 基础）
 *
 *   Interpreter A —— 教学型：Monad Transformer 栈
 *     EitherT[WriterT[StateT[Kleisli[IO, AppConfig, *], Inventory, *], Chain[Event], *], DomainError, *]
 *     全部用 cats-mtl 预装的 given 实例，业务代码不变。
 *
 *   Interpreter B —— 生产型：直接用 IO + Ref（零 transformer）
 *     自己实现 Ask / Stateful / Tell / Raise 的 IO-level instance。
 *     性能最好，线上更常用。
 *
 *   ★ 同一个业务函数跑两套 interpreter，结果一致 —— 这才是 cats-mtl 最大价值。
 * ============================================================
 */
object Scene03_TwoInterpreters extends IOApp.Simple {

  // ============================================================
  // 业务模型
  // ============================================================
  case class AppConfig(memberDiscount: BigDecimal, vipDiscount: BigDecimal)

  case class Sku(id: String, price: BigDecimal)
  case class Inventory(stock: Map[String, Int]) {
    def decrement(sku: String, qty: Int): Option[Inventory] =
      stock.get(sku).filter(_ >= qty).map(_ - qty).map(n => Inventory(stock.updated(sku, n)))
  }

  sealed trait Event
  case class OrderPlaced(userId: String, sku: String, qty: Int, finalPrice: BigDecimal) extends Event
  case class StockChanged(sku: String, remaining: Int) extends Event

  sealed trait DomainError extends Product with Serializable
  case class  OutOfStock(sku: String)      extends DomainError
  case class  UnknownSku(sku: String)      extends DomainError
  case class  BadQuantity(qty: Int)        extends DomainError

  enum UserTier { case Normal, Member, Vip }

  // ============================================================
  // ① 业务函数：完全对 F 多态，只声明需要的能力
  // ============================================================
  def placeOrder[F[_]: Monad](userId: String, tier: UserTier, sku: Sku, qty: Int)(using
      ask:   Ask[F, AppConfig],
      inv:   Stateful[F, Inventory],
      tell:  Tell[F, Chain[Event]],
      raise: Raise[F, DomainError]
  ): F[BigDecimal] = for {
    _         <- if (qty <= 0) raise.raise(BadQuantity(qty)): F[Unit] else Monad[F].unit

    cfg       <- ask.ask
    inventory <- inv.get

    remaining <- inventory.stock.get(sku.id) match {
                   case None    => raise.raise(UnknownSku(sku.id)): F[Int]
                   case Some(n) => Monad[F].pure(n)
                 }
    _         <- if (remaining < qty) raise.raise(OutOfStock(sku.id)): F[Unit]
                 else                  Monad[F].unit

    discount   = tier match {
                   case UserTier.Normal => BigDecimal(0)
                   case UserTier.Member => cfg.memberDiscount
                   case UserTier.Vip    => cfg.vipDiscount
                 }
    finalPrice = sku.price * BigDecimal(qty) * (BigDecimal(1) - discount)

    // 更新库存
    newInv     = inventory.decrement(sku.id, qty).get  // 前面已校验过库存
    _         <- inv.set(newInv)

    // 追加事件流水
    _         <- tell.tell(Chain(
                   OrderPlaced(userId, sku.id, qty, finalPrice),
                   StockChanged(sku.id, newInv.stock(sku.id))
                 ))
  } yield finalPrice

  // ============================================================
  // ② 组合出一段"完整业务"（两笔成功 + 一笔失败）
  // ============================================================
  def program[F[_]: Monad](using
      Ask[F, AppConfig], Stateful[F, Inventory], Tell[F, Chain[Event]], Raise[F, DomainError]
  ): F[List[BigDecimal]] = for {
    a <- placeOrder("U001", UserTier.Member, Sku("SKU-A", BigDecimal(100)), 2)
    b <- placeOrder("U002", UserTier.Vip,    Sku("SKU-B", BigDecimal(50)),  3)
    // 库存是 5，再买 10 会 OutOfStock → 短路
    c <- placeOrder("U003", UserTier.Normal, Sku("SKU-A", BigDecimal(100)), 10)
  } yield List(a, b, c)

  // ============================================================
  // ③ Interpreter A：Monad Transformer 栈 + cats-mtl 预装 given
  // ============================================================
  object InterpreterA {
    type R[A] = Kleisli[IO, AppConfig, A]
    type S[A] = StateT[R, Inventory, A]
    type W[A] = WriterT[S, Chain[Event], A]
    type App[A] = EitherT[W, DomainError, A]

    def run[A](
        app: App[A], cfg: AppConfig, init: Inventory
    ): IO[(Inventory, Chain[Event], Either[DomainError, A])] = {
      val s1: W[Either[DomainError, A]]                         = app.value
      val s2: S[(Chain[Event], Either[DomainError, A])]         = s1.run
      val s3: R[(Inventory, (Chain[Event], Either[DomainError, A]))] = s2.run(init)
      s3.run(cfg).map { case (inv, (evts, res)) => (inv, evts, res) }
    }
  }

  // ============================================================
  // ④ Interpreter B：IO + Ref —— 手写 4 个 cats-mtl instance
  //
  //    生产环境通常这样：不用 transformer，性能和调试都更好。
  //    只要 F = IO 时提供 Ask/Stateful/Tell/Raise 的 given 即可。
  // ============================================================
  object InterpreterB {

    // Raise：短路用 IO.raiseError + 我们的自定义异常包装器
    private case class DomainErr(err: DomainError) extends RuntimeException(err.toString)

    given raiseIO: Raise[IO, DomainError] with {
      def functor = cats.Functor[IO]
      def raise[E2 <: DomainError, A](e: E2): IO[A] = IO.raiseError(DomainErr(e))
    }

    // 构造器：需要 cfg + 两个 Ref 才能 wire 所有 given
    def make(
        cfg: AppConfig,
        inventoryRef: Ref[IO, Inventory],
        eventsRef:    Ref[IO, Chain[Event]]
    ): (Ask[IO, AppConfig], Stateful[IO, Inventory], Tell[IO, Chain[Event]]) = {
      val ask = new Ask[IO, AppConfig] {
        def applicative = cats.Applicative[IO]
        def ask[E2 >: AppConfig]: IO[E2] = IO.pure(cfg)
      }

      val stateful = new Stateful[IO, Inventory] {
        def monad = cats.Monad[IO]
        def get: IO[Inventory] = inventoryRef.get
        def set(s: Inventory): IO[Unit] = inventoryRef.set(s)
      }

      val tell = new Tell[IO, Chain[Event]] {
        def functor = cats.Functor[IO]
        def tell(l: Chain[Event]): IO[Unit] = eventsRef.update(_ |+| l)
      }

      (ask, stateful, tell)
    }

    def run(cfg: AppConfig, init: Inventory): IO[(Inventory, Chain[Event], Either[DomainError, List[BigDecimal]])] =
      for {
        invRef <- Ref.of[IO, Inventory](init)
        evRef  <- Ref.of[IO, Chain[Event]](Chain.empty)
        wired  = make(cfg, invRef, evRef)
        // 把 3 个 given 安装到作用域里
        result <- {
          given Ask[IO, AppConfig]         = wired._1
          given Stateful[IO, Inventory]    = wired._2
          given Tell[IO, Chain[Event]]     = wired._3
          // Raise 用的是 object 里的 given
          program[IO].attempt.map {
            case Right(v)                 => Right(v)
            case Left(DomainErr(e))       => Left(e)
            case Left(other)              => throw other   // 非业务异常直接传出
          }
        }
        finalInv <- invRef.get
        finalEv  <- evRef.get
      } yield (finalInv, finalEv, result)
  }

  // ============================================================
  // ⑤ 演示：两个 interpreter 跑同一个 program
  // ============================================================
  override def run: IO[Unit] = {
    val cfg  = AppConfig(
      memberDiscount = BigDecimal("0.10"),
      vipDiscount    = BigDecimal("0.20")
    )
    val init = Inventory(Map("SKU-A" -> 5, "SKU-B" -> 10))

    for {
      _ <- IO.println("===== Scene03: 两套 Interpreter 跑同一业务 =====\n")

      // Interpreter A
      _ <- IO.println("【Interpreter A】Monad Transformer 栈 + cats-mtl 预装 given")
      tA    <- InterpreterA.run(program[InterpreterA.App], cfg, init)
      (invA, evA, resA) = tA
      _ <- IO.println(s"  结果:   $resA")
      _ <- IO.println(s"  库存:   $invA")
      _ <- IO.println(s"  事件(${evA.length}):")
      _ <- evA.toList.traverse(e => IO.println(s"    - $e"))

      // Interpreter B
      _ <- IO.println("\n【Interpreter B】直接 IO + Ref（手写 mtl instance）")
      tB    <- InterpreterB.run(cfg, init)
      (invB, evB, resB) = tB
      _ <- IO.println(s"  结果:   $resB")
      _ <- IO.println(s"  库存:   $invB")
      _ <- IO.println(s"  事件(${evB.length}):")
      _ <- evB.toList.traverse(e => IO.println(s"    - $e"))

      // 断言：两种 Interpreter 得到完全一致的业务结果
      _ <- IO.println("\n  ★ 两次运行的业务结果完全一致：")
      _ <- IO.println(s"    结果一致? ${resA == resB}")
      _ <- IO.println(s"    库存一致? ${invA == invB}")
      _ <- IO.println(s"    事件一致? ${evA.toList == evB.toList}")

      _ <- IO.println(
             """
               |  ★ 关键观察：
               |    1. placeOrder / program 一行没改，跑了两种截然不同的实现
               |    2. Interpreter A 适合"快速搭原型、全部纯函数式"
               |    3. Interpreter B 适合"生产环境、关心性能和调试"
               |    4. 想加审计、tracing？在 using 里加一个能力即可
               |    5. 这就是 cats-mtl 的核心价值：让 Tagless Final 更进一步
               |""".stripMargin)
      _ <- IO.println("===== Scene03 完成 =====")
    } yield ()
  }
}
