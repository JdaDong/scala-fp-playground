package demo.part08

import cats.{Id, Monad}
import cats.data.{StateT, WriterT}
import cats.effect.{IO, IOApp}
import cats.syntax.all.*

import scala.collection.mutable

/**
 * ============================================================
 * Scene 02: Tagless Final 的"超能力" —— 同一个业务逻辑，4 种解释器
 *
 *   一个 Service 的 algebra：UserRepo[F[_]]
 *
 *   下面我们给它 4 种完全不同的 interpreter：
 *
 *     1) IO 解释器      —— 真实运行（DB / HTTP）
 *     2) Id 解释器      —— 同步、纯函数，单元测试最爱
 *     3) State 解释器   —— 用一个 Map 当 "内存数据库"，可断言状态变化
 *     4) Writer 解释器  —— 收集所有副作用日志（trace 整个调用链）
 *
 *   ★ 业务代码 1 行不改，外部能力却天翻地覆。
 *     这就是 Tagless Final 在工业上最大的卖点：
 *     "测试和生产共用业务代码，互不污染"
 * ============================================================
 */
object Scene02_MultipleInterpreters extends IOApp.Simple {

  // ============================================================
  // 业务模型 + Algebra（与 Scene01 同款）
  // ============================================================
  case class User(id: Long, name: String)

  trait UserRepo[F[_]] {
    def find(id: Long): F[Option[User]]
    def save(u: User): F[Unit]
  }

  // ============================================================
  // ★ 业务 Service —— 完全多态，对 F 一无所知
  // ============================================================
  class UserService[F[_]: Monad](repo: UserRepo[F]) {
    def renameUser(id: Long, newName: String): F[Option[User]] = for {
      opt     <- repo.find(id)
      updated <- opt match {
                   case Some(u) =>
                     val u2 = u.copy(name = newName)
                     repo.save(u2).as(Some(u2))
                   case None    => Monad[F].pure(None)
                 }
    } yield updated
  }

  // ============================================================
  // 解释器 1: IO —— 真实运行（这里用一个 mutable map 模拟 DB）
  // ============================================================
  object Interp_IO {
    val storage: mutable.Map[Long, User] =
      mutable.Map(1L -> User(1, "Alice"), 2L -> User(2, "Bob"))

    val repo: UserRepo[IO] = new UserRepo[IO] {
      def find(id: Long): IO[Option[User]] = IO.delay(storage.get(id))
      def save(u: User): IO[Unit] = IO.delay { storage.update(u.id, u); () }
    }

    def demo: IO[Unit] = for {
      _ <- IO.println("\n【解释器 1】IO（真实运行）")
      svc = UserService[IO](repo)
      r <- svc.renameUser(1, "Alice-V2")
      _ <- IO.println(s"    结果：$r")
      _ <- IO.println(s"    store: $storage")
    } yield ()
  }

  // ============================================================
  // 解释器 2: Id —— 同步、纯函数，单元测试最爱
  //   Id[A] 就是 A 本身（type Id[A] = A）
  //   要求 storage 不可变，所以这里直接写死返回值
  // ============================================================
  object Interp_Id {
    val data: Map[Long, User] = Map(1L -> User(1, "Alice"), 2L -> User(2, "Bob"))

    val repo: UserRepo[Id] = new UserRepo[Id] {
      def find(id: Long): Id[Option[User]] = data.get(id)
      def save(u: User): Id[Unit]          = ()  // Id 没法存状态，仅演示
    }

    def demo: IO[Unit] = IO.delay {
      println("\n【解释器 2】Id（同步纯函数）")
      val svc = UserService[Id](repo)
      val r: Option[User] = svc.renameUser(1, "Alice-Test")  // ← 直接拿到结果，不需要 unsafeRunSync
      println(s"    结果：$r        ← 返回类型直接是 Option[User]")
    }
  }

  // ============================================================
  // 解释器 3: State —— 用 Map 当"内存 DB"，可观察状态变化
  //   StateT[Id, Map[Long, User], A] = "拿一个 Map 进去，吐一个新 Map 和 A 出来"
  // ============================================================
  object Interp_State {
    type DB[A] = StateT[Id, Map[Long, User], A]

    val repo: UserRepo[DB] = new UserRepo[DB] {
      def find(id: Long): DB[Option[User]] = StateT.inspect(_.get(id))
      def save(u: User): DB[Unit]          = StateT.modify(_.updated(u.id, u))
    }

    def demo: IO[Unit] = IO.delay {
      println("\n【解释器 3】State（可观察的内存 DB）")
      val svc = UserService[DB](repo)
      val initial = Map[Long, User](1L -> User(1, "Alice"), 2L -> User(2, "Bob"))
      val (finalDB, result) = svc.renameUser(1, "Alice-State").run(initial)
      println(s"    结果：$result")
      println(s"    最终 DB 状态：$finalDB    ← 整个状态变化过程纯净可控")
    }
  }

  // ============================================================
  // 解释器 4: Writer —— 收集所有调用日志（trace）
  //   WriterT[Id, List[String], A] = "在算 A 的同时累计一个日志 List"
  //   配合 Id：完全同步，结果包含 (logs, value)
  // ============================================================
  object Interp_Writer {
    type Trace[A] = WriterT[Id, List[String], A]

    val data: Map[Long, User] = Map(1L -> User(1, "Alice"), 2L -> User(2, "Bob"))

    val repo: UserRepo[Trace] = new UserRepo[Trace] {
      def find(id: Long): Trace[Option[User]] =
        WriterT.tell[Id, List[String]](List(s"  → find($id)"))
          .as(data.get(id))
      def save(u: User): Trace[Unit] =
        WriterT.tell[Id, List[String]](List(s"  → save($u)"))
    }

    def demo: IO[Unit] = IO.delay {
      println("\n【解释器 4】Writer（trace 调用链）")
      val svc = UserService[Trace](repo)
      val (logs, result) = svc.renameUser(1, "Alice-Trace").run
      println(s"    结果：$result")
      println(s"    完整调用链：")
      logs.foreach(println)
    }
  }

  override def run: IO[Unit] = for {
    _ <- IO.println("===== Scene02: 同一业务，4 种解释器 =====")
    _ <- Interp_IO.demo
    _ <- Interp_Id.demo
    _ <- Interp_State.demo
    _ <- Interp_Writer.demo
    _ <- IO.println(
           """
             |  ★ 关键观察：
             |    UserService 一行未改，仅替换 F：
             |    - F = IO     → 生产环境真实运行
             |    - F = Id     → 单元测试，同步直接断言
             |    - F = State  → 内存 DB 模拟，断言状态变化
             |    - F = Writer → 自动收集所有副作用调用记录
             |    这就是 Tagless Final 的"超能力"
             |""".stripMargin)
    _ <- IO.println("===== Scene02 完成 =====")
  } yield ()
}
