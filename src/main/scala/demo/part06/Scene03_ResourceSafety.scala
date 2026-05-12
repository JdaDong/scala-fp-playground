package demo.part06

import cats.effect.{IO, IOApp, ExitCode, Resource}
import cats.syntax.all.*
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 03: Resource Safety —— 资源即使异常也能正确释放
 *
 *   传统 Java/Scala 的 try-finally：
 *     try { ... } finally { conn.close() }
 *     ── finally 不能组合：3 个资源就是 3 层嵌套，地狱深渊
 *
 *   Scala 的 Using（≈ try-with-resources）：
 *     ── 只解决"同步"资源，对异步 IO 无能为力
 *
 *   IO + Resource 的解法：
 *     ── 资源是值（Resource[IO, A]）
 *     ── 可组合（flatMap / mapN / parZip）
 *     ── 自动释放（成功 / 失败 / 取消 三种情况都释放）
 *     ── 释放顺序是"获取顺序的逆序"（栈式）
 * ============================================================
 */
object Scene03_ResourceSafety extends IOApp {

  // ============================================================
  // 模拟资源：数据库连接、文件、HTTP 客户端
  // ============================================================
  case class Connection(name: String) {
    def query(sql: String): IO[String] =
      IO.println(s"    [$name] 执行 SQL: $sql").as(s"result-of($sql)")
  }

  def acquireConn(name: String): IO[Connection] =
    IO.println(s"    >> 获取连接 $name").as(Connection(name))

  def releaseConn(c: Connection): IO[Unit] =
    IO.println(s"    << 关闭连接 ${c.name}")

  // 把"获取 + 释放"打包成 Resource
  def connection(name: String): Resource[IO, Connection] =
    Resource.make(acquireConn(name))(releaseConn)
    //         ↑ acquire           ↑ release （不管成功/失败/取消都会调用）

  // ============================================================
  // 1. 单资源：等价于 try-finally，但更优雅
  // ============================================================
  val demo_SingleResource: IO[Unit] =
    for {
      _ <- IO.println("===== 1. 单资源：自动释放 =====")
      _ <- connection("db").use { conn =>
             conn.query("SELECT 1")
             // use 块结束 → 自动调用 release
           }
      _ <- IO.println("    （use 结束后连接已关闭）\n")
    } yield ()

  // ============================================================
  // 2. 多资源组合：靠 flatMap，释放顺序自动逆序
  // ============================================================
  val demo_Composition: IO[Unit] = {
    val composed: Resource[IO, (Connection, Connection)] =
      for {
        a <- connection("primary")    // 第 1 个 acquire
        b <- connection("replica")    // 第 2 个 acquire
      } yield (a, b)

    for {
      _ <- IO.println("===== 2. 多资源组合（释放顺序：replica → primary）=====")
      _ <- composed.use { case (a, b) =>
             a.query("UPDATE x") *> b.query("SELECT y")
           }
      _ <- IO.println("    （注意上面打印的关闭顺序是 acquire 的逆序）\n")
    } yield ()
  }

  // ============================================================
  // 3. 异常安全：use 中抛异常，资源也会被释放
  // ============================================================
  val demo_ExceptionSafety: IO[Unit] =
    for {
      _ <- IO.println("===== 3. use 中抛异常，资源仍然释放 =====")
      result <- connection("db-err").use { conn =>
                  conn.query("SELECT 1") *>
                  IO.raiseError[String](new RuntimeException("💥 业务异常"))
                }.attempt
      _ <- IO.println(s"    结果：$result")
      _ <- IO.println("    （即使异常，连接也被关闭了 ↑）\n")
    } yield ()

  // ============================================================
  // 4. 取消安全：use 中被取消，资源也会被释放
  // ============================================================
  val demo_CancelSafety: IO[Unit] = {
    val longTask: IO[Unit] =
      connection("db-cancel").use { conn =>
        conn.query("LONG QUERY") *> IO.sleep(5.seconds)
      }

    for {
      _     <- IO.println("===== 4. use 中被取消，资源仍然释放 =====")
      fiber <- longTask.start
      _     <- IO.sleep(200.millis)
      _     <- IO.println("    [main] 取消 longTask")
      _     <- fiber.cancel
      _     <- IO.println("    （连接已关闭 ↑，即使任务被取消）\n")
    } yield ()
  }

  // ============================================================
  // 5. 真实场景：把 acquire/release 改成 fromAutoCloseable
  //    适用所有 java.lang.AutoCloseable（JDBC Connection / InputStream...）
  // ============================================================
  val demo_FromAutoCloseable: IO[Unit] = {
    import java.io.{ByteArrayInputStream, InputStream}

    def stream(content: String): Resource[IO, InputStream] =
      Resource.fromAutoCloseable(
        IO.println(s"    >> 打开 InputStream").as(
          new ByteArrayInputStream(content.getBytes("UTF-8"))
        )
      )
      // release 自动调用 .close()，并打印不出来（因为是 Java 实现）

    for {
      _ <- IO.println("===== 5. fromAutoCloseable：直接对接 Java =====")
      _ <- stream("hello cats-effect").use { is =>
             IO {
               val bytes = is.readAllBytes()
               new String(bytes, "UTF-8")
             }.flatMap(s => IO.println(s"    读到：$s"))
           }
      _ <- IO.println("    （InputStream 已自动关闭）\n")
    } yield ()
  }

  // ============================================================
  // 6. 释放保证（Outcome 三态）：Resource 提供 onFinalize
  // ============================================================
  val demo_OnFinalize: IO[Unit] = {
    val resourceWithLog: Resource[IO, String] =
      Resource.make(IO.pure("token-xyz"))(_ => IO.println("    << release 一定会跑"))

    for {
      _ <- IO.println("===== 6. release 保证一定执行（成功/失败/取消）=====")
      _ <- resourceWithLog.use { token =>
             IO.println(s"    使用 $token")
           }
      _ <- IO.println("")
    } yield ()
  }

  def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- demo_SingleResource
      _ <- demo_Composition
      _ <- demo_ExceptionSafety
      _ <- demo_CancelSafety
      _ <- demo_FromAutoCloseable
      _ <- demo_OnFinalize
      _ <- IO.println("===== Scene03 完成 =====")
    } yield ExitCode.Success
}
