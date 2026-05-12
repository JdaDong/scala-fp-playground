package demo.part06

import cats.effect.{IO, IOApp, ExitCode, Fiber, Outcome}
import cats.syntax.all.*
import scala.concurrent.duration.*

/**
 * ============================================================
 * Scene 02: Cancellation —— 真正"可取消"的并发
 *
 *   Future 的痛点：
 *     1) 没有取消能力：一旦启动，就只能"忽略它的结果"，但任务依然在跑
 *     2) 资源不会被回收：超时后还在占着连接 / 文件句柄
 *
 *   IO 的能力：
 *     - start    ：启动一个 Fiber（轻量级线程）
 *     - cancel   ：真正中断它（在每个 cede 点检查）
 *     - timeout  ：到点自动取消
 *     - race     ：两个并发，谁先完成就取消另一个
 *     - parMapN  ：并行运行，失败 → 自动取消其他
 *
 *   重点理解：
 *     IO 在每个"挂起点" (sleep / delay / IO.async / *>) 都会检查取消信号
 *     → 比 Thread.interrupt 更可控，比 Future.cancel 更真实
 * ============================================================
 */
object Scene02_Cancellation extends IOApp {

  // ============================================================
  // 1. start + cancel：手动取消一个 Fiber
  // ============================================================
  val demo_StartCancel: IO[Unit] = {
    // 一个"无限循环"的任务
    //   注意：递归调用必须用 IO.defer 包起来，否则在构造 IO 时就会爆栈
    def loop(name: String, n: Int = 0): IO[Unit] =
      IO.println(s"    [$name] tick #$n") *>
      IO.sleep(200.millis) *>
      IO.defer(loop(name, n + 1))

    for {
      _     <- IO.println("===== 1. start + cancel =====")
      fiber <- loop("worker").start          // 启动 Fiber，立刻返回
      _     <- IO.sleep(700.millis)          // 主线程做点别的
      _     <- IO.println("    [main] 700ms 后取消 worker")
      _     <- fiber.cancel                  // 真正中断它（loop 在下一个 sleep 处停下）
      _     <- IO.println("    [main] worker 已取消\n")
    } yield ()
  }

  // ============================================================
  // 2. timeout：到点自动取消（不用自己写 racing）
  // ============================================================
  val demo_Timeout: IO[Unit] = {
    val slow: IO[String] =
      IO.println("    [slow] 开始一个 5 秒任务") *>
      IO.sleep(5.seconds) *>
      IO.pure("done")

    val withTimeout: IO[String] =
      slow.timeout(500.millis)               // 到 500ms 还没完 → 抛 TimeoutException + 取消

    for {
      _      <- IO.println("===== 2. timeout 自动取消 =====")
      result <- withTimeout.attempt           // attempt 把异常变 Either
      _      <- IO.println(s"    结果：$result\n")
    } yield ()
  }

  // ============================================================
  // 3. race：两个 IO 并行，谁先完成谁赢，输者自动取消
  //    典型场景：主备 RPC、缓存 vs DB 读取、超时兜底
  // ============================================================
  val demo_Race: IO[Unit] = {
    def query(name: String, delay: FiniteDuration): IO[String] =
      IO.println(s"    [$name] 开始查询 (${delay.toMillis}ms)") *>
      IO.sleep(delay) *>
      IO.println(s"    [$name] 查询完成").as(s"$name 的结果").onCancel(
        IO.println(s"    [$name] ❌ 被取消，释放资源")
      )

    val primary  = query("主库", 800.millis)
    val replica  = query("备库", 300.millis)   // 备库快

    for {
      _      <- IO.println("===== 3. race：备库赢，主库被自动取消 =====")
      result <- IO.race(primary, replica)
      _      <- IO.println(s"    最终结果：$result\n")
      _      <- IO.sleep(200.millis)          // 等"被取消"的日志打完
    } yield ()
  }

  // ============================================================
  // 4. parMapN：并行 + 失败传播 + 自动取消
  //    任意一个失败 → 其他正在跑的兄弟任务自动取消
  // ============================================================
  val demo_ParMapN: IO[Unit] = {
    def task(name: String, delay: FiniteDuration, fail: Boolean = false): IO[Int] =
      IO.println(s"    [$name] 启动").onCancel(
        IO.println(s"    [$name] ❌ 被取消")
      ) *>
      IO.sleep(delay) *>
      (if (fail) IO.raiseError(new RuntimeException(s"$name 失败"))
       else      IO.println(s"    [$name] 完成").as(name.length))

    val a = task("A", 200.millis)
    val b = task("B", 1.second, fail = true)   // 200ms 后 A 完成；1s 后 B 失败 → 此时其他已完成
    val c = task("C", 5.seconds)               // 永远等不到 → 会被取消

    val combined: IO[(Int, Int, Int)] = (a, b, c).parTupled

    for {
      _      <- IO.println("===== 4. parMapN：B 失败，C 被自动取消 =====")
      result <- combined.attempt
      _      <- IO.println(s"    结果：$result\n")
      _      <- IO.sleep(300.millis)
    } yield ()
  }

  // ============================================================
  // 5. uncancelable：保护"关键区"不被取消
  //    场景：转账两步（扣款 + 入账），不能在中间被打断
  // ============================================================
  val demo_Uncancelable: IO[Unit] = {
    val criticalSection: IO[Unit] = IO.uncancelable { _ =>
      for {
        _ <- IO.println("    [critical] step 1: 扣款")
        _ <- IO.sleep(300.millis)
        _ <- IO.println("    [critical] step 2: 入账")
        _ <- IO.sleep(300.millis)
        _ <- IO.println("    [critical] 完成（即使被 cancel 也会跑完）")
      } yield ()
    }

    for {
      _     <- IO.println("===== 5. uncancelable：关键区不被打断 =====")
      fiber <- criticalSection.start
      _     <- IO.sleep(100.millis)
      _     <- IO.println("    [main] 试图取消（应该无效）")
      _     <- fiber.cancel                  // 这里会"等"关键区跑完才返回
      _     <- IO.println("    [main] cancel 返回（关键区已完整执行）\n")
    } yield ()
  }

  def run(args: List[String]): IO[ExitCode] =
    for {
      _ <- demo_StartCancel
      _ <- demo_Timeout
      _ <- demo_Race
      _ <- demo_ParMapN
      _ <- demo_Uncancelable
      _ <- IO.println("===== Scene02 完成 =====")
    } yield ExitCode.Success
}
