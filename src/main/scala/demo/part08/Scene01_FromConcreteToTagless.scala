package demo.part08

import cats.Monad
import cats.effect.{IO, IOApp}
import cats.syntax.all.*

/**
 * ============================================================
 * Scene 01: 从"具体实现"到 Tagless Final —— 业务和效果彻底解耦
 *
 *   传统写法的问题：业务代码直接耦合具体的"效果类型"（IO / Future）
 *     1. 测试难写  → 必须在测试里跑真实 IO
 *     2. 切换难    → 想从 IO 换 ZIO？把所有代码改一遍
 *     3. 推理难    → 一段代码同时混着多种副作用
 *
 *   Tagless Final 的核心思想：
 *     "把业务代码写成 ∀F[_]: Monad 的多态函数，
 *      到 main 函数末尾才挑一个具体的 F"
 *
 *   ★ 这是工业级 Scala 后端（http4s / doobie / skunk）的事实标准。
 * ============================================================
 */
object Scene01_FromConcreteToTagless extends IOApp.Simple {

  // ============================================================
  // ① 业务模型
  // ============================================================
  case class User(id: Long, name: String, email: String)

  // ============================================================
  // ② 写法 A：耦合 IO —— 业务代码里全是 IO
  //   缺点：UserService 直接依赖 cats.effect.IO，没法换
  // ============================================================
  object Approach_A_ConcreteIO {
    class UserRepoIO {
      def findById(id: Long): IO[Option[User]] =
        IO.pure(if (id == 1) Some(User(1, "Alice", "a@x.com")) else None)

      def save(u: User): IO[Unit] =
        IO.println(s"    [IO-Repo] saved: $u")
    }

    class UserServiceIO(repo: UserRepoIO) {
      // ★ 这个方法返回 IO[String]，永远绑死了
      def greet(id: Long): IO[String] = for {
        opt <- repo.findById(id)
      } yield opt.map(u => s"Hello, ${u.name}!").getOrElse("Stranger")
    }

    def demo: IO[Unit] = for {
      _   <- IO.println("【写法 A】具体 IO（耦合）")
      svc =  UserServiceIO(UserRepoIO())
      m1  <- svc.greet(1)
      m2  <- svc.greet(99)
      _   <- IO.println(s"    $m1 / $m2")
    } yield ()
  }

  // ============================================================
  // ③ 写法 B：Tagless Final —— 业务代码与 F 完全解耦
  //
  //   3 步走：
  //     Step 1: 把"能力"定义成 type class（algebra）
  //     Step 2: 业务用 F[_]: Monad 写，对 F 完全多态
  //     Step 3: 在 main 里 wire 一个具体 F（这里选 IO）
  // ============================================================
  object Approach_B_Tagless {

    // —— Step 1: Algebra（能力契约，不知道 F 是什么）——
    trait UserRepo[F[_]] {
      def findById(id: Long): F[Option[User]]
      def save(u: User): F[Unit]
    }

    // —— Step 2: Service 完全多态 ——
    //   注意签名里出现了 [F[_]: Monad] —— 这是 tagless final 的招牌
    class UserService[F[_]: Monad](repo: UserRepo[F]) {
      def greet(id: Long): F[String] = for {
        opt <- repo.findById(id)
      } yield opt.map(u => s"Hello, ${u.name}!").getOrElse("Stranger")

      // 复杂逻辑也照写不误，对 F 一无所知
      def greetAndLog(id: Long, log: String => F[Unit]): F[String] = for {
        msg <- greet(id)
        _   <- log(s"[audit] greet($id) = $msg")
      } yield msg
    }

    // —— Step 3: Interpreter（具体 F = IO 时怎么实现）——
    object UserRepoIOInterpreter extends UserRepo[IO] {
      def findById(id: Long): IO[Option[User]] =
        IO.pure(if (id == 1) Some(User(1, "Alice", "a@x.com")) else None)

      def save(u: User): IO[Unit] =
        IO.println(s"    [Tagless-Repo-IO] saved: $u")
    }

    def demo: IO[Unit] = {
      // 在 main 里"装配"：F = IO
      val repo: UserRepo[IO] = UserRepoIOInterpreter
      val svc                = UserService[IO](repo)

      for {
        _   <- IO.println("\n【写法 B】Tagless Final（解耦）")
        m1  <- svc.greetAndLog(1,  m => IO.println(s"    $m"))
        m2  <- svc.greetAndLog(99, m => IO.println(s"    $m"))
        _   <- IO.println(s"    返回值：$m1 / $m2")
      } yield ()
    }
  }

  // ============================================================
  // ④ 关键观察：Service 没出现过"IO"这个词
  // ============================================================
  def keyInsight: IO[Unit] = IO.println(
    """
      |  ★ 关键观察：
      |    1. Approach_B 的 UserService 完全没出现过 IO 这个词
      |       → 它能在任何 Monad 里运行（IO / EitherT / 测试用的 Id / ZIO）
      |    2. 业务作者只关心"逻辑"，不关心"执行环境"
      |    3. 测试时可以用纯净的 Id Monad（同步、无副作用），秒级单测
      |    4. 切换运行时（IO ↔ ZIO ↔ Future）只改 Interpreter，业务零修改
      |""".stripMargin)

  // ============================================================
  // 入口
  // ============================================================
  override def run: IO[Unit] = for {
    _ <- IO.println("===== Scene01: From Concrete to Tagless =====\n")
    _ <- Approach_A_ConcreteIO.demo
    _ <- Approach_B_Tagless.demo
    _ <- keyInsight
    _ <- IO.println("===== Scene01 完成 =====")
  } yield ()
}
